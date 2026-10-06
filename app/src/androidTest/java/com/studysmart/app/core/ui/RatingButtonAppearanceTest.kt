package com.studysmart.app.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Shared component only; no database, preferences, or device setup. */
class RatingButtonAppearanceTest {
    @get:Rule val compose = createComposeRule()
    private enum class Appearance { DEFAULT, PRESERVED, OVERRIDDEN, ENABLED }
    private val dark = mutableStateOf(false)
    private val expected = mutableMapOf<RatingButtonVariant, Pair<Color, Color>>()
    private var clicks = 0

    private fun verify(appearance: Appearance) {
        compose.setContent {
            StudySmartTheme(darkTheme = dark.value) {
                val semantic = LocalStudySmartSemanticColors.current
                Column {
                    RatingButtonVariant.entries.forEach { rating ->
                        val (container, content) = when (rating) {
                            RatingButtonVariant.AGAIN -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
                            RatingButtonVariant.HARD -> semantic.warningContainer to semantic.warning
                            RatingButtonVariant.GOOD -> semantic.successContainer to semantic.success
                            RatingButtonVariant.EASY -> semantic.infoContainer to semantic.info
                        }
                        expected[rating] = when (appearance) {
                            Appearance.DEFAULT -> Color(0xFFE6E6EC) to Color(0xFF85869A)
                            Appearance.PRESERVED -> container to content.copy(alpha = 0.65f).compositeOver(container)
                            Appearance.OVERRIDDEN -> Color.Yellow to Color.Magenta
                            Appearance.ENABLED -> container to content
                        }
                        val modifier = Modifier.width(160.dp).testTag(rating.name)
                        when (appearance) {
                            // Deliberately omit the new argument to guard its public default.
                            Appearance.DEFAULT -> RatingButton(rating, { clicks++ }, modifier, enabled = false)
                            Appearance.PRESERVED -> RatingButton(rating, { clicks++ }, modifier,
                                enabled = false, preserveDisabledColor = true)
                            Appearance.OVERRIDDEN -> RatingButton(rating, { clicks++ }, modifier,
                                enabled = false, preserveDisabledColor = true,
                                disabledContainerColor = Color.Yellow, disabledContentColor = Color.Magenta)
                            // The new flag must win over both legacy preservation and explicit overrides.
                            Appearance.ENABLED -> RatingButton(rating, { clicks++ }, modifier,
                                enabled = false, preserveDisabledColor = true,
                                disabledContainerColor = Color.Yellow, disabledContentColor = Color.Magenta,
                                keepEnabledAppearance = true)
                        }
                    }
                }
            }
        }
        listOf(false, true).forEach { darkTheme ->
            compose.runOnIdle { dark.value = darkTheme }
            compose.waitForIdle()
            RatingButtonVariant.entries.forEach { rating ->
                val node = compose.onNodeWithTag(rating.name)
                node.assertIsNotEnabled()
                val pixels = node.captureToImage().toPixelMap()
                val (container, foreground) = expected.getValue(rating)
                fun close(actual: Color, target: Color): Boolean =
                    abs(actual.red - target.red) < 0.025f && abs(actual.green - target.green) < 0.025f &&
                        abs(actual.blue - target.blue) < 0.025f && abs(actual.alpha - target.alpha) < 0.025f
                assertTrue("$rating/$appearance/$darkTheme container",
                    close(pixels[pixels.width / 2, pixels.height / 8], container))
                var hasForeground = false
                for (y in pixels.height / 4 until pixels.height * 3 / 4)
                    for (x in pixels.width / 4 until pixels.width * 3 / 4)
                        if (close(pixels[x, y], foreground)) hasForeground = true
                assertTrue("$rating/$appearance/$darkTheme content", hasForeground)
                node.performTouchInput { click(center) }
            }
            assertEquals("Disabled appearance must never enable callbacks", 0, clicks)
        }
    }

    @Test fun defaultDisabledColorsRemainUnchanged() = verify(Appearance.DEFAULT)
    @Test fun legacyPreservationKeepsContainerAnd65PercentContent() = verify(Appearance.PRESERVED)
    @Test fun explicitDisabledOverridesWinOverLegacyPreservation() = verify(Appearance.OVERRIDDEN)
    @Test fun enabledAppearanceWinsOverOverridesWithoutEnablingTaps() = verify(Appearance.ENABLED)
}
