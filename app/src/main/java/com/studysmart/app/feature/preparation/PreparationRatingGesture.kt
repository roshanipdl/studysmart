package com.studysmart.app.feature.preparation

import com.studysmart.app.core.model.Rating
import kotlin.math.abs

enum class RatingSwipeDirection(val rating: Rating, val arrow: String, val instruction: String) {
    LEFT(Rating.AGAIN, "←", "Swipe left"),
    DOWN(Rating.HARD, "↓", "Swipe down from the card header or rating pad"),
    UP(Rating.GOOD, "↑", "Swipe up from the card header or rating pad"),
    RIGHT(Rating.EASY, "→", "Swipe right"),
}

internal enum class PreparationGestureRegion(val allowsVertical: Boolean) {
    OUTSIDE(false), CARD_BODY(false), HEADER(true), RATING_PAD(true),
}

internal data class PreparationGestureBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

internal fun preparationGestureRegion(
    x: Float,
    y: Float,
    card: PreparationGestureBounds?,
    header: PreparationGestureBounds?,
    pad: PreparationGestureBounds?,
): PreparationGestureRegion = when {
    pad?.contains(x, y) == true -> PreparationGestureRegion.RATING_PAD
    card?.contains(x, y) != true -> PreparationGestureRegion.OUTSIDE
    header?.contains(x, y) == true -> PreparationGestureRegion.HEADER
    else -> PreparationGestureRegion.CARD_BODY
}

fun Rating.swipeDirection(): RatingSwipeDirection = RatingSwipeDirection.entries.first { it.rating == this }

/** Returns null until displacement has a clear axis. A caller locks that axis for the drag. */
fun ratingDragDirection(x: Float, y: Float, slop: Float, allowVertical: Boolean): RatingSwipeDirection? {
    if (!x.isFinite() || !y.isFinite() || !slop.isFinite() || slop < 0f) return null
    return when {
        abs(x) > slop && abs(x) > abs(y) * 1.25f ->
            if (x < 0f) RatingSwipeDirection.LEFT else RatingSwipeDirection.RIGHT
        allowVertical && abs(y) > slop && abs(y) > abs(x) * 1.25f ->
            if (y < 0f) RatingSwipeDirection.UP else RatingSwipeDirection.DOWN
        else -> null
    }
}

fun ratingDragThreshold(viewportExtent: Float, density: Float): Float {
    require(viewportExtent.isFinite() && viewportExtent > 0f)
    require(density.isFinite() && density > 0f)
    return (viewportExtent * 0.25f).coerceIn(56f * density, 96f * density)
}

fun ratingDragIntensity(distance: Float, threshold: Float): Float =
    if (!distance.isFinite() || !threshold.isFinite() || threshold <= 0f) 0f
    else (abs(distance) / threshold).coerceIn(0f, 1f)

fun ratingDragCommits(distance: Float, threshold: Float): Boolean =
    distance.isFinite() && threshold.isFinite() && threshold > 0f && abs(distance) >= threshold
