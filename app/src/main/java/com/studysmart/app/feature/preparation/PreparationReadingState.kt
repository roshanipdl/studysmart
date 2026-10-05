package com.studysmart.app.feature.preparation

internal enum class PreparationControlMode { MEASURING, REVEAL_REQUIRED, CONTENT_SCROLL, RATING }

internal data class PreparationBodyMeasurement(val viewportHeight: Int = 0, val maxScroll: Int = Int.MAX_VALUE) {
    val ready: Boolean get() = viewportHeight > 0 && maxScroll >= 0 && maxScroll != Int.MAX_VALUE
    val overflows: Boolean get() = ready && maxScroll > 0
}

/** Only the settled, measured answer can unlock; the latch belongs to one presentation. */
internal fun preparationAnswerEndReached(
    alreadyReached: Boolean,
    revealed: Boolean,
    answerVisible: Boolean,
    faceSettled: Boolean,
    measurement: PreparationBodyMeasurement,
    position: Int,
    tolerance: Int,
): Boolean = alreadyReached || (revealed && answerVisible && faceSettled && measurement.ready &&
    position >= 0 && position >= measurement.maxScroll - tolerance.coerceAtLeast(0))

internal fun preparationControlMode(
    answerEndReached: Boolean,
    measurement: PreparationBodyMeasurement,
    scrollingAction: Boolean,
): PreparationControlMode = when {
    scrollingAction -> PreparationControlMode.CONTENT_SCROLL
    answerEndReached -> PreparationControlMode.RATING
    !measurement.ready -> PreparationControlMode.MEASURING
    measurement.overflows -> PreparationControlMode.CONTENT_SCROLL
    else -> PreparationControlMode.REVEAL_REQUIRED
}
