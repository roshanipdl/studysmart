package com.studysmart.app.feature.preparation

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreparationJoystickOffsetTest {
    @Test fun all360AnglesFollowRawVectorAtBoundedRadius() {
        repeat(360) { degrees ->
            val angle = Math.toRadians(degrees.toDouble())
            val offset = preparationJoystickOffset((100 * cos(angle)).toFloat(),
                (100 * sin(angle)).toFloat(), 10f, 2f, 40f)
            assertEquals((16 * cos(angle)).toFloat(), offset.x, 0.001f)
            assertEquals((16 * sin(angle)).toFloat(), offset.y, 0.001f)
        }
    }

    @Test fun centerDeadZoneAndSmoothExit() {
        assertEquals(Offset.Zero, preparationJoystickOffset(6f, 8f, 10f, 1f, 8f))
        assertEquals(Offset(0.5f, 0f), preparationJoystickOffset(10.5f, 0f, 10f, 1f, 8f))
    }

    @Test fun availableGeometryCanReduceTravel() {
        assertEquals(Offset(3f, 0f), preparationJoystickOffset(100f, 0f, 10f, 1f, 3f))
    }

    @Test fun invalidInputsFailClosed() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertEquals(Offset.Zero, preparationJoystickOffset(it, 1f, 0f, 1f, 8f))
            assertEquals(Offset.Zero, preparationJoystickOffset(1f, it, 0f, 1f, 8f))
            assertEquals(Offset.Zero, preparationJoystickOffset(1f, 1f, it, 1f, 8f))
            assertEquals(Offset.Zero, preparationJoystickOffset(1f, 1f, 0f, it, 8f))
            assertEquals(Offset.Zero, preparationJoystickOffset(1f, 1f, 0f, 1f, it))
        }
        assertEquals(Offset.Zero, preparationJoystickOffset(100f, 0f, -1f, 1f, 8f))
        assertEquals(Offset.Zero, preparationJoystickOffset(100f, 0f, 0f, 0f, 8f))
        assertEquals(Offset.Zero, preparationJoystickOffset(100f, 0f, 0f, 1f, -1f))
    }

    @Test fun diagonalMotionDoesNotInventRatingSelection() {
        val offset = preparationJoystickOffset(100f, 100f, 10f, 1f, 8f)
        assertEquals(8f, offset.getDistance(), 0.001f)
        assertNull(ratingJoystickDirection(100f, 100f, 10f))
    }
}
