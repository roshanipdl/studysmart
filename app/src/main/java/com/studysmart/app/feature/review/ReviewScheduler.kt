package com.studysmart.app.feature.review

import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.model.ReviewScheduleDecision
import java.time.Clock

interface ReviewScheduler {
    fun nextReview(rating: Rating): ReviewScheduleDecision
}

class DeterministicReviewScheduler(
    private val clock: Clock,
) : ReviewScheduler {
    override fun nextReview(rating: Rating): ReviewScheduleDecision {
        val reviewedAt = clock.instant()
        val intervalSeconds = rating.intervalSeconds()
        return ReviewScheduleDecision(
            rating = rating,
            reviewedAt = reviewedAt,
            nextReviewAt = reviewedAt.plusSeconds(intervalSeconds),
            intervalSeconds = intervalSeconds,
        )
    }
}

private fun Rating.intervalSeconds(): Long = when (this) {
    Rating.AGAIN -> 10 * 60L
    Rating.HARD -> 24 * 60 * 60L
    Rating.GOOD -> 3 * 24 * 60 * 60L
    Rating.EASY -> 7 * 24 * 60 * 60L
}
