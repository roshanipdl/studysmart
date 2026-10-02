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
        assertEquals(0f, projection.completion, 0f)
    }
}
