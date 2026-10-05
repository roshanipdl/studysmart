package com.studysmart.app.feature.preparation

import org.junit.Assert.*
import org.junit.Test

class PreparationReadingStateTest {
    private fun reached(measurement: PreparationBodyMeasurement, position: Int = 0,
        visible: Boolean = true, revealed: Boolean = true, settled: Boolean = true, latch: Boolean = false) =
        preparationAnswerEndReached(latch, revealed, visible, settled, measurement, position, 1)

    @Test fun unknownAndUnsettledContentNeverUnlocks() {
        assertFalse(reached(PreparationBodyMeasurement()))
        assertFalse(reached(PreparationBodyMeasurement(100, 0), settled = false))
        assertFalse(reached(PreparationBodyMeasurement(100, 0), revealed = false))
        assertFalse(reached(PreparationBodyMeasurement(100, 0), visible = false))
    }

    @Test fun shortAnswersAndBottomToleranceUnlock() {
        assertTrue(reached(PreparationBodyMeasurement(100, 0)))
        assertFalse(reached(PreparationBodyMeasurement(100, 200), 198))
        assertTrue(reached(PreparationBodyMeasurement(100, 200), 199))
    }

    @Test fun latchSurvivesBackwardScrollingFacesAndRemeasurement() {
        assertTrue(reached(PreparationBodyMeasurement(), visible = false, latch = true))
        assertTrue(reached(PreparationBodyMeasurement(50, 1000), 0, latch = true))
        // A new presentation starts with a fresh false latch.
        assertFalse(reached(PreparationBodyMeasurement(50, 1000), 0))
    }

    @Test fun activeScrollingDefersRatingMode() {
        val body = PreparationBodyMeasurement(100, 200)
        assertEquals(PreparationControlMode.CONTENT_SCROLL, preparationControlMode(true, body, true))
        assertEquals(PreparationControlMode.RATING, preparationControlMode(true, body, false))
        assertEquals(PreparationControlMode.MEASURING, preparationControlMode(false, PreparationBodyMeasurement(), false))
        assertEquals(PreparationControlMode.CONTENT_SCROLL, preparationControlMode(false, body, false))
        assertEquals(PreparationControlMode.REVEAL_REQUIRED, preparationControlMode(false, PreparationBodyMeasurement(100, 0), false))
    }
}
