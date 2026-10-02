package com.studysmart.app.feature.preparation

import com.studysmart.app.core.model.Rating

data class PreparationProgressProjection(
    val uniqueReviewed: Int,
    val uniqueTotal: Int,
    val ratingCounts: Map<Rating, Int>,
    val totalRatings: Int,
    val completion: Float,
)

fun projectPreparationProgress(session: PreparationSessionModel): PreparationProgressProjection {
    val events = session.reviewEvents.distinctBy { it.id }
    val counts = events.groupingBy { it.rating }.eachCount()
    return PreparationProgressProjection(
        uniqueReviewed = events.map { it.questionId }.distinct().size,
        uniqueTotal = session.uniqueQuestionCount,
        ratingCounts = Rating.entries.associateWith { counts[it] ?: 0 },
        totalRatings = events.size,
        completion = session.completionProgress,
    )
}
