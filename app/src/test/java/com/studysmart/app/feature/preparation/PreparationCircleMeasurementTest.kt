package com.studysmart.app.feature.preparation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparationCircleMeasurementTest {
    @Test fun minimumTouchDiameterIs48() {
        assertEquals(48f, preparationCircleDiameter(listOf(10f to 10f)), 0.001f)
    }

    @Test fun actualRowCornersReplacePaddedBoundingRectangle() {
        val diameter = preparationCircleDiameter(listOf(80f to 40f))
        assertEquals(kotlin.math.hypot(80f, 40f) + 16f, diameter, 0.001f)
        assertTrue(diameter < kotlin.math.hypot(96f, 56f))
    }

    @Test fun motionClearanceIsRadialNotDiameter() {
        val rows = listOf(24f to 24f, 80f to 40f)
        assertEquals(preparationCircleDiameter(rows, gap = 4f) + 16f,
            preparationCircleDiameter(rows, gap = 4f, motion = 8f), 0.001f)
    }

    @Test fun eachStackedRowFitsInsideCircle() {
        val rows = listOf(20f to 40f, 80f to 40f)
        val radius = preparationCircleDiameter(rows, gap = 4f) / 2f - 8f
        assertEquals(kotlin.math.hypot(40f, 42f), radius, 0.001f)
    }
}
