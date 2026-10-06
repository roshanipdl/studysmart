package com.studysmart.app.feature.preparation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparationControlGroupGeometryTest {
    @Test fun approved320At200PercentReducesOnlyControlMargins() {
        val result = preparationControlGroupGeometry(320f, 308.58777f, 20f, 12f)
        assertTrue(result.fits)
        assertEquals(308.58777f, result.width, 0.001f)
        assertEquals(5.706115f, result.left, 0.001f)
        assertEquals(result.left, 320f - result.left - result.width, 0.001f)
    }

    @Test fun roomierWidthsRetainStandardMarginAndRightwardOffset() {
        val result = preparationControlGroupGeometry(360f, 240f, 20f, 12f)
        assertEquals(72f, result.left, 0.001f)
        assertTrue(360f - result.left - result.width >= 20f)
    }

    @Test fun exactFitNeverCompresses() {
        val result = preparationControlGroupGeometry(320f, 320f, 20f, 12f)
        assertTrue(result.fits)
        assertEquals(0f, result.left, 0f)
        assertEquals(320f, result.width, 0f)
    }

    @Test fun tooWideRemainsAProductGateNotAScaledPad() {
        val result = preparationControlGroupGeometry(320f, 321f, 20f, 12f)
        assertFalse(result.fits)
        assertEquals(321f, result.width, 0f)
    }
}
