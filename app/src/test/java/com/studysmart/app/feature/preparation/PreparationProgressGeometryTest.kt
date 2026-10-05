package com.studysmart.app.feature.preparation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparationProgressGeometryTest {
    @Test
    fun horizontalSegmentsFillUniqueCompletionInRatingOrder() {
        val segments = preparationHorizontalSegments(200f, listOf(0.1f, 0.2f, 0.15f, 0.05f))

        assertEquals(4, segments.size)
        assertEquals(0f, segments.first().left, 0.001f)
        assertEquals(100f, segments.last().right, 0.001f)
        assertEquals(20f, segments[0].right, 0.001f)
        assertEquals(segments[0].right, segments[1].left, 0.001f)
        assertEquals(segments[1].right, segments[2].left, 0.001f)
    }

    @Test
    fun fullCompletionFillsTheWholeTrackAndEmptyFractionsDoNotFill() {
        assertEquals(120f, preparationHorizontalSegments(120f, listOf(0.25f, 0.25f, 0.25f, 0.25f)).last().right, 0.001f)
        assertTrue(preparationHorizontalSegments(120f, listOf(0f, Float.NaN, -1f)).all { it.left == it.right })
        assertTrue(preparationHorizontalSegments(Float.NaN, listOf(1f)).all { it.left == it.right })
    }

    @Test
    fun outOfRangeFractionsAreBoundedToTrackWidth() {
        val segments = preparationHorizontalSegments(80f, listOf(1f, 1f, Float.POSITIVE_INFINITY))

        assertEquals(80f, segments.last().right, 0.001f)
        assertTrue(segments.all { it.left >= 0f && it.right <= 80f })
    }
}
