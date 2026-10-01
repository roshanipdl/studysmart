package com.studysmart.app.feature.test

import com.studysmart.app.core.database.TestAnswerEntity
import com.studysmart.app.core.database.TestAttemptEntity
import com.studysmart.app.core.database.TestAttemptQuestionEntity
import com.studysmart.app.core.model.KeywordEvidenceCodec
import com.studysmart.app.core.model.KeywordSnapshotCodec
import com.studysmart.app.core.model.TestAttemptStatus
import java.math.BigDecimal
import kotlin.math.abs

enum class TestQuestionReportStatus {
    EVALUATED,
    NOT_EVALUABLE,
}

data class TestQuestionReport(
    val snapshot: TestAttemptQuestionEntity,
    val answer: TestAnswerEntity,
    val status: TestQuestionReportStatus,
    val matchedKeywords: List<String>,
    val missingKeywords: List<String>,
)

data class TestResultReport(
    val attempt: TestAttemptEntity,
    val questions: List<TestQuestionReport>,
    val weightedKeywordMatchPercent: Double,
    val evaluableQuestionCount: Int,
    val needsReviewCount: Int,
) {
    val score: Double get() = requireNotNull(attempt.score)
    val maxScore: Double get() = requireNotNull(attempt.maxScore)
    val percentage: Double get() = requireNotNull(attempt.percentage)
    val timeTakenSeconds: Long get() = requireNotNull(attempt.timeTakenSeconds)
}

sealed interface TestResultProjection {
    data class Available(val report: TestResultReport) : TestResultProjection
    data object NotSubmitted : TestResultProjection
    data class Corrupt(val reason: String) : TestResultProjection
}

/** Builds a read-only report exclusively from the submitted attempt's snapshots and evidence. */
object TestResultProjector {
    fun project(model: TestAttemptModel): TestResultProjection {
        if (model.attempt.status != TestAttemptStatus.SUBMITTED) {
            return TestResultProjection.NotSubmitted
        }
        return try {
            TestResultProjection.Available(projectSubmitted(model))
        } catch (error: IllegalArgumentException) {
            TestResultProjection.Corrupt(error.message ?: "Submitted result evidence is invalid.")
        } catch (error: IllegalStateException) {
            TestResultProjection.Corrupt(error.message ?: "Submitted result evidence is incomplete.")
        }
    }

