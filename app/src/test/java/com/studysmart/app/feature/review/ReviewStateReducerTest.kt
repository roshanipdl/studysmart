package com.studysmart.app.feature.review

import com.studysmart.app.core.model.LearningState
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.model.ReviewScheduleDecision
import com.studysmart.app.core.model.ReviewScheduleSnapshot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewStateReducerTest {
    private val reviewedAt = Instant.parse("2026-01-01T12:00:00Z")
    private val reducer = ReviewStateReducer()

    @Test
    fun firstAgainNeedsImprovementAndIncrementsLapse() {
        val result = reducer.reduce("question-1", null, decision(Rating.AGAIN))

        assertEquals(LearningState.NEEDS_IMPROVEMENT, result.learningState)
        assertEquals(0, result.repetitionCount)
        assertEquals(1, result.lapseCount)
    }

    @Test
    fun hardKeepsCardLearningWithoutSuccessfulRepetition() {
        val result = reducer.reduce("question-1", null, decision(Rating.HARD))

        assertEquals(LearningState.LEARNING, result.learningState)
        assertEquals(0, result.repetitionCount)
        assertEquals(0, result.lapseCount)
    }

    @Test
    fun secondSuccessfulGoodReachesGood() {
        val first = reducer.reduce("question-1", null, decision(Rating.GOOD))
        val second = reducer.reduce("question-1", first, decision(Rating.GOOD))

        assertEquals(LearningState.GOOD, second.learningState)
        assertEquals(2, second.repetitionCount)
    }

    @Test
    fun thirdSuccessfulEasyReachesMastered() {
        var result = reducer.reduce("question-1", null, decision(Rating.EASY))
        result = reducer.reduce("question-1", result, decision(Rating.EASY))
        result = reducer.reduce("question-1", result, decision(Rating.EASY))

        assertEquals(LearningState.MASTERED, result.learningState)
        assertEquals(3, result.repetitionCount)
    }

    @Test
    fun hardAfterMasteredReturnsToLearningWithoutErasingSuccessCount() {
        var result = reducer.reduce("question-1", null, decision(Rating.EASY))
        result = reducer.reduce("question-1", result, decision(Rating.EASY))
        result = reducer.reduce("question-1", result, decision(Rating.EASY))
        result = reducer.reduce("question-1", result, decision(Rating.HARD))

        assertEquals(LearningState.LEARNING, result.learningState)
        assertEquals(3, result.repetitionCount)
    }

    @Test
    fun reducerUsesSchedulerTimestampAndInterval() {
        val nextReviewAt = reviewedAt.plusSeconds(600)
        val result = reducer.reduce(
            questionId = "question-1",
            current = null,
            decision = ReviewScheduleDecision(
                rating = Rating.AGAIN,
                reviewedAt = reviewedAt,
                nextReviewAt = nextReviewAt,
                intervalSeconds = 600,
            ),
        )

        assertEquals(reviewedAt, result.lastReviewedAt)
        assertEquals(nextReviewAt, result.nextReviewAt)
        assertEquals(600, result.intervalSeconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun snapshotRejectsNegativeCounters() {
        ReviewScheduleSnapshot(questionId = "question-1", repetitionCount = -1)
    }

    private fun decision(rating: Rating): ReviewScheduleDecision = ReviewScheduleDecision(
        rating = rating,
        reviewedAt = reviewedAt,
        nextReviewAt = reviewedAt.plusSeconds(600),
        intervalSeconds = 600,
    )
}
