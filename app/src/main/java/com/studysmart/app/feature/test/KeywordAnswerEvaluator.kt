package com.studysmart.app.feature.test

import com.studysmart.app.core.database.TestAnswerEntity
import com.studysmart.app.core.database.TestAttemptQuestionEntity
import com.studysmart.app.core.model.CaseFoldKeywordNormalizer
import com.studysmart.app.core.model.KeywordNormalizer
import com.studysmart.app.core.model.KeywordSnapshotCodec
import com.studysmart.app.core.model.QuestionType
import java.math.BigDecimal
import java.math.RoundingMode

class KeywordAnswerEvaluator(override val version: String = CURRENT_VERSION) : AnswerEvaluator {
    init {
        require(version == LEGACY_VERSION || version == CURRENT_VERSION) { "unsupported evaluator version" }
    }

    private fun normalize(value: String): String = if (version == LEGACY_VERSION) {
        KeywordNormalizer.normalize(value)
    } else {
        CaseFoldKeywordNormalizer.normalize(value)
    }

    override fun evaluate(
        questions: List<TestAttemptQuestionEntity>,
        answers: List<TestAnswerEntity>,
    ): TestEvaluation {
        require(questions.isNotEmpty()) { "an attempt must contain at least one question" }
        require(questions.size <= 30) { "an attempt cannot contain more than 30 questions" }
        require(questions.map { it.id }.distinct().size == questions.size) {
            "attempt question IDs must be unique"
        }
        require(answers.size == questions.size) { "every question must have one answer row" }
        require(answers.map { it.testAttemptQuestionId }.distinct().size == answers.size) {
            "attempt answer IDs must be unique"
        }
        val answersByQuestion = answers.associateBy { it.testAttemptQuestionId }
        require(questions.all { it.id in answersByQuestion }) {
            "attempt answers must match the fixed question set"
        }

        val evaluated = questions.sortedBy { it.displayOrder }.map { question ->
            require(question.questionTypeSnapshot == QuestionType.WRITTEN) {
                "unsupported question type in attempt snapshot"
            }
            require(question.maxScoreSnapshot.isFinite() && question.maxScoreSnapshot > 0.0) {
                "question maximum score must be finite and positive"
            }
            require(question.keywordSnapshotVersion == KeywordSnapshotCodec.CURRENT_VERSION) {
                "unsupported keyword snapshot version"
            }
            val answer = answersByQuestion.getValue(question.id)
            val normalizedAnswer = normalize(answer.answerText)
            val keywordEntries = KeywordSnapshotCodec.decode(question.keywordSnapshotJson)
            if (keywordEntries.isEmpty()) {
                EvaluatedTestAnswer(
                    attemptQuestionId = question.id,
                    normalizedAnswer = normalizedAnswer,
                    matchedKeywords = emptyList(),
                    missingKeywords = emptyList(),
                    keywordMatchPercent = null,
                    score = 0.0,
                    maxScore = question.maxScoreSnapshot,
                    requiresReview = true,
                    status = AnswerEvaluationStatus.NOT_EVALUABLE,
                )
            } else {
                val matched = keywordEntries.filter { entry ->
                    val comparison = normalize(entry.keyword)
                    if (' ' in comparison) {
                        normalizedAnswer.contains(comparison)
                    } else {
                        containsWholeWord(normalizedAnswer, comparison)
                    }
                }
                val matchedSet = matched.mapTo(HashSet()) { it.keyword }
                val missing = keywordEntries.filterNot { it.keyword in matchedSet }
                val totalWeight = keywordEntries.sumOf { it.weight }
                val matchedWeight = matched.sumOf { it.weight }
                val matchPercent = (matchedWeight / totalWeight * 100.0).coerceIn(0.0, 100.0)
                val score = roundScore(question.maxScoreSnapshot, matchPercent)
                EvaluatedTestAnswer(
                    attemptQuestionId = question.id,
                    normalizedAnswer = normalizedAnswer,
                    matchedKeywords = matched.map { it.keyword },
                    missingKeywords = missing.map { it.keyword },
                    keywordMatchPercent = matchPercent,
                    score = score,
                    maxScore = question.maxScoreSnapshot,
                    requiresReview = normalizedAnswer.isEmpty() || matchPercent < REVIEW_THRESHOLD_PERCENT,
                    status = AnswerEvaluationStatus.EVALUATED,
                )
            }
        }

        val evaluable = evaluated.filter { it.status == AnswerEvaluationStatus.EVALUATED }
        val allKeywords = questions.flatMap { question ->
            KeywordSnapshotCodec.decode(question.keywordSnapshotJson)
        }
        val totalKeywordWeight = allKeywords.sumOf { it.weight }
        val matchedKeywordWeight = evaluated.sumOf { result ->
            val matchedSet = result.matchedKeywords.toSet()
            val question = questions.first { it.id == result.attemptQuestionId }
            KeywordSnapshotCodec.decode(question.keywordSnapshotJson)
                .filter { it.keyword in matchedSet }
                .sumOf { it.weight }
        }
        val aggregateMatch = if (totalKeywordWeight <= 0.0 || evaluable.isEmpty()) {
            0.0
        } else {
            (matchedKeywordWeight / totalKeywordWeight * (evaluable.size.toDouble() / questions.size) * 100.0)
                .coerceIn(0.0, 100.0)
        }
        val totalScoreDecimal = evaluated.fold(BigDecimal.ZERO) { total, result ->
            total + BigDecimal.valueOf(result.score)
        }
        val maxScoreDecimal = evaluated.fold(BigDecimal.ZERO) { total, result ->
            total + BigDecimal.valueOf(result.maxScore)
        }
        val totalScore = totalScoreDecimal.toDouble()
        val maxScore = maxScoreDecimal.toDouble()
        val percentage = if (maxScoreDecimal.signum() == 0) {
            0.0
        } else {
            totalScoreDecimal
                .multiply(HUNDRED)
                .divide(maxScoreDecimal, 8, RoundingMode.HALF_UP)
                .toDouble()
                .coerceIn(0.0, 100.0)
        }
        return TestEvaluation(
            answers = evaluated,
            totalScore = totalScore,
            maxScore = maxScore,
            percentage = percentage,
            aggregateKeywordMatchPercent = aggregateMatch,
            evaluableQuestionCount = evaluable.size,
            requiresReviewCount = evaluated.count { it.requiresReview },
        )
    }

