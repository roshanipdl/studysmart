package com.studysmart.app.feature.preparation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.ui.StudySmartTheme
import com.studysmart.app.testing.unclippedControlBoundsInRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** No database fixtures, app-data mutations, permissions, or cross-app interaction. */
class PreparationJoystickVisualTest {
    @get:Rule val composeRule = createComposeRule()
    private val unlocked = mutableStateOf(true)
    private val input = mutableStateOf(true)
    private val reset = mutableStateOf(false)
    private val presentation = mutableStateOf(0)
    private val visual = mutableStateOf(PreparationJoystickVisual())
    private val rated = mutableListOf<Rating>()
    private var flipped = 0
    private var density = 1f

    private fun setContent(fontScale: Float = 1f) {
        composeRule.setContent {
            StudySmartTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    density = LocalDensity.current.density
                    key(presentation.value) {
                        val bodyScroll = rememberScrollState()
                        PreparationSwipeCard(
                            enabled = unlocked.value,
                            inputEnabled = input.value,
                            feedback = null,
                            viewportHeight = (500 * density).toInt(),
                            entranceDirection = null,
                            resetPreview = reset.value,
                            onRate = { rated += it }, onExitFinished = {}, onBusyChanged = {},
                            scrollingAllowed = !unlocked.value,
                            readingScrollState = bodyScroll,
                            onJoystickVisualChanged = { visual.value = it },
                            ratingPad = { modifier, ready, direction, scrollDirection, bounds ->
                                PreparationRatingPad(
                                    enabled = unlocked.value && ready,
                                    ratingsUnlocked = unlocked.value,
                                    joystickVisual = visual.value,
                                    flipEnabled = input.value && ready,
                                    onFlip = { flipped++ }, onRate = { rated += it },
                                    showDirectionHints = fontScale == 1f,
                                    flipExtra = 0.dp,
                                    previewDirection = direction, scrollDirection = scrollDirection,
                                    scrollMode = !unlocked.value, onFlipBounds = bounds,
                                    modifier = modifier.testTag("pad"),
                                )
                            },
                            layout = { modifier, card, pad, _ ->
                                Box(modifier.width(320.dp).height(500.dp).testTag("viewport")) { pad() }
                                card(true)
                            },
                        ) { modifier, _, _, _, _ ->
                            Box(modifier.height(48.dp).verticalScroll(bodyScroll)) {
                                Text("Reading body\n".repeat(100))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun flipStart(): Offset {
        val ring = composeRule.onNodeWithTag("preparation_flip_button").unclippedControlBoundsInRoot()
        val pad = composeRule.onNodeWithTag("pad").unclippedControlBoundsInRoot()
        return ring.center - pad.topLeft
    }

    private fun knobCenter() = composeRule.onNodeWithTag("preparation_joystick_knob", true)
        .unclippedControlBoundsInRoot().center

    private fun centerLabel(label: String) = composeRule.onNode(
        hasText(label) and hasAnyAncestor(hasTestTag("preparation_joystick_knob")), true)

    @Test fun narrow200PercentTargetsStayInsideViewportAndRealTapsWork() {
        setContent(2f)
        val viewport = composeRule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        listOf("again", "good", "easy", "hard").forEach { rating ->
            val node = composeRule.onNodeWithTag("preparation_rating_$rating")
            node.assertIsDisplayed()
            val bounds = node.unclippedControlBoundsInRoot()
            assertTrue(bounds.left >= viewport.left && bounds.right <= viewport.right + 1f)
            assertTrue(bounds.top >= viewport.top && bounds.bottom <= viewport.bottom + 1f)
            assertTrue(bounds.width >= 48 * density && bounds.height >= 48 * density)
            assertEquals(bounds.width, bounds.height, 1f)
        }
        composeRule.onNodeWithTag("preparation_rating_easy").performTouchInput { click(center) }
        assertEquals(listOf(Rating.EASY), rated)
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput { click(center) }
        assertEquals(1, flipped)
    }

    @Test fun diagonalFollowsFingerWithoutSelectionAndReturnsWithoutFlip() {
        setContent()
        val start = flipStart()
        val ring = composeRule.onNodeWithTag("preparation_flip_button").unclippedControlBoundsInRoot()
        val rest = knobCenter()
        composeRule.onNodeWithTag("pad").performTouchInput {
            down(start); moveTo(start + Offset(40 * density, 40 * density))
        }
        composeRule.runOnIdle {
            assertTrue(visual.value.active)
            assertEquals(8 * density, visual.value.offset.getDistance(), 0.1f)
        }
        centerLabel("Drag").assertTextEquals("Drag")
        assertEquals(ring, composeRule.onNodeWithTag("preparation_flip_button").unclippedControlBoundsInRoot())
        assertTrue(knobCenter().x > rest.x && knobCenter().y > rest.y)
        composeRule.onNodeWithTag("pad").performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(rest.x, knobCenter().x, 1f)
        assertEquals(rest.y, knobCenter().y, 1f)
        assertEquals(0, flipped)
        assertTrue(rated.isEmpty())
    }

    @Test fun selectedLabelSurvivesDragBusyAndReleaseCommitsExactlyOnce() {
        setContent()
        val start = flipStart()
        composeRule.onNodeWithTag("pad").performTouchInput {
            down(start); moveTo(start + Offset(100 * density, 0f))
        }
        centerLabel("Easy").assertTextEquals("Easy")
        assertTrue(visual.value.active)
        composeRule.onNodeWithTag("pad").performTouchInput { up() }
        assertEquals(listOf(Rating.EASY), rated)
        assertEquals(0, flipped)
        centerLabel("Drag").assertTextEquals("Drag")
    }

    @Test fun ordinarySwipeSelectsLabelButNeverMovesKnob() {
        setContent()
        val rest = knobCenter()
        val ring = composeRule.onNodeWithTag("preparation_flip_button").unclippedControlBoundsInRoot()
        val again = composeRule.onNodeWithTag("preparation_rating_again").unclippedControlBoundsInRoot()
        val pad = composeRule.onNodeWithTag("pad").unclippedControlBoundsInRoot()
        composeRule.onNodeWithTag("pad").performTouchInput {
            down(again.center - pad.topLeft); moveBy(Offset(100 * density, 0f))
        }
        centerLabel("Easy").assertTextEquals("Easy")
        assertEquals(PreparationJoystickVisual(), visual.value)
        assertEquals(ring, composeRule.onNodeWithTag("preparation_flip_button").unclippedControlBoundsInRoot())
        // A differently sized label changes integer-placement parity by at most half a pixel.
        // The raw visual offset remains exactly zero and the outer ring does not move.
        assertEquals(rest.x, knobCenter().x, 0.51f)
        assertEquals(rest.y, knobCenter().y, 0.51f)
        composeRule.onNodeWithTag("pad").performTouchInput { up() }
        assertEquals(listOf(Rating.EASY), rated)
        assertEquals(0, flipped)
    }

    @Test fun returnToCenterThenReleaseDoesNotFlipOrRate() {
        setContent()
        val start = flipStart()
        composeRule.onNodeWithTag("pad").performTouchInput {
            down(start); moveTo(start + Offset(100 * density, 0f)); moveTo(start)
        }
        centerLabel("Drag").assertTextEquals("Drag")
        assertEquals(Offset.Zero, visual.value.offset)
        composeRule.onNodeWithTag("pad").performTouchInput { up() }
        assertTrue(rated.isEmpty())
        assertEquals(0, flipped)
    }

    @Test fun multiTouchCancellationClearsVisualAndSuppressesCommit() {
        setContent()
        val start = flipStart()
        composeRule.onNodeWithTag("pad").performTouchInput {
            down(start); moveTo(start + Offset(100 * density, 0f))
            down(1, start); moveBy(1, Offset(1f, 0f))
            up(1); up(0)
        }
        assertEquals(PreparationJoystickVisual(), visual.value)
        assertTrue(rated.isEmpty())
        assertEquals(0, flipped)
    }

    @Test fun readingGestureStaysStationaryAndNeverConvertsAfterUnlock() {
        unlocked.value = false
        setContent()
        composeRule.mainClock.autoAdvance = false
        val rest = knobCenter()
        val start = flipStart()
        composeRule.onNodeWithTag("pad").performTouchInput {
            down(start); moveTo(start + Offset(0f, -100 * density))
        }
        assertEquals(PreparationJoystickVisual(), visual.value)
        assertEquals(rest, knobCenter())
        centerLabel("Flip").assertTextEquals("Flip")
        composeRule.runOnIdle { unlocked.value = true }
        composeRule.onNodeWithTag("pad").performTouchInput {
            moveTo(start + Offset(100 * density, 0f)); up()
        }
        composeRule.mainClock.autoAdvance = true
        assertEquals(PreparationJoystickVisual(), visual.value)
        assertTrue(rated.isEmpty())
        assertEquals(0, flipped)
    }

    @Test fun errorDiscardOrSavingInputCancellationClearsVisual() {
        setContent()
        val start = flipStart()
        composeRule.onNodeWithTag("pad").performTouchInput {
            down(start); moveTo(start + Offset(100 * density, 0f))
        }
        composeRule.runOnIdle { reset.value = true; input.value = false }
        // State writes schedule recomposition; wait for pointer cancellation/effect cleanup.
        composeRule.waitForIdle()
        assertEquals(PreparationJoystickVisual(), visual.value)
        composeRule.onNodeWithTag("pad").performTouchInput { up() }
        assertTrue(rated.isEmpty())
        assertEquals(0, flipped)
    }

    @Test fun repeatedPresentationDisposesOldVisual() {
        setContent()
        val start = flipStart()
        composeRule.onNodeWithTag("pad").performTouchInput {
            down(start); moveTo(start + Offset(100 * density, 0f))
        }
        composeRule.runOnIdle { presentation.value++ }
        // Disposal occurs during the scheduled composition, not inside the state-write block.
        composeRule.waitForIdle()
        assertEquals(PreparationJoystickVisual(), visual.value)
        centerLabel("Drag").assertTextEquals("Drag")
        composeRule.onNodeWithTag("pad").performTouchInput { up() }
        assertTrue(rated.isEmpty())
    }

    @Test fun resetAloneCancelsOwnedGestureAndItsLaterReleaseCannotRateOrFlip() {
        setContent()
        val start = flipStart()
        composeRule.onNodeWithTag("pad").performTouchInput {
            down(start); moveTo(start + Offset(100 * density, 0f))
        }
        centerLabel("Easy").assertTextEquals("Easy")
        assertTrue(visual.value.active)
        composeRule.runOnIdle { reset.value = true }
        composeRule.waitForIdle()
        assertEquals(PreparationJoystickVisual(), visual.value)
        centerLabel("Drag").assertTextEquals("Drag")
        // Input stays enabled throughout. Removing reset must not recapture the held finger.
        composeRule.runOnIdle { reset.value = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pad").performTouchInput {
            moveTo(start + Offset(110 * density, 0f)); up()
        }
        composeRule.waitForIdle()
        assertEquals(PreparationJoystickVisual(), visual.value)
        assertTrue(rated.isEmpty())
        assertEquals(0, flipped)
    }
}
