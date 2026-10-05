package com.studysmart.app.feature.preparation

/** A colored interval on the horizontal unique-completion track. */
internal data class PreparationHorizontalSegment(val left: Float, val right: Float)

/** Converts ordered whole-track fractions to bounded left-to-right segments. */
internal fun preparationHorizontalSegments(
    width: Float,
    fractions: List<Float>,
): List<PreparationHorizontalSegment> {
    val safeWidth = if (width.isFinite()) width.coerceAtLeast(0f) else 0f
    val safeFractions = fractions.map { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
    val sum = safeFractions.sum()
    val scale = if (sum > 1f) 1f / sum else 1f
    var left = 0f
    return safeFractions.map { fraction ->
        val right = (left + safeWidth * fraction * scale).coerceAtMost(safeWidth)
        PreparationHorizontalSegment(left, right).also { left = right }
    }
}
