package com.studysmart.app.feature.preparation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.ui.StudySmartTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Product gate: execute on an approved host/device before implementing knob motion. */
class PreparationPadMeasurementTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun measuredFontAt200PercentFits320WithoutCompression() {
        var rating = 0f
        var reading = 0f
        var center = 0f
        var width = 0f
        composeRule.setContent {
            StudySmartTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Box(Modifier.width(320.dp)) {
                        // Constrained-width path may omit decorative rating arrows, not center states.
                        rating = preparationRatingButtonDiameter(false).value
                        reading = preparationReadingButtonDiameter().value
                        center = preparationFlipDiameter(false, 0.dp).value
                        width = preparationRatingPadWidth(false, 0.dp).value
                    }
                }
            }
        }
        composeRule.runOnIdle {
            val evidence = "200% font: rating=$rating reading=$reading center=$center pad=$width available=320dp"
            android.util.Log.i("PreparationPadMeasurement", evidence)
            assertTrue(evidence, rating >= 48f && reading >= 48f && center >= 48f)
            assertTrue("Product fallback approval required: $evidence", width <= 320f)
        }
    }
}
