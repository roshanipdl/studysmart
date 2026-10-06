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
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
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

    private fun setContent(fontScale: Float = 1f, darkTheme: Boolean = false) {
        composeRule.setContent {
            StudySmartTheme(darkTheme = darkTheme) {
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
                            ratingPad = { modifier, ready, ratingGestureActive, direction, scrollDirection, bounds ->
                                PreparationRatingPad(
                                    enabled = unlocked.value && ready,
                                    ratingsUnlocked = unlocked.value,
                                    ratingGestureActive = ratingGestureActive,
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
                                Box(modifier.width(320.dp).height(500.dp).testTag("viewport")) {
                                    pad()
                                    // Keep the translated reading layer outside all pad pixel captures.
                                    Box(Modifier.align(Alignment.BottomStart).width(320.dp).height(48.dp)
                                        .testTag("reading_slot")) { card(true) }
                                }
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

    // Interior excludes circular edges and selected outlines but includes arrows/labels.
    private fun ratingPixels(): Map<String, List<Color>> = listOf("again", "hard", "good", "easy").associateWith { name ->
        val pixels = composeRule.onNodeWithTag("preparation_rating_$name").captureToImage().toPixelMap()
        buildList {
            for (y in pixels.height / 4 until pixels.height * 3 / 4)
                for (x in pixels.width / 4 until pixels.width * 3 / 4) add(pixels[x, y])
        }
    }

    private fun assertReadingDoesNotOverlapPad() {
        val pad = composeRule.onNodeWithTag("pad").unclippedControlBoundsInRoot()
        val reading = composeRule.onNodeWithTag("reading_slot", true).unclippedControlBoundsInRoot()
        // The production preview can translate the reading card by at most 16dp.
        assertTrue("Reading layer must remain outside pad, including preview travel",
            reading.top - 16 * density >= pad.bottom)
    }

    private fun assertRatingPixelsEqual(expected: Map<String, List<Color>>, phase: String) {
        val actual = ratingPixels()
        expected.forEach { (rating, before) ->
            val after = actual.getValue(rating)
            assertEquals("$phase/$rating sample size", before.size, after.size)
            val first = before.indices.firstOrNull { before[it] != after[it] }
            // Do not dump entire images: Android truncates the message before actual pixels.
            assertTrue("$phase/$rating: first mismatch=$first, expected=${first?.let { before[it] }}, " +
                "actual=${first?.let { after[it] }}, changed=${before.indices.count { before[it] != after[it] }}",
                first == null)
        }
    }

    private fun verifyDragColors(dark: Boolean, ordinary: Boolean) {
        setContent(darkTheme = dark)
        assertReadingDoesNotOverlapPad()
        listOf("again", "hard", "good", "easy").forEach {
            composeRule.onNodeWithTag("preparation_rating_$it").assertIsEnabled()
        }
        val idle = ratingPixels()
        val pad = composeRule.onNodeWithTag("pad").unclippedControlBoundsInRoot()
        val start = if (ordinary) composeRule.onNodeWithTag("preparation_rating_again")
            .unclippedControlBoundsInRoot().center - pad.topLeft else flipStart()
        composeRule.onNodeWithTag("pad").performTouchInput {
            down(start); moveTo(start + Offset(100 * density, 0f))
        }
        listOf("again", "hard", "good", "easy").forEach {
            composeRule.onNodeWithTag("preparation_rating_$it").assertIsNotEnabled()
        }
        assertRatingPixelsEqual(idle, "claimed drag")
        if (!ordinary) {
            composeRule.onNodeWithTag("pad").performTouchInput { moveTo(start + Offset(40 * density, 40 * density)) }
            assertRatingPixelsEqual(idle, "held diagonal")
            composeRule.onNodeWithTag("pad").performTouchInput { moveTo(start) }
            assertRatingPixelsEqual(idle, "held center")
            composeRule.onNodeWithTag("pad").performTouchInput { up() }
            assertTrue(rated.isEmpty())
        } else {
            composeRule.onNodeWithTag("pad").performTouchInput { up() }
            assertEquals(listOf(Rating.EASY), rated)
        }
        assertEquals(0, flipped)
    }

    @Test fun lightJoystickDragKeepsAllIdleContainerAndContentPixels() = verifyDragColors(false, false)
    @Test fun darkJoystickDragKeepsAllIdleContainerAndContentPixels() = verifyDragColors(true, false)
    @Test fun lightOrdinaryDragKeepsAllIdleContainerAndContentPixels() = verifyDragColors(false, true)
    @Test fun darkOrdinaryDragKeepsAllIdleContainerAndContentPixels() = verifyDragColors(true, true)

    @Test fun cancellationThenResetRestoresMutedColorsWithoutRating() {
        setContent()
        assertReadingDoesNotOverlapPad()
        val idle = ratingPixels()
        val start = flipStart()
        composeRule.onNodeWithTag("pad").performTouchInput { down(start); moveTo(start + Offset(100 * density, 0f)) }
        assertRatingPixelsEqual(idle, "before cancellation")
        composeRule.onNodeWithTag("pad").performTouchInput { cancel() }
        assertRatingPixelsEqual(idle, "after cancellation")
        composeRule.onNodeWithTag("pad").performTouchInput { down(start); moveTo(start + Offset(100 * density, 0f)) }
        composeRule.runOnIdle { reset.value = true }
        composeRule.waitForIdle()
        val muted = ratingPixels()
        idle.keys.forEach { rating ->
            composeRule.onNodeWithTag("preparation_rating_$rating").assertIsNotEnabled()
            assertTrue("Reset must mute $rating", idle.getValue(rating) != muted.getValue(rating))
        }
        composeRule.onNodeWithTag("pad").performTouchInput { up() }
        assertTrue(rated.isEmpty())
        assertEquals(0, flipped)
    }

    @Test fun narrow200PercentTargetsStayInsideViewportAndRealTapsWork() {
        setContent(2f)
        assertReadingDoesNotOverlapPad()
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