    private fun projectSubmitted(model: TestAttemptModel): TestResultReport {
        val attempt = model.attempt
        require(attempt.actualQuestionCount in 1..30) { "invalid submitted question count" }
        require(model.integrityIssue() == null) { "submitted snapshots are invalid" }
        require(attempt.questionsAreComplete(model)) { "submitted snapshots or answers are incomplete" }
        val evaluator = KeywordAnswerEvaluator(requireNotNull(attempt.evaluatorVersion) {
            "submitted evaluator version is missing"
        })
        require(attempt.submittedAt != null && attempt.completedAt != null) {
            "submitted timestamps are missing"
        }
        require(attempt.completionReason == "submitted" || attempt.completionReason == "time_expired") {
            "submitted completion reason is invalid"
        }
        require(attempt.timeTakenSeconds != null && attempt.timeTakenSeconds >= 0L) {
            "submitted duration is invalid"
        }

        val score = requireNotNull(attempt.score) { "submitted score is missing" }
        val maxScore = requireNotNull(attempt.maxScore) { "submitted maximum score is missing" }
        val percentage = requireNotNull(attempt.percentage) { "submitted percentage is missing" }
        require(score.isFinite() && maxScore.isFinite() && maxScore > 0.0 && score in 0.0..maxScore) {
            "submitted totals are outside valid bounds"
        }
        require(percentage.isFinite() && percentage in 0.0..100.0) {
            "submitted percentage is outside valid bounds"
        }

        val frozenEvaluation = evaluator.evaluate(model.questions, model.answers)
        val evaluatedAnswersById = frozenEvaluation.answers.associateBy { it.attemptQuestionId }
        val answersById = model.answers.associateBy { it.testAttemptQuestionId }
        val questionReports = model.questions.sortedBy { it.displayOrder }.map { question ->
            val answer = requireNotNull(answersById[question.id]) { "submitted answer is missing" }
            val keywords = KeywordSnapshotCodec.decode(question.keywordSnapshotJson)
            val matched = KeywordEvidenceCodec.decode(
                requireNotNull(answer.matchedKeywordsJson) { "matched keyword evidence is missing" },
            )
            val missing = KeywordEvidenceCodec.decode(
                requireNotNull(answer.missingKeywordsJson) { "missing keyword evidence is missing" },
            )
            val answerScore = requireNotNull(answer.score) { "per-question score is missing" }
            val answerMaxScore = requireNotNull(answer.maxScore) { "per-question maximum score is missing" }
            val requiresReview = requireNotNull(answer.requiresReview) { "review decision is missing" }
            val matchPercent = answer.keywordMatchPercent
            val evaluatedAnswer = requireNotNull(evaluatedAnswersById[question.id]) {
                "frozen evaluation is incomplete"
            }

            require(answer.normalizedAnswer == evaluatedAnswer.normalizedAnswer) {
                "normalized answer does not match the frozen answer"
            }
            require(
                answer.normalizedAnswer == evaluatedAnswer.normalizedAnswer &&
                    matched == evaluatedAnswer.matchedKeywords &&
                    missing == evaluatedAnswer.missingKeywords &&
                    matchPercent.isCloseToNullable(evaluatedAnswer.keywordMatchPercent) &&
                    closeEnough(answerScore, evaluatedAnswer.score) &&
                    closeEnough(answerMaxScore, evaluatedAnswer.maxScore) &&
                    requiresReview == evaluatedAnswer.requiresReview,
            ) {
                "frozen answer evidence does not match its evaluator snapshot"
            }
            require(answer.timeSpentSeconds == null || answer.timeSpentSeconds >= 0L) {
                "per-question duration is invalid"
            }
            require(answerScore.isFinite() && answerMaxScore.isFinite()) {
                "per-question score is not finite"
            }
            require(closeEnough(answerMaxScore, question.maxScoreSnapshot) &&
                answerScore in 0.0..answerMaxScore
            ) {
                "per-question score does not match its snapshot"
            }

            val status = if (keywords.isEmpty()) {
                require(matchPercent == null) { "not-evaluable question has a keyword-match percentage" }
                require(answerScore == 0.0 && requiresReview && matched.isEmpty() && missing.isEmpty()) {
                    "not-evaluable evidence is inconsistent"
                }
                TestQuestionReportStatus.NOT_EVALUABLE
            } else {
                require(matchPercent != null && matchPercent.isFinite() && matchPercent in 0.0..100.0) {
                    "evaluable question has an invalid keyword-match percentage"
                }
                val snapshotWords = keywords.map { it.keyword }.toSet()
                require(matched.toSet().intersect(missing.toSet()).isEmpty() &&
                    matched.toSet() + missing.toSet() == snapshotWords
                ) {
                    "keyword evidence does not partition the frozen keyword snapshot"
                }
                TestQuestionReportStatus.EVALUATED
            }

            TestQuestionReport(
                snapshot = question,
                answer = answer,
                status = status,
                matchedKeywords = matched,
                missingKeywords = missing,
            )
        }

        val actualScore = questionReports.fold(BigDecimal.ZERO) { sum, result ->
            sum + BigDecimal.valueOf(requireNotNull(result.answer.score))
        }.toDouble()
        val actualMaxScore = questionReports.fold(BigDecimal.ZERO) { sum, result ->
            sum + BigDecimal.valueOf(requireNotNull(result.answer.maxScore))
        }.toDouble()
        require(closeEnough(score, actualScore) && closeEnough(maxScore, actualMaxScore)) {
            "attempt totals do not match frozen per-question scores"
        }
        val calculatedPercentage = (actualScore / actualMaxScore * 100.0).coerceIn(0.0, 100.0)
        require(closeEnough(percentage, calculatedPercentage)) {
            "attempt percentage does not match frozen score totals"
        }

        val evaluable = questionReports.filter { it.status == TestQuestionReportStatus.EVALUATED }
        val totalKeywordWeight = questionReports.sumOf { report ->
            KeywordSnapshotCodec.decode(report.snapshot.keywordSnapshotJson).sumOf { it.weight }
        }
        val matchedKeywordWeight = questionReports.sumOf { report ->
            val matched = report.matchedKeywords.toSet()
            KeywordSnapshotCodec.decode(report.snapshot.keywordSnapshotJson)
                .filter { it.keyword in matched }
                .sumOf { it.weight }
        }
        val weightedMatch = if (totalKeywordWeight <= 0.0 || evaluable.isEmpty()) {
            0.0
        } else {
            (matchedKeywordWeight / totalKeywordWeight *
                (evaluable.size.toDouble() / questionReports.size) * 100.0).coerceIn(0.0, 100.0)
        }
        require(closeEnough(weightedMatch, frozenEvaluation.aggregateKeywordMatchPercent)) {
            "aggregate keyword-match evidence is inconsistent"
        }

        return TestResultReport(
            attempt = attempt,
            questions = questionReports,
            weightedKeywordMatchPercent = weightedMatch,
            evaluableQuestionCount = evaluable.size,
            needsReviewCount = questionReports.count { it.answer.requiresReview == true },
        )
    }

    private fun TestAttemptEntity.questionsAreComplete(model: TestAttemptModel): Boolean =
        model.questions.size == actualQuestionCount &&
            model.questions.map { it.id }.distinct().size == model.questions.size &&
            model.questions.sortedBy { it.displayOrder }.map { it.displayOrder } ==
            model.questions.indices.toList() &&
            model.answers.size == actualQuestionCount &&
            model.answers.map { it.testAttemptQuestionId }.distinct().size == model.answers.size &&
            model.answers.all { answer -> model.questions.any { it.id == answer.testAttemptQuestionId } }

    private fun closeEnough(actual: Double, expected: Double): Boolean =
        abs(actual - expected) <= TOTAL_TOLERANCE

    private fun Double?.isCloseToNullable(expected: Double?): Boolean = when {
        this == null || expected == null -> this == null && expected == null
        else -> closeEnough(this, expected)
    }

    private const val TOTAL_TOLERANCE = 0.000001
}
