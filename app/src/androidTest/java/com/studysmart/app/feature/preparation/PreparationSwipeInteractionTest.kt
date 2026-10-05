package com.studysmart.app.feature.preparation

import com.studysmart.app.testing.unclippedOriginInRoot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.navigation.AppRoutes
import com.studysmart.app.testing.awaitNodeWithTag
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import com.studysmart.app.testing.setStudySmartContent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PreparationSwipeInteractionTest {
    @get:Rule val composeRule = createComposeRule()
    private var database: StudySmartDatabase? = null
    @After fun close() { database?.close() }

    private fun open(longAnswer: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = createInMemoryStudySmartDatabase(context).also { database = it }
        if (longAnswer) db.openHelper.writableDatabase.execSQL(
            "UPDATE questions SET answer = ? WHERE id = ?",
            arrayOf("Long study answer\n".repeat(80), "question-bca-s1-dbms-topic"),
        )
        composeRule.setStudySmartContent(coreAlphaTestContainer(context, db), AppRoutes.prepareTopic("topic-bca-s1-dbms-01"))
        composeRule.awaitNodeWithTag("preparation_card_front", useUnmergedTree = true)
    }

    private fun reveal() {
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.awaitNodeWithTag("preparation_answer", useUnmergedTree = true)
    }

    private fun ratingCount(): Int = database!!.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM review_records").use { it.moveToFirst(); it.getInt(0) }

    private fun scrollPosition(): Float = composeRule.onNodeWithTag("preparation_content_scroll", useUnmergedTree = true)
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun scrollBodyBy(amount: Float) {
        composeRule.onNodeWithTag("preparation_content_scroll", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, amount) }
        composeRule.waitForIdle()
    }

    @Test fun longAnswerBlocksRatingsUntilBottomAndKeepsHeaderAndPaddingFixed() {
        open(longAnswer = true)
        reveal()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preparation_scroll_forward").assertIsEnabled()
        composeRule.onNodeWithTag("preparation_rating_easy").assertIsNotEnabled()
        composeRule.onNodeWithTag("preparation_content_scrollbar", useUnmergedTree = true).assertExists()
        val header = composeRule.onNodeWithTag("preparation_card_back", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val card = composeRule.onNodeWithTag("preparation_card").fetchSemanticsNode().boundsInRoot
        val body = composeRule.onNodeWithTag("preparation_answer_body", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(body.bottom < card.bottom)
        swipeRating(RatingSwipeDirection.RIGHT)
        swipeRating(RatingSwipeDirection.LEFT, "preparation_flip_button")
        assertEquals(0, ratingCount())
        scrollBodyBy(100000f)
        assertEquals(header, composeRule.onNodeWithTag("preparation_card_back", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        assertEquals(body, composeRule.onNodeWithTag("preparation_answer_body", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        composeRule.onNodeWithTag("preparation_rating_good").assertIsEnabled()
        scrollBodyBy(-100000f)
        composeRule.onNodeWithTag("preparation_rating_good").assertIsEnabled()
        composeRule.onNodeWithTag("preparation_rating_easy").performClick()
        composeRule.awaitNodeWithTag("preparation_summary_screen")
        assertEquals(1, ratingCount())
    }

    @Test fun scrollButtonsAdvanceAndReturnWithoutWritingHistory() {
        open(longAnswer = true)
        reveal()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preparation_scroll_backward").assertIsNotEnabled()
        composeRule.onNodeWithTag("preparation_scroll_forward").performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(scrollPosition() > 0f)
        composeRule.onNodeWithTag("preparation_scroll_backward").performClick()
        composeRule.waitForIdle()
        assertEquals(0f, scrollPosition(), 1f)
        assertEquals(0, ratingCount())
    }

    @Test fun scrollUnlockNoticeExpiresWithoutRelockingOrMovingControls() {
        open(longAnswer = true)
        reveal()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preparation_ratings_unlocked").assertDoesNotExist()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("preparation_content_scroll", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag("preparation_ratings_unlocked", useUnmergedTree = true).assertExists()
        val before = composeRule.onNodeWithTag("preparation_rating_pad").fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.onNodeWithTag("preparation_ratings_unlocked").assertDoesNotExist()
        assertEquals(before, composeRule.onNodeWithTag("preparation_rating_pad").fetchSemanticsNode().boundsInRoot)
        composeRule.onNodeWithTag("preparation_rating_good").assertIsEnabled()
        composeRule.mainClock.autoAdvance = true
        scrollBodyBy(-100000f)
        composeRule.onNodeWithTag("preparation_ratings_unlocked").assertDoesNotExist()
    }

    @Test fun shortAnswerUnlockDoesNotShowReadingNotice() {
        open()
        reveal()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preparation_rating_good").assertIsEnabled()
        composeRule.onNodeWithTag("preparation_ratings_unlocked").assertDoesNotExist()
    }

    @Test fun heldJoystickScrollsSlowlyAndStopsAtCenterWithoutRating() {
        open(longAnswer = true)
        reveal()
        composeRule.waitForIdle()
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput {
            down(center)
            moveBy(Offset(0f, -40f * density))
        }
        composeRule.mainClock.advanceTimeBy(1000)
        val first = scrollPosition()
        org.junit.Assert.assertTrue(first > 0f && first <= 100f * density)
        composeRule.mainClock.advanceTimeBy(1000)
        org.junit.Assert.assertTrue(scrollPosition() > first)
        val beforeFaster = scrollPosition()
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput { moveBy(Offset(0f, -60f * density)) }
        composeRule.mainClock.advanceTimeBy(1000)
        org.junit.Assert.assertTrue(scrollPosition() - beforeFaster > first)
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput { moveBy(Offset(0f, 100f * density)) }
        composeRule.mainClock.advanceTimeByFrame()
        val stopped = scrollPosition()
        composeRule.mainClock.advanceTimeBy(1000)
        assertEquals(stopped, scrollPosition(), 1f)
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(0, ratingCount())
    }

    @Test fun bottomReachedDuringHeldScrollDoesNotTurnReleaseIntoRating() {
        open(longAnswer = true)
        reveal()
        composeRule.waitForIdle()
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val maximum = composeRule.onNodeWithTag("preparation_content_scroll", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        scrollBodyBy(maximum - 30f * density)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput {
            down(center)
            moveBy(Offset(0f, -100f * density))
        }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.onNodeWithTag("preparation_scroll_forward").assertExists()
        composeRule.onNodeWithTag("preparation_rating_good").assertDoesNotExist()
        assertEquals(0, ratingCount())
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preparation_rating_good").assertIsEnabled()
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, ratingCount())
    }

    private fun swipeRating(direction: RatingSwipeDirection, tag: String = "preparation_rating_pad", fromGap: Boolean = false) {
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val pad = composeRule.onNodeWithTag("preparation_rating_pad").fetchSemanticsNode().boundsInRoot
        val horizontal = direction == RatingSwipeDirection.LEFT || direction == RatingSwipeDirection.RIGHT
        val distance = ratingDragThreshold(if (horizontal) pad.width else pad.height, density) + 8f * density
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
            val delta = when (direction) {
                RatingSwipeDirection.LEFT -> Offset(-distance, 0f)
                RatingSwipeDirection.RIGHT -> Offset(distance, 0f)
                RatingSwipeDirection.UP -> Offset(0f, -distance)
                RatingSwipeDirection.DOWN -> Offset(0f, distance)
            }
            val start = if (fromGap) Offset(8f * density, 8f * density)
                else if (tag == "preparation_rating_pad") Offset(center.x, height / 6f) else center
            swipe(start, start + delta, durationMillis = 450)
        }
    }

    private fun assertSwipe(direction: RatingSwipeDirection, tag: String = "preparation_rating_pad", fromGap: Boolean = false) {
        open()
        reveal()
        swipeRating(direction, tag, fromGap)
        composeRule.waitForIdle()
        assertEquals(1, ratingCount())
        val rating = database!!.openHelper.readableDatabase.query("SELECT rating FROM review_records").use {
            it.moveToFirst(); it.getString(0)
        }
        assertEquals(direction.rating.name.lowercase(), rating)
        if (direction == RatingSwipeDirection.RIGHT || direction == RatingSwipeDirection.UP) {
            composeRule.awaitNodeWithTag("preparation_summary_screen")
        } else {
            composeRule.awaitNodeWithTag("preparation_card_front", useUnmergedTree = true)
            composeRule.onNodeWithTag("preparation_rating_good").assertIsNotEnabled()
            composeRule.onNodeWithTag("preparation_remaining_count", useUnmergedTree = true).assertTextEquals("1 repeat left")
            composeRule.onNodeWithTag("preparation_count_${direction.rating.name.lowercase()}").assertDoesNotExist()
        }
    }

    @Test fun padLeftCommitsAgain() = assertSwipe(RatingSwipeDirection.LEFT)
    @Test fun padRightCommitsEasy() = assertSwipe(RatingSwipeDirection.RIGHT)
    @Test fun padUpCommitsGood() = assertSwipe(RatingSwipeDirection.UP)
    @Test fun padDownCommitsHard() = assertSwipe(RatingSwipeDirection.DOWN)

    @Test fun padPreviewDoesNotCommitBeforeReleaseAndCancelRestoresCard() {
        open()
        reveal()
        composeRule.waitForIdle()
        val rest = composeRule.onNodeWithTag("preparation_card").unclippedOriginInRoot()
        val pad = composeRule.onNodeWithTag("preparation_rating_pad").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("preparation_rating_pad").performTouchInput {
            down(center)
            moveBy(Offset(-pad.width / 3f, 0f))
        }
        composeRule.waitForIdle()
        assertEquals(0, ratingCount())
        val preview = composeRule.onNodeWithTag("preparation_card").unclippedOriginInRoot()
        org.junit.Assert.assertTrue(preview.x < rest.x)
        assertEquals(pad, composeRule.onNodeWithTag("preparation_rating_pad").fetchSemanticsNode().boundsInRoot)
        composeRule.onNodeWithTag("preparation_rating_pad").performTouchInput { cancel() }
        composeRule.waitForIdle()
        assertEquals(rest.x, composeRule.onNodeWithTag("preparation_card").unclippedOriginInRoot().x, 1f)
        assertEquals(0, ratingCount())
    }
    @Test fun padWhitespaceAlsoAcceptsSwipes() = assertSwipe(RatingSwipeDirection.RIGHT, fromGap = true)
    @Test fun swipeStartingOnFlipRatesWithoutClickThrough() = assertSwipe(RatingSwipeDirection.LEFT, "preparation_flip_button")
    @Test fun joystickRightCommitsEasy() = assertSwipe(RatingSwipeDirection.RIGHT, "preparation_flip_button")
    @Test fun joystickUpCommitsGood() = assertSwipe(RatingSwipeDirection.UP, "preparation_flip_button")
    @Test fun joystickDownCommitsHard() = assertSwipe(RatingSwipeDirection.DOWN, "preparation_flip_button")

    private fun assertSelected(rating: String?) {
        val flip = composeRule.onNodeWithTag("preparation_flip_button").fetchSemanticsNode().config
        val description = flip[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        if (rating == null) org.junit.Assert.assertFalse(description.contains("selected"))
        else {
            org.junit.Assert.assertTrue(description.contains("$rating selected"))
            val button = composeRule.onNodeWithTag("preparation_rating_$rating").fetchSemanticsNode().config
            assertEquals("Selected: $rating", button[androidx.compose.ui.semantics.SemanticsProperties.StateDescription])
        }
    }

    @Test fun joystickPreviewSwitchesDirectionsAndCenterReleaseCancelsWithoutFlip() {
        open()
        reveal()
        val rest = composeRule.onNodeWithTag("preparation_card").unclippedOriginInRoot()
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val distance = 100f * density
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput {
            down(center)
            moveBy(Offset(0f, -distance))
        }
        composeRule.waitForIdle()
        assertSelected("good")
        assertEquals(0, ratingCount())
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput {
            moveBy(Offset(distance, distance))
        }
        composeRule.waitForIdle()
        assertSelected("easy")
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput {
            moveBy(Offset(-distance, 0f))
        }
        composeRule.waitForIdle()
        assertSelected(null)
        composeRule.onNodeWithTag("preparation_swipe_preview").assertDoesNotExist()
        assertEquals(rest.x, composeRule.onNodeWithTag("preparation_card").unclippedOriginInRoot().x, 1f)
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(0, ratingCount())
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun joystickChangedDirectionCommitsOnlyFinalSelection() {
        open()
        reveal()
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val distance = 100f * density
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput {
            down(center)
            moveBy(Offset(-distance, 0f))
            moveBy(Offset(distance, -distance))
            up()
        }
        composeRule.awaitNodeWithTag("preparation_summary_screen")
        assertEquals(1, ratingCount())
        val rating = database!!.openHelper.readableDatabase.query("SELECT rating FROM review_records").use {
            it.moveToFirst(); it.getString(0)
        }
        assertEquals("good", rating)
    }

    @Test fun joystickDiagonalClearsSelectionAndDoesNotCommit() {
        open()
        reveal()
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val distance = 100f * density
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput {
            down(center)
            moveBy(Offset(distance, 0f))
        }
        composeRule.waitForIdle()
        assertSelected("easy")
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput { moveBy(Offset(0f, distance)) }
        composeRule.waitForIdle()
        assertSelected(null)
        composeRule.onNodeWithTag("preparation_swipe_preview").assertDoesNotExist()
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(0, ratingCount())
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun joystickReleasePositionAtCenterCancelsWithoutPriorMoveEvent() {
        open()
        reveal()
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput {
            down(center)
            moveBy(Offset(100f * density, 0f))
        }
        composeRule.waitForIdle()
        assertSelected("easy")
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput {
            updatePointerTo(0, center)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(0, ratingCount())
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun ordinarySwipeReleaseReversalKeepsAxisButUsesFinalSignedDirection() {
        open()
        reveal()
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val distance = 100f * density
        composeRule.onNodeWithTag("preparation_rating_good").performTouchInput {
            down(center)
            moveBy(Offset(distance, 0f))
        }
        composeRule.waitForIdle()
        assertSelected("easy")
        composeRule.onNodeWithTag("preparation_rating_good").performTouchInput {
            updatePointerTo(0, center + Offset(-distance, 0f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(1, ratingCount())
        val rating = database!!.openHelper.readableDatabase.query("SELECT rating FROM review_records").use {
            it.moveToFirst(); it.getString(0)
        }
        assertEquals("again", rating)
    }
    @Test fun swipeStartingOnRatingChoosesDirectionNotButtonLabel() = assertSwipe(RatingSwipeDirection.DOWN, "preparation_rating_good")

    @Test fun padTouchTapChoosesItsLabelInsteadOfASwipeDirection() {
        open()
        reveal()
        composeRule.onNodeWithTag("preparation_rating_easy").performTouchInput { click() }
        composeRule.awaitNodeWithTag("preparation_summary_screen")
        assertEquals(1, ratingCount())
    }

    @Test fun flipTouchTapRevealsAndCardTapFlipsBackWithoutRating() {
        open()
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput { click() }
        composeRule.awaitNodeWithTag("preparation_answer", useUnmergedTree = true)
        composeRule.onNodeWithTag("preparation_card").performTouchInput { click() }
        composeRule.awaitNodeWithTag("preparation_card_front", useUnmergedTree = true)
        assertEquals(0, ratingCount())
    }

    @Test fun cardHeaderProgressAndSupportingDragsNeverRate() {
        open()
        reveal()
        RatingSwipeDirection.entries.forEach { swipeRating(it, "preparation_card") }
        swipeRating(RatingSwipeDirection.DOWN, "preparation_remaining_count")
        swipeRating(RatingSwipeDirection.RIGHT, "preparation_progress")
        swipeRating(RatingSwipeDirection.UP, "preparation_supporting_text")
        composeRule.waitForIdle()
        assertEquals(0, ratingCount())
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun gestureStartingOnProgressCannotBecomeRatingWhenItEntersPad() {
        open()
        reveal()
        val target = composeRule.onNodeWithTag("preparation_rating_pad").fetchSemanticsNode().boundsInRoot.center
        val progress = composeRule.onNodeWithTag("preparation_progress").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("preparation_progress").performTouchInput {
            swipe(center, target - progress.topLeft, durationMillis = 450)
        }
        composeRule.waitForIdle()
        assertEquals(0, ratingCount())
    }

    @Test fun unrevealedShortCancelledAndMultiTouchDragsDoNotRateOrFlip() {
        open()
        swipeRating(RatingSwipeDirection.RIGHT)
        composeRule.onNodeWithTag("preparation_card_front", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, ratingCount())
        reveal()
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        composeRule.onNodeWithTag("preparation_flip_button").performTouchInput {
            swipe(center, center + Offset(40f * density, 0f), durationMillis = 450)
        }
        composeRule.onNodeWithTag("preparation_rating_pad").performTouchInput {
            down(center)
            moveBy(Offset(100f, 0f))
            cancel()
        }
        composeRule.onNodeWithTag("preparation_rating_pad").performTouchInput {
            down(0, center)
            down(1, center + Offset(5f, 5f))
            moveBy(0, Offset(100f, 0f))
            up(0)
            up(1)
        }
        composeRule.waitForIdle()
        assertEquals(0, ratingCount())
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun longAnswerDiagonalAndVerticalScrollNeverRate() {
        open(longAnswer = true)
        reveal()
        composeRule.onNodeWithTag("preparation_card_viewport").performTouchInput {
            down(Offset(center.x, bottom - 24f))
            moveBy(Offset(-24f, -24f))
            moveBy(Offset(-70f, 0f))
            up()
            swipe(Offset(center.x, bottom - 20f), center, durationMillis = 450)
        }
        composeRule.waitForIdle()
        assertEquals(0, ratingCount())
        composeRule.onNodeWithTag("preparation_scroll_forward").assertIsDisplayed()
        scrollBodyBy(100000f)
        swipeRating(RatingSwipeDirection.DOWN)
        composeRule.waitForIdle()
        assertEquals(1, ratingCount())
    }
}
