package com.studysmart.app.feature.preparation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.navigation.AppRoutes
import com.studysmart.app.testing.awaitNodeWithTag
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import com.studysmart.app.testing.setStudySmartContent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun ratingCount(): Int = database!!.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM review_records").use { it.moveToFirst(); it.getInt(0) }

    private fun swipeRating(direction: RatingSwipeDirection, fromPad: Boolean = false, fromGap: Boolean = false) {
        val tag = if (fromPad) "preparation_rating_pad" else if (direction == RatingSwipeDirection.UP || direction == RatingSwipeDirection.DOWN) {
            "preparation_swipe_grip"
        } else "preparation_card"
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val viewport = composeRule.onNodeWithTag("preparation_study_viewport").fetchSemanticsNode().boundsInRoot
        val card = composeRule.onNodeWithTag("preparation_card").fetchSemanticsNode().boundsInRoot
        val target = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val horizontal = direction == RatingSwipeDirection.LEFT || direction == RatingSwipeDirection.RIGHT
        val distance = ratingDragThreshold(if (horizontal) card.width else viewport.height, density) + 16f * density
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
            val delta = when (direction) {
                RatingSwipeDirection.LEFT -> Offset(-distance, 0f)
                RatingSwipeDirection.RIGHT -> Offset(distance, 0f)
                RatingSwipeDirection.UP -> Offset(0f, -distance)
                RatingSwipeDirection.DOWN -> Offset(0f, distance)
            }
            val start = if (fromGap) Offset(8f * density, 8f * density) else when (direction) {
                RatingSwipeDirection.LEFT -> Offset(right - 8f * density, center.y)
                RatingSwipeDirection.RIGHT -> Offset(left + 8f * density, center.y)
                RatingSwipeDirection.UP -> Offset(center.x, bottom - 8f * density)
                RatingSwipeDirection.DOWN -> Offset(center.x, top + 8f * density)
            }
            val endInRoot = target.topLeft + start + delta
            assertTrue("Swipe endpoint must remain inside app width", endInRoot.x >= 0 && endInRoot.x <
                ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.widthPixels)
            assertTrue("Swipe endpoint must remain below system status area", endInRoot.y >= 0)
            swipe(start, start + delta, durationMillis = 450)
        }
    }

    private fun assertSwipe(direction: RatingSwipeDirection, fromPad: Boolean = false, fromGap: Boolean = false) {
        open()
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.awaitNodeWithTag("preparation_answer", useUnmergedTree = true)
        swipeRating(direction, fromPad, fromGap)
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
            composeRule.onNodeWithTag("preparation_count_${direction.rating.name.lowercase()}").assertIsDisplayed()
        }
    }

    @Test fun leftCommitsAgain() = assertSwipe(RatingSwipeDirection.LEFT)
    @Test fun rightCommitsEasyAndCompletesAfterExit() = assertSwipe(RatingSwipeDirection.RIGHT)
    @Test fun headerUpCommitsGoodAndCompletesAfterExit() = assertSwipe(RatingSwipeDirection.UP)
    @Test fun headerDownCommitsHard() = assertSwipe(RatingSwipeDirection.DOWN)
    @Test fun padLeftCommitsAgain() = assertSwipe(RatingSwipeDirection.LEFT, fromPad = true)
    @Test fun padRightCommitsEasy() = assertSwipe(RatingSwipeDirection.RIGHT, fromPad = true)
    @Test fun padUpCommitsGood() = assertSwipe(RatingSwipeDirection.UP, fromPad = true)
    @Test fun padDownCommitsHard() = assertSwipe(RatingSwipeDirection.DOWN, fromPad = true)
    @Test fun padWhitespaceAlsoAcceptsSwipes() = assertSwipe(RatingSwipeDirection.RIGHT, fromPad = true, fromGap = true)

    @Test fun padTapChoosesItsLabelInsteadOfASwipeDirection() {
        open()
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.awaitNodeWithTag("preparation_answer", useUnmergedTree = true)
        composeRule.onNodeWithTag("preparation_rating_easy").performClick()
        composeRule.awaitNodeWithTag("preparation_summary_screen")
        assertEquals(1, ratingCount())
    }

    @Test fun diagonalTextScrollCannotBecomeARatingWhenItTurnsHorizontal() {
        open(longAnswer = true)
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.awaitNodeWithTag("preparation_answer", useUnmergedTree = true)
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        composeRule.onNodeWithTag("preparation_left_viewport").performTouchInput {
            down(Offset(center.x, bottom - 24f * density))
            moveBy(Offset(-24f * density, -24f * density))
            moveBy(Offset(-70f * density, 0f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(0, ratingCount())
        composeRule.onNodeWithTag("preparation_rating_good").assertIsDisplayed()
    }

    @Test fun unrevealedAndShortDragsDoNotRate() {
        open()
        swipeRating(RatingSwipeDirection.RIGHT)
        swipeRating(RatingSwipeDirection.DOWN, fromPad = true)
        assertEquals(0, ratingCount())
        composeRule.onNodeWithTag("preparation_rating_good").assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.awaitNodeWithTag("preparation_answer", useUnmergedTree = true)
        composeRule.onNodeWithTag("preparation_card").performTouchInput {
            swipe(center, center + Offset(35f, 0f), durationMillis = 450)
        }
        composeRule.waitForIdle()
        assertEquals(0, ratingCount())
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun longAnswerBodyScrollDoesNotCommitRating() {
        open(longAnswer = true)
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.awaitNodeWithTag("preparation_answer", useUnmergedTree = true)
        composeRule.onNodeWithTag("preparation_left_viewport").performTouchInput {
            swipe(Offset(center.x, bottom - 20f), center, durationMillis = 450)
        }
        composeRule.waitForIdle()
        assertEquals(0, ratingCount())
        composeRule.onNodeWithTag("preparation_rating_good").assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_swipe_grip", useUnmergedTree = true).performScrollTo()
        swipeRating(RatingSwipeDirection.DOWN)
        composeRule.waitForIdle()
        assertEquals(1, ratingCount())
    }
}