    private fun containsWholeWord(text: String, keyword: String): Boolean {
        var start = 0
        while (start <= text.length - keyword.length) {
            val matchAt = text.indexOf(keyword, start)
            if (matchAt < 0) return false
            val end = matchAt + keyword.length
            val startsAtBoundary = matchAt == 0 || !text.isWordCodePointBefore(matchAt)
            val endsAtBoundary = end == text.length || !text.isWordCodePointAt(end)
            if (startsAtBoundary && endsAtBoundary) return true
            start = matchAt + 1
        }
        return false
    }

    private fun String.isWordCodePointBefore(index: Int): Boolean =
        Character.codePointBefore(this, index).isWordCodePoint()

    private fun String.isWordCodePointAt(index: Int): Boolean =
        Character.codePointAt(this, index).isWordCodePoint()

    private fun Int.isWordCodePoint(): Boolean {
        if (Character.isLetterOrDigit(this)) return true
        return when (Character.getType(this)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(),
            Character.CONNECTOR_PUNCTUATION.toInt(),
            -> true
            else -> false
        }
    }

    private fun roundScore(maxScore: Double, matchPercent: Double): Double =
        BigDecimal.valueOf(maxScore)
            .multiply(BigDecimal.valueOf(matchPercent))
            .divide(HUNDRED, 2, RoundingMode.HALF_UP)
            .toDouble()

    companion object {
        const val LEGACY_VERSION = "keyword-v1"
        const val CURRENT_VERSION = "keyword-v2"
        const val REVIEW_THRESHOLD_PERCENT = 50.0
        val HUNDRED: BigDecimal = BigDecimal.valueOf(100)
    }
}
