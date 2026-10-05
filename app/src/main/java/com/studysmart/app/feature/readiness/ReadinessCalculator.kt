package com.studysmart.app.feature.readiness

import com.studysmart.app.core.model.LearningState
import com.studysmart.app.feature.review.effectiveLearningState
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

data class ReadinessQuestionInput(
    val questionId: String,
    val learningState: LearningState?,
    val lastReviewedAt: Instant?,
    val nextReviewAt: Instant?,
    val hasCommittedRating: Boolean,
    val latestTestSubmittedAt: Instant?,
    val latestTestRequiresReview: Boolean?,
)

data class ReadinessTestAnswerInput(
    val attemptId: String,
    val questionId: String,
    val submittedAt: Instant,
    val score: Double?,
    val maxScore: Double?,
)

data class ReadinessEstimate(
    val score: Double,
    val masteryScore: Double,
    val recentTestScore: Double,
    val coverageScore: Double,
    val freshnessScore: Double,
    val activeQuestionCount: Int,
    val ratedQuestionCount: Int,
    val recentAttemptCount: Int,
) {
    val displayPercent: Int
        get() = ReadinessCalculator.roundForDisplay(score)
}

/** Calculates the deterministic MVP readiness estimate from current, active scope inputs. */
class ReadinessCalculator {
    fun calculate(
        questions: List<ReadinessQuestionInput>,
        testAnswers: List<ReadinessTestAnswerInput>,
        now: Instant,
    ): ReadinessEstimate? {
        val activeQuestions = questions.distinctBy(ReadinessQuestionInput::questionId)
        if (activeQuestions.isEmpty()) return null
        val questionsById = activeQuestions.associateBy(ReadinessQuestionInput::questionId)

        val mastery = activeQuestions.sumOf { question ->
            val state = effectiveLearningState(
                question.learningState, question.lastReviewedAt,
                question.latestTestSubmittedAt, question.latestTestRequiresReview,
            )
            when (state) {
                LearningState.NOT_STUDIED -> 0.0
                LearningState.NEEDS_IMPROVEMENT -> 25.0
                LearningState.LEARNING -> 50.0
                LearningState.GOOD -> 80.0
                LearningState.MASTERED -> 100.0
            }
        } / activeQuestions.size

        val ratedCount = activeQuestions.count(ReadinessQuestionInput::hasCommittedRating)
        val coverage = ratedCount.toDouble() / activeQuestions.size * 100.0

        val freshness = activeQuestions.sumOf { question ->
            question.freshnessScore(now)
        } / activeQuestions.size

        val recentAttempts = testAnswers
            .asSequence()
            .filter { it.questionId in questionsById }
            .groupBy(ReadinessTestAnswerInput::attemptId)
            .mapNotNull { (attemptId, answers) ->
                val submittedAt = answers.maxOfOrNull(ReadinessTestAnswerInput::submittedAt)
                    ?: return@mapNotNull null
                val maxScore = answers.sumOf { it.maxScore.validPositiveOrZero() }
                val score = answers.sumOf { answer ->
                    val max = answer.maxScore.validPositiveOrZero()
                    answer.score.validFiniteOrZero().coerceIn(0.0, max)
                }
                if (maxScore <= 0.0) {
                    ReadinessAttemptScore(attemptId, submittedAt, 0.0)
                } else {
                    ReadinessAttemptScore(
                        attemptId = attemptId,
                        submittedAt = submittedAt,
                        percentage = (score / maxScore * 100.0).coerceIn(0.0, 100.0),
                    )
                }
            }
            .sortedWith(
                compareByDescending<ReadinessAttemptScore> { it.submittedAt }
                    .thenByDescending { it.attemptId },
            )
            .take(RECENT_ATTEMPT_LIMIT)
        val recentTestScore = if (recentAttempts.isEmpty()) {
            0.0
        } else {
            recentAttempts.sumOf(ReadinessAttemptScore::percentage) / recentAttempts.size
        }

        val readiness = (
            MASTERY_WEIGHT * mastery +
                RECENT_TEST_WEIGHT * recentTestScore +
                COVERAGE_WEIGHT * coverage +
                FRESHNESS_WEIGHT * freshness
            ).coerceIn(0.0, 100.0)

        return ReadinessEstimate(
            score = readiness,
            masteryScore = mastery,
            recentTestScore = recentTestScore,
            coverageScore = coverage,
            freshnessScore = freshness,
            activeQuestionCount = activeQuestions.size,
            ratedQuestionCount = ratedCount,
            recentAttemptCount = recentAttempts.size,
        )
    }

    private fun ReadinessQuestionInput.freshnessScore(now: Instant): Double {
        val reviewedAt = lastReviewedAt ?: return 0.0
        val dueAt = nextReviewAt ?: return 0.0
        if (reviewedAt.isAfter(now)) return 0.0
        if (dueAt.isAfter(now)) return 100.0
        val overdueDays = Duration.between(dueAt, now).toDays().coerceAtLeast(0L)
        return (100.0 - overdueDays * FRESHNESS_OVERDUE_PENALTY).coerceAtLeast(0.0)
    }

    private fun Double?.validFiniteOrZero(): Double = this?.takeIf(Double::isFinite) ?: 0.0

    private fun Double?.validPositiveOrZero(): Double =
        this?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0

    private data class ReadinessAttemptScore(
        val attemptId: String,
        val submittedAt: Instant,
        val percentage: Double,
    )

    companion object {
        const val RECENT_ATTEMPT_LIMIT = 5
        const val MASTERY_WEIGHT = 0.40
        const val RECENT_TEST_WEIGHT = 0.35
        const val COVERAGE_WEIGHT = 0.15
        const val FRESHNESS_WEIGHT = 0.10
        const val FRESHNESS_OVERDUE_PENALTY = 10.0

        fun roundForDisplay(value: Double): Int {
            require(value.isFinite()) { "readiness score must be finite" }
            return BigDecimal.valueOf(value.coerceIn(0.0, 100.0))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact()
        }
    }
}
