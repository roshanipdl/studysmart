package com.studysmart.app.feature.review

import com.studysmart.app.core.database.ReadinessQuestionRow
import com.studysmart.app.core.model.LearningState
import com.studysmart.app.core.model.Rating
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ReviewFilterTest {
    private val now = Instant.parse("2026-10-06T12:00:00Z")
    private fun row(id: String, state: LearningState? = null, reviewed: Instant? = null,
        tested: Instant? = null, warning: Boolean? = null, rating: Rating? = null) =
        ReadinessQuestionRow(id, state, reviewed, now.minusSeconds(10), reviewed != null, tested, warning, latestRating = rating)

    @Test fun dueIsNotAWeakState() {
        val rows = listOf(row("good", LearningState.GOOD, now), row("mastered", LearningState.MASTERED, now))
        assertTrue(ReviewFilter.NEEDS_IMPROVEMENT.select(rows).isEmpty())
        assertEquals(listOf("good"), ReviewFilter.GOOD.select(rows).map { it.questionId })
    }
    @Test fun passingTestDoesNotCountAsNotStudiedOrPromoteMastery() {
        val rows = listOf(row("unseen"), row("tested", tested = now, warning = false))
        assertEquals(listOf("unseen"), ReviewFilter.NOT_STUDIED.select(rows).map { it.questionId })
        assertTrue(ReviewFilter.GOOD.select(rows).isEmpty())
    }
    @Test fun latestWarningOverlaysAndLaterRatingClearsIt() {
        val rows = listOf(row("warning", LearningState.MASTERED, now.minusSeconds(1), now, true),
            row("cleared", LearningState.GOOD, now, now.minusSeconds(1), true))
        assertEquals(listOf("warning"), ReviewFilter.NEEDS_IMPROVEMENT.select(rows).map { it.questionId })
        assertEquals(listOf("cleared"), ReviewFilter.GOOD.select(rows).map { it.questionId })
    }
    @Test fun achievementNewestFirstAndCurriculumTiesStable() {
        val rows = listOf(row("old", LearningState.GOOD, now.minusSeconds(1)), row("a", LearningState.GOOD, now), row("b", LearningState.GOOD, now))
        assertEquals(listOf("a", "b", "old"), ReviewFilter.GOOD.select(rows).map { it.questionId })
    }
    @Test fun weakIsWeakestThenOldest() {
        val rows = listOf(row("hard", LearningState.NEEDS_IMPROVEMENT, now.minusSeconds(9), rating = Rating.HARD),
            row("again", LearningState.NEEDS_IMPROVEMENT, now, rating = Rating.AGAIN))
        assertEquals(listOf("again", "hard"), ReviewFilter.NEEDS_IMPROVEMENT.select(rows).map { it.questionId })
    }
    @Test fun invalidFilterFailsClosed() { assertNull(ReviewFilter.fromRoute("due")) }

    @Test fun scheduleWithoutAnyCommittedEventCannotCountAsMastered() {
        val rows = listOf(row("no-event", LearningState.MASTERED))
        assertEquals(1, ReviewFilter.NOT_STUDIED.select(rows).size)
        assertTrue(ReviewFilter.MASTERED.select(rows).isEmpty())
    }
}
