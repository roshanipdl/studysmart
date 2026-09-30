package com.studysmart.app.feature.preparation

import com.studysmart.app.core.model.Rating

/** Display aggregates from committed session data, independent of Compose. */
data class PreparationSummaryProjection(
    val uniqueQuestionCount: Int,
    val presentationCount: Int,
    val ratingCounts: Map<Rating, Int>,
    val needsImprovementCount: Int,
    val recentlyReviewed: List<PreparationItemModel>,
)

fun projectPreparationSummary(session: PreparationSessionModel): PreparationSummaryProjection {
    val counts = session.reviewEvents.groupingBy { it.rating }.eachCount()
    return PreparationSummaryProjection(
        uniqueQuestionCount = session.uniqueQuestionCount,
        presentationCount = session.reviewCount,
        ratingCounts = Rating.entries.associateWith { counts[it] ?: 0 },
        needsImprovementCount = session.items.count {
            it.lastRating == Rating.AGAIN || it.lastRating == Rating.HARD
        },
        recentlyReviewed = session.items.sortedByDescending { it.lastPresentedAt },
    )
}
