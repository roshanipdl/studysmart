package com.studysmart.app.feature.preparation

import com.studysmart.app.core.model.Rating
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PreparationSummaryProjectionTest {
    @Test
    fun completionProgressIsBoundedAndHandlesEmptySessions() {
        val item = sampleSession().items.single()
        assertEquals(0f, sampleSession().completionProgress, 0f)
        assertEquals(0f, sampleSession().copy(uniqueQuestionCount = 0, items = emptyList()).completionProgress, 0f)
        assertEquals(0.5f, sampleSession().copy(
            uniqueQuestionCount = 2,
            items = listOf(item.copy(isCompleted = true), item.copy(id = "item-2")),
        ).completionProgress, 0f)
        assertEquals(1f, sampleSession().copy(items = listOf(item.copy(isCompleted = true))).completionProgress, 0f)
    }

    @Test
    fun repeatedRatingsCountAsEventsWhileFinalWeaknessCountsItems() {
        val first = sampleSession().items.single().copy(
            lastRating = Rating.GOOD,
            lastPresentedAt = Instant.EPOCH.plusSeconds(20),
        )
        val second = first.copy(
            id = "item-2", questionId = "question-2", lastRating = Rating.HARD,
            lastPresentedAt = Instant.EPOCH.plusSeconds(10),
        )
        val events = listOf(Rating.AGAIN, Rating.AGAIN, Rating.GOOD, Rating.HARD)
            .mapIndexed { index, rating ->
                PreparationReviewEventModel("review-$index", first.questionId, rating, Instant.EPOCH)
            }
        val projection = projectPreparationSummary(sampleSession().copy(
            uniqueQuestionCount = 2, reviewCount = 4,
            items = listOf(second, first), reviewEvents = events,
        ))

        assertEquals(2, projection.uniqueQuestionCount)
        assertEquals(4, projection.presentationCount)
        assertEquals(mapOf(Rating.AGAIN to 2, Rating.HARD to 1, Rating.GOOD to 1, Rating.EASY to 0), projection.ratingCounts)
        assertEquals(1, projection.needsImprovementCount)
        assertEquals(listOf(first, second), projection.recentlyReviewed)
        assertEquals("Question", projection.recentlyReviewed.first().questionSnapshot)
    }

    @Test
    fun emptySessionHasZeroCountsAndNoRecentlyReviewedItems() {
        val projection = projectPreparationSummary(sampleSession().copy(
            uniqueQuestionCount = 0, reviewCount = 0, items = emptyList(), reviewEvents = emptyList(),
        ))
        assertEquals(0, projection.uniqueQuestionCount)
        assertEquals(0, projection.presentationCount)
        assertEquals(0, projection.needsImprovementCount)
        assertEquals(Rating.entries.associateWith { 0 }, projection.ratingCounts)
        assertEquals(emptyList<PreparationItemModel>(), projection.recentlyReviewed)
    }

    @Test
    fun equalTimestampsPreserveExistingOrderAndMissingTimestampsSortLast() {
        val first = sampleSession().items.single().copy(lastPresentedAt = Instant.EPOCH)
        val second = first.copy(id = "item-2", questionId = "question-2")
        val missing = first.copy(id = "item-3", questionId = "question-3", lastPresentedAt = null)
        assertEquals(listOf(first, second, missing), projectPreparationSummary(
            sampleSession().copy(items = listOf(missing, first, second)),
        ).recentlyReviewed)
    }
}
