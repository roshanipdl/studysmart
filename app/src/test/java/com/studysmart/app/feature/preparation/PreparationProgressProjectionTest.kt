package com.studysmart.app.feature.preparation

import com.studysmart.app.core.model.Rating
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class PreparationProgressProjectionTest {
    @Test fun emptySessionHasNeutralDistribution() {
        val progress = projectPreparationProgress(sampleSession())
        assertEquals(0, progress.uniqueReviewed)
        assertEquals(0, progress.totalRatings)
        assertTrue(progress.ratingCounts.values.all { it == 0 })
        assertEquals(0f, progress.completion, 0f)
        assertTrue(progress.ratingSegmentFractions.values.all { it == 0f })
    }
    @Test fun repeatsCountAsEventsButNotAsNewQuestions() {
        val events = listOf(Rating.AGAIN, Rating.HARD, Rating.GOOD).mapIndexed { index, rating ->
            PreparationReviewEventModel("r-$index", "question-1", rating, Instant.EPOCH)
        }
        val projection = projectPreparationProgress(sampleSession().copy(reviewEvents = events + events.first()))
        assertEquals(1, projection.uniqueReviewed)
        assertEquals(3, projection.totalRatings)
        assertEquals(1, projection.ratingCounts[Rating.AGAIN])
        assertEquals(0, projection.ratingCounts[Rating.EASY])
        assertEquals(1f, projection.completion, 0f)
        assertEquals(1f, projection.ratingSegmentFractions.values.sum(), 0.0001f)
        assertFalse(sampleSession().items.single().isCompleted)
    }

    @Test fun firstRatingFillsOnlyItsUniqueShareAndRepeatsRedistributeThatFill() {
        val first = PreparationReviewEventModel("r-1", "question-1", Rating.AGAIN, Instant.EPOCH)
        val session = sampleSession().copy(uniqueQuestionCount = 2, reviewEvents = listOf(first))
        val initial = projectPreparationProgress(session)
        assertEquals(0.5f, initial.completion, 0f)
        assertEquals(0.5f, initial.ratingSegmentFractions.getValue(Rating.AGAIN), 0f)
        val repeated = projectPreparationProgress(session.copy(reviewEvents = listOf(first,
            PreparationReviewEventModel("r-2", "question-1", Rating.GOOD, Instant.EPOCH))))
        assertEquals(initial.completion, repeated.completion, 0f)
        assertEquals(0.25f, repeated.ratingSegmentFractions.getValue(Rating.AGAIN), 0f)
        assertEquals(0.25f, repeated.ratingSegmentFractions.getValue(Rating.GOOD), 0f)
    }

    @Test fun nonpositiveTotalsAreSafeAndFillIsBounded() {
        val event = PreparationReviewEventModel("r-1", "question-1", Rating.HARD, Instant.EPOCH)
        listOf(0, -1).forEach { total ->
            val progress = projectPreparationProgress(sampleSession().copy(uniqueQuestionCount = total, reviewEvents = listOf(event)))
            assertEquals(0f, progress.completion, 0f)
            assertEquals(0f, progress.ratingSegmentFractions.values.sum(), 0f)
        }
        val progress = projectPreparationProgress(sampleSession().copy(reviewEvents = listOf(event,
            event.copy(id = "r-2", questionId = "question-2"))))
        assertEquals(1f, progress.completion, 0f)
    }
}
