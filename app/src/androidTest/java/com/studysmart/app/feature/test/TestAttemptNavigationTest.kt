package com.studysmart.app.feature.test

import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.app.MainActivity
import com.studysmart.app.app.StudySmartApp
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.TestQuestionSource
import com.studysmart.app.core.navigation.AppRoutes
import com.studysmart.app.core.ui.StudySmartTheme
import com.studysmart.app.testing.awaitNodeWithText
import com.studysmart.app.testing.awaitNodeWithTag
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import com.studysmart.app.testing.coreAlphaTestContainer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TestAttemptNavigationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private var database: com.studysmart.app.core.database.StudySmartDatabase? = null

    @After fun closeDatabase() { database?.close() }

    @Test
    fun navigatorFlagsSubmitCancelAndBackExitsPreserveOrDiscardTheActiveAttempt() {
        val container = container()
        setStudySmartContent(container)
        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performClick()
        composeRule.awaitNodeWithTag("period_period-bca-semester-1")
        composeRule.onNodeWithTag("period_period-bca-semester-1").performClick()
        composeRule.awaitNodeWithTag("subject_subject-bca-s1-dbms")
        composeRule.onNodeWithTag("subject_subject-bca-s1-dbms").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
        composeRule.onAllNodesWithText("Test").get(0).performClick()
        composeRule.awaitNodeWithTag("test_answer_input")

        composeRule.onNodeWithTag("test_question_1").performScrollTo()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithTag("test_question_1").assertIsSelected()
        composeRule.onNodeWithTag("test_flag").performScrollTo().performClick()
        composeRule.onNodeWithTag("test_flag").assertTextContains("Flagged for review", substring = true)
        composeRule.onNodeWithContentDescription("Question 1, current, unanswered, flagged")
            .performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("test_question_2").performScrollTo().performClick()
        composeRule.awaitNodeWithText("Question 2 of 2")
        composeRule.onNodeWithTag("test_flag").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Question 2, current, unanswered, flagged")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_navigation_courses").performClick()
        composeRule.onNodeWithText("Discard test?").assertIsDisplayed()
        composeRule.onNodeWithTag("test_cancel_discard").performClick()
        composeRule.onNodeWithTag("test_attempt_screen").assertIsDisplayed()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("Discard test?").assertIsDisplayed()
        composeRule.onNodeWithTag("test_cancel_discard").performClick()
        composeRule.onNodeWithTag("test_attempt_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("test_question_1").performScrollTo().performClick()
        composeRule.awaitNodeWithText("Question 1 of 2")
        composeRule.onNodeWithTag("test_answer_input").performScrollTo()
            .performClick().performTextInput("database")
        composeRule.onNodeWithTag("test_next").performScrollTo().performClick()
        composeRule.awaitNodeWithText("Question 2 of 2")
        composeRule.onNodeWithContentDescription("Question 1, answered, flagged")
            .performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("test_submit").performScrollTo().performClick()
        composeRule.onNodeWithText(
            "Answered: 1 · Unanswered: 1 · Flagged: 2.",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("test_attempt_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("test_close").performClick()
        composeRule.onNodeWithTag("test_discard").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")

        composeRule.onAllNodesWithText("Test").get(0).performClick()
        composeRule.awaitNodeWithTag("test_answer_input")
        composeRule.onNodeWithTag("bottom_navigation_courses").performClick()
        composeRule.onNodeWithText("Discard test?").assertIsDisplayed()
        composeRule.onNodeWithTag("test_discard").performClick()
        composeRule.awaitNodeWithTag("courses_screen")
        val transientAttempts = database!!.openHelper.writableDatabase
            .query("SELECT COUNT(*) FROM test_attempts WHERE status IN ('created', 'in_progress')")
            .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        assertEquals(0, transientAttempts)
    }

    @Test
    fun thirtyQuestionNavigatorStaysScrollableAndAccessibleAtDoubleFontScale() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = createInMemoryStudySmartDatabase(context)
        repeat(28) { index ->
            val id = "layout-question-${index + 1}"
            database!!.openHelper.writableDatabase.execSQL(
                """INSERT INTO questions
                    (id, chapterId, topicId, question, answer, questionType, difficulty, maxScore,
                     explanation, isActive, sortOrder, createdAt, updatedAt)
                    VALUES (?, ?, NULL, ?, ?, 'written', 'medium', 2.0, '', 1, ?, 1767225600000, 1767225600000)""".trimIndent(),
                arrayOf<Any?>(
                    id,
                    "chapter-bca-s1-dbms-01",
                    "Long generated question ${index + 1}",
                    "Generated reference answer",
                    index + 10,
                ),
            )
        }
        val container = coreAlphaTestContainer(context, database!!)
        runBlocking {
            container.preferences.updateTestDefaults(
                TestDefaults(questionCount = 30, questionSource = TestQuestionSource.ALL),
            )
        }
        setStudySmartContent(container, fontScale = 2f)
        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("period_period-bca-semester-1")
        composeRule.onNodeWithTag("period_period-bca-semester-1").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("subject_subject-bca-s1-dbms")
        composeRule.onNodeWithTag("subject_subject-bca-s1-dbms").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
        composeRule.onAllNodesWithText("Test").get(0).performScrollTo().performClick()
        composeRule.awaitNodeWithText("Question 1 of 30")
        composeRule.onNodeWithTag("test_question_navigator").assertIsDisplayed()
        composeRule.onNodeWithTag("test_question_30").performScrollTo()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        composeRule.awaitNodeWithText("Question 30 of 30")
        composeRule.onNodeWithTag("test_submit").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("test_close").performClick()
        composeRule.onNodeWithTag("test_discard").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
    }

    private fun container(): com.studysmart.app.app.AppContainer {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = createInMemoryStudySmartDatabase(context)
        val container = coreAlphaTestContainer(context, database!!)
        runBlocking {
            container.preferences.updateTestDefaults(
                TestDefaults(questionCount = 2, questionSource = TestQuestionSource.ALL),
            )
        }
        return container
    }

    private fun setStudySmartContent(
        container: com.studysmart.app.app.AppContainer,
        fontScale: Float = 1f,
    ) {
        composeRule.runOnIdle {
            val content = composeRule.activity.findViewById<ViewGroup>(android.R.id.content)
            (content.getChildAt(0) as ComposeView).setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = fontScale),
                ) {
                    StudySmartTheme { StudySmartApp(startRoute = AppRoutes.HOME, container = container) }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
