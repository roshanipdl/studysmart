package com.studysmart.app.feature.preparation

import kotlin.math.hypot

/** Minimal centered circle enclosing each row's corners, plus radial padding/motion. */
internal fun preparationCircleDiameter(
    rows: List<Pair<Float, Float>>,
    gap: Float = 0f,
    padding: Float = 8f,
    motion: Float = 0f,
): Float {
    require(rows.isNotEmpty() && gap >= 0f && padding >= 0f && motion >= 0f)
    require(rows.all { (width, height) -> width >= 0f && height >= 0f })
    val height = rows.sumOf { it.second.toDouble() }.toFloat() + gap * (rows.size - 1)
    var top = -height / 2f
    val radius = rows.maxOf { (width, rowHeight) ->
        val bottom = top + rowHeight
        val edge = maxOf(kotlin.math.abs(top), kotlin.math.abs(bottom))
        top = bottom + gap
        hypot(width / 2f, edge)
    }
    return maxOf(48f, 2f * (radius + padding + motion))
}
