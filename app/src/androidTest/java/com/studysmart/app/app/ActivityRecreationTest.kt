package com.studysmart.app.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.ui.StudySmartTheme
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.testing.LifecycleTestActivity
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real Activity recreation with an isolated graph; never clears the user's database. */
class ActivityRecreationTest {
    @get:Rule val composeRule = createEmptyComposeRule()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var database: StudySmartDatabase
    private lateinit var graph: AppContainer
    private var scenario: ActivityScenario<LifecycleTestActivity>? = null
    private val installedActivities = java.util.WeakHashMap<Activity, Boolean>()
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) {
            if (activity is LifecycleTestActivity && installedActivities.put(activity, true) == null) activity.setContent {
                StudySmartTheme { StudySmartApp(container = graph) }
            }
        }
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    @Before fun launchIsolatedActivity() {
        database = createInMemoryStudySmartDatabase(application)
        graph = coreAlphaTestContainer(application, database)
        application.registerActivityLifecycleCallbacks(callbacks)
        scenario = ActivityScenario.launch(LifecycleTestActivity::class.java)
        composeRule.awaitNodeWithTag("course_course-bca")
    }

    @After fun closeIsolatedActivity() {
        scenario?.close()
        application.unregisterActivityLifecycleCallbacks(callbacks)
        database.close()
    }

    @Test fun revealedPreparationSurvivesActivityRecreationWithoutWritingHistory() {
        openChapters()
        composeRule.onAllNodesWithText("Prepare")[0].performScrollTo().performClick()
        composeRule.awaitNodeWithTag("preparation_card_front", useUnmergedTree = true)
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.awaitNodeWithTag("preparation_card_back", useUnmergedTree = true)
        awaitRatingEnabled()
        val before = scalar("SELECT COUNT(*) FROM preparation_sessions")
        scenario!!.recreate()
        composeRule.awaitNodeWithTag("preparation_card_back", useUnmergedTree = true)
        awaitRatingEnabled()
        assertEquals(before, scalar("SELECT COUNT(*) FROM preparation_sessions"))
        assertEquals(0, scalar("SELECT COUNT(*) FROM review_records"))
    }

    @Test fun persistedAnswerAndFlagSurviveActivityRecreationWithoutCreatingAnotherAttempt() {
        openChapters()
        composeRule.onAllNodesWithText("Test")[0].performScrollTo().performClick()
        composeRule.awaitNodeWithTag("test_answer_input")
        composeRule.onNodeWithTag("test_answer_input").performScrollTo().performTextInput("database")
        composeRule.onNodeWithTag("test_flag").performScrollTo().performClick()
        composeRule.onNodeWithTag("test_next").performScrollTo().performClick()
        composeRule.onNodeWithTag("test_question_1").performScrollTo().performClick()
        composeRule.onNodeWithTag("test_answer_input").assertTextContains("database")
        val id = database.openHelper.readableDatabase.query("SELECT id FROM test_attempts").use {
            check(it.moveToFirst()) { "The isolated graph must own the created attempt" }; it.getString(0)
        }
        val before = runBlocking { graph.testAttemptRepository.observeAttempt(id).first() }!!
        scenario!!.recreate()
        composeRule.awaitNodeWithTag("test_answer_input")
        composeRule.onNodeWithTag("test_answer_input").assertTextContains("database")
        composeRule.onNodeWithTag("test_flag").assertTextContains("Flagged for review", substring = true)
        assertEquals(1, scalar("SELECT COUNT(*) FROM test_attempts"))
        val after = runBlocking { graph.testAttemptRepository.observeAttempt(id).first() }!!
        assertEquals(before.attempt.id, after.attempt.id)
        assertEquals(before.questions, after.questions)
    }

    private fun openChapters() {
        composeRule.onNodeWithTag("course_course-bca").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("period_period-bca-semester-1")
        composeRule.onNodeWithTag("period_period-bca-semester-1").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("subject_subject-bca-s1-dbms")
        composeRule.onNodeWithTag("subject_subject-bca-s1-dbms").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
    }

    private fun scalar(sql: String): Int = database.openHelper.readableDatabase.query(sql).use {
        it.moveToFirst(); it.getInt(0)
    }

    private fun awaitRatingEnabled() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("preparation_rating_good")
                .filter(isEnabled()).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag("preparation_rating_good").assertIsEnabled()
    }

    private fun ComposeTestRule.awaitNodeWithTag(tag: String, useUnmergedTree: Boolean = false) {
        waitUntil(5_000) {
            onAllNodesWithTag(tag, useUnmergedTree = useUnmergedTree)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }
}
