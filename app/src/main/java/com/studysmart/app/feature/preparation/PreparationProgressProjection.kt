package com.studysmart.app.feature.preparation

import com.studysmart.app.core.model.Rating

data class PreparationProgressProjection(
    val uniqueReviewed: Int,
    val uniqueTotal: Int,
    val ratingCounts: Map<Rating, Int>,
    val totalRatings: Int,
    val completion: Float,
) {
    val ratingProportions: Map<Rating, Float>
        get() = Rating.entries.associateWith { rating ->
            if (totalRatings <= 0) 0f else ratingCounts.getValue(rating).toFloat() / totalRatings
        }
    /** Fractions of the whole track; their sum is the unique-reviewed fill. */
    val ratingSegmentFractions: Map<Rating, Float>
        get() = ratingProportions.mapValues { (_, proportion) -> completion * proportion }
}

fun projectPreparationProgress(session: PreparationSessionModel): PreparationProgressProjection {
    val events = session.reviewEvents.distinctBy { it.id }
    val counts = events.groupingBy { it.rating }.eachCount()
    return PreparationProgressProjection(
        uniqueReviewed = events.map { it.questionId }.distinct().size,
        uniqueTotal = session.uniqueQuestionCount,
        ratingCounts = Rating.entries.associateWith { counts[it] ?: 0 },
        totalRatings = events.size,
        completion = if (session.uniqueQuestionCount <= 0) 0f else {
            (events.map { it.questionId }.distinct().size.toFloat() / session.uniqueQuestionCount)
                .coerceIn(0f, 1f)
        },
    )
}
