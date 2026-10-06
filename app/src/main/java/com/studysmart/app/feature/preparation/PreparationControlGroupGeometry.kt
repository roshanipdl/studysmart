package com.studysmart.app.feature.preparation

internal data class PreparationControlGroupGeometry(val left: Float, val width: Float, val fits: Boolean)

/** Controls own full-screen width; card margins must not constrain their circles. */
internal fun preparationControlGroupGeometry(
    availableWidth: Float,
    measuredPadWidth: Float,
    preferredMargin: Float,
    preferredRightwardOffset: Float,
): PreparationControlGroupGeometry {
    val fits = availableWidth.isFinite() && measuredPadWidth.isFinite() &&
        availableWidth >= measuredPadWidth && measuredPadWidth > 0f
    if (!fits) return PreparationControlGroupGeometry(0f, measuredPadWidth, false)
    val spare = availableWidth - measuredPadWidth
    val margin = minOf(preferredMargin.coerceAtLeast(0f), spare / 2f)
    val left = (spare / 2f + preferredRightwardOffset.coerceAtLeast(0f))
        .coerceIn(margin, spare - margin)
    return PreparationControlGroupGeometry(left, measuredPadWidth, true)
}
