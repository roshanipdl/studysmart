package com.studysmart.app.feature.preparation

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparationJoystickSpringTest {
    @Test fun criticallyDampedZeroVelocityReturnNeverOvershoots() {
        val spec = spring<Float>(dampingRatio = 1f, stiffness = 400f).vectorize(Float.VectorConverter)
        val start = AnimationVector1D(8f)
        val end = AnimationVector1D(0f)
        val velocity = AnimationVector1D(0f)
        var previous = 8f
        repeat(101) { index ->
            val value = spec.getValueFromNanos(index * 10_000_000L, start, end, velocity).value
            assertTrue("Nonmonotonic return: $previous -> $value", value >= 0f && value <= previous + 0.001f)
            previous = value
        }
        assertTrue(previous < 0.001f)
    }
}
