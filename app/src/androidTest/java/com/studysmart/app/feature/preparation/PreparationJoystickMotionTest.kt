package com.studysmart.app.feature.preparation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.ui.StudySmartTheme
import com.studysmart.app.testing.unclippedCenterInRoot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PreparationJoystickMotionTest {
    private val scale = object : MotionDurationScale { override val scaleFactor = 0f }
    @get:Rule val composeRule = createComposeRule(effectContext = scale)

    @Test fun zeroMotionScaleReturnsImmediatelyAndNewDragUsesRawOffset() {
        val visual = mutableStateOf(PreparationJoystickVisual())
        var density = 1f
        composeRule.setContent {
            StudySmartTheme {
                density = LocalDensity.current.density
                PreparationRatingPad(enabled = true, onRate = {}, flipExtra = 0.dp,
                    joystickVisual = visual.value)
            }
        }
        val node = composeRule.onNodeWithTag("preparation_joystick_knob", true)
        val rest = node.unclippedCenterInRoot()
        composeRule.runOnIdle { visual.value = PreparationJoystickVisual(true, Offset(8 * density, 0f)) }
        assertEquals(rest.x + 8 * density, node.unclippedCenterInRoot().x, 0.1f)
        composeRule.runOnIdle { visual.value = PreparationJoystickVisual() }
        assertEquals(rest.x, node.unclippedCenterInRoot().x, 0.1f)
        composeRule.runOnIdle { visual.value = PreparationJoystickVisual(true, Offset(-8 * density, 0f)) }
        assertEquals(rest.x - 8 * density, node.unclippedCenterInRoot().x, 0.1f)
    }
}
