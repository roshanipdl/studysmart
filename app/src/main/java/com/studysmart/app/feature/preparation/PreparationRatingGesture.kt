package com.studysmart.app.feature.preparation

import com.studysmart.app.core.model.Rating
import kotlin.math.abs

enum class RatingSwipeDirection(val rating: Rating, val arrow: String, val instruction: String) {
    LEFT(Rating.AGAIN, "←", "Swipe left"),
    DOWN(Rating.HARD, "↓", "Swipe down on the controls"),
    UP(Rating.GOOD, "↑", "Swipe up on the controls"),
    RIGHT(Rating.EASY, "→", "Swipe right"),
}

fun Rating.swipeDirection(): RatingSwipeDirection = RatingSwipeDirection.entries.first { it.rating == this }

internal enum class PreparationGestureOrigin { PAD_SWIPE, FLIP_JOYSTICK }

/** Joystick selection is recalculated, never axis-locked: center and diagonals cancel. */
internal fun ratingJoystickDirection(x: Float, y: Float, slop: Float): RatingSwipeDirection? =
    ratingDragDirection(x, y, slop, allowVertical = true)

/** Positive velocity advances the reading position: an upward joystick moves text up. */
internal fun preparationJoystickScrollVelocity(x: Float, y: Float, slop: Float, density: Float): Float {
    if (!density.isFinite() || density <= 0f) return 0f
    val direction = ratingJoystickDirection(x, y, slop)
    if (direction != RatingSwipeDirection.UP && direction != RatingSwipeDirection.DOWN) return 0f
    val fraction = ((abs(y) - slop) / (56f * density)).coerceIn(0f, 1f)
    val speed = (20f + 80f * fraction * fraction) * density
    return if (direction == RatingSwipeDirection.UP) speed else -speed
}

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

/** Preview is visual only; it never changes the commit threshold. */
internal fun ratingPreviewDistance(distance: Float, threshold: Float, density: Float): Float =
    if (!density.isFinite() || density <= 0f) 0f
    else ratingDragIntensity(distance, threshold) * 16f * density * if (distance < 0f) -1f else 1f
