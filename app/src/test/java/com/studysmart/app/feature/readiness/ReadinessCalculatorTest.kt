package com.studysmart.app.feature.readiness

import com.studysmart.app.core.model.LearningState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessCalculatorTest {
    private val calculator = ReadinessCalculator()
    private val now = Instant.parse("2026-09-30T12:00:00Z")

    @Test
    fun mapsLearningStatesAndAppliesTheFixedFormulaWithoutRenormalizing() {
        val questions = listOf(
            question("not-studied", LearningState.NOT_STUDIED, hasRating = false),
            question("needs-improvement", LearningState.NEEDS_IMPROVEMENT),
            question("learning", LearningState.LEARNING),
            question("good", LearningState.GOOD),
            question("mastered", LearningState.MASTERED),
        )
        val attempt = answersForAttempt(
            id = "attempt-1",
            at = now,
            values = listOf("needs-improvement" to 0.2),
        )

        val estimate = calculator.calculate(questions, attempt, now)!!

        assertEquals(51.0, estimate.masteryScore, 0.0)
        assertEquals(80.0, estimate.coverageScore, 0.0)
        assertEquals(20.0, estimate.recentTestScore, 0.0)
        assertEquals(0.0, estimate.freshnessScore, 0.0)
        assertEquals(39.4, estimate.score, 0.000001)
        assertEquals(39, estimate.displayPercent)
    }

    @Test
    fun coverageCountsRatingsButNotSubmittedTests() {
        val questions = listOf(
            question("weak-test", LearningState.NOT_STUDIED, hasRating = false).copy(
                latestTestSubmittedAt = now,
                latestTestRequiresReview = true,
            ),
            question("strong-test", LearningState.NOT_STUDIED, hasRating = false).copy(
                latestTestSubmittedAt = now,
                latestTestRequiresReview = false,
            ),
            question("rated", LearningState.LEARNING, hasRating = true),
        )
        val testAnswers = answersForAttempt(
            "test-only",
            now,
            listOf("weak-test" to 0.0, "strong-test" to 1.0),
        )

        val estimate = calculator.calculate(questions, testAnswers, now)!!

        assertEquals(1, estimate.ratedQuestionCount)
        assertEquals(100.0 / 3.0, estimate.coverageScore, 0.000001)
        assertEquals(3, estimate.activeQuestionCount)
    }

    @Test
    fun weakTestOverlaysMasteryButLaterStrongTestDoesNotPromoteIt() {
        val rated = question(
            id = "q1",
            state = LearningState.MASTERED,
            hasRating = true,
            lastReviewedAt = now.minusSeconds(100),
        )
        val weak = calculator.calculate(
            listOf(rated.copy(latestTestSubmittedAt = now, latestTestRequiresReview = true)),
            emptyList(),
            now,
        )!!
        val strong = calculator.calculate(
            listOf(rated.copy(latestTestSubmittedAt = now.plusSeconds(1), latestTestRequiresReview = false)),
            emptyList(),
            now,
        )!!

        assertEquals(25.0, weak.masteryScore, 0.0)
        assertEquals(100.0, strong.masteryScore, 0.0)
    }

    @Test
    fun latestFiveAttemptsUseOnlyQuestionsInScopeForMixedScopeAttempts() {
        val question = question("in-scope", LearningState.NOT_STUDIED, hasRating = false)
        val answers = (0..5).flatMap { index ->
            val attemptId = "attempt-$index"
            val at = now.minusSeconds((5 - index).toLong())
            listOf(
                ReadinessTestAnswerInput(
                    attemptId = attemptId,
                    questionId = "in-scope",
                    submittedAt = at,
                    score = index / 10.0,
                    maxScore = 1.0,
                ),
                ReadinessTestAnswerInput(
                    attemptId = attemptId,
                    questionId = "outside-scope",
                    submittedAt = at,
                    score = 1.0,
                    maxScore = 1.0,
                ),
            )
        }

        val estimate = calculator.calculate(listOf(question), answers, now)!!

        assertEquals(5, estimate.recentAttemptCount)
        assertEquals(30.0, estimate.recentTestScore, 0.000001)
    }

    @Test
    fun freshnessUsesCompletedUtcDaysAndZeroForUnstudiedQuestions() {
        val questions = listOf(
            question(
                "fresh",
                LearningState.LEARNING,
                hasRating = true,
                lastReviewedAt = now.minusSeconds(86_400),
                nextReviewAt = now.plusSeconds(1),
            ),
            question(
                "two-days-overdue",
                LearningState.LEARNING,
                hasRating = true,
                lastReviewedAt = now.minusSeconds(500_000),
                nextReviewAt = now.minusSeconds(2 * 86_400L + 600),
            ),
            question("not-studied", LearningState.NOT_STUDIED, hasRating = false),
        )

        val estimate = calculator.calculate(questions, emptyList(), now)!!

        assertEquals(60.0, estimate.freshnessScore, 0.0)
    }

    @Test
    fun noActiveQuestionsHasNoEstimateAndInvalidTestScoresAreBounded() {
        assertNull(calculator.calculate(emptyList(), emptyList(), now))
        val estimate = calculator.calculate(
            listOf(question("q", LearningState.NOT_STUDIED, hasRating = false)),
            listOf(
                ReadinessTestAnswerInput("attempt", "q", now, score = 5.0, maxScore = 1.0),
            ),
            now,
        )!!

        assertTrue(estimate.score in 0.0..100.0)
        assertEquals(100.0, estimate.recentTestScore, 0.0)
    }

    @Test
    fun displayRoundingIsHalfUpAndClamped() {
        assertEquals(63, ReadinessCalculator.roundForDisplay(62.5))
        assertEquals(0, ReadinessCalculator.roundForDisplay(-2.0))
        assertEquals(100, ReadinessCalculator.roundForDisplay(102.0))
    }

    private fun question(
        id: String,
        state: LearningState,
        hasRating: Boolean = true,
        lastReviewedAt: Instant? = null,
        nextReviewAt: Instant? = null,
    ) = ReadinessQuestionInput(
        questionId = id,
        learningState = state,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        hasCommittedRating = hasRating,
        latestTestSubmittedAt = null,
        latestTestRequiresReview = null,
    )

    private fun answersForAttempt(
        id: String,
        at: Instant,
        values: List<Pair<String, Double>>,
    ): List<ReadinessTestAnswerInput> = values.map { (questionId, score) ->
        ReadinessTestAnswerInput(id, questionId, at, score, 1.0)
    }
}
