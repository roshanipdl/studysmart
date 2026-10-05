package com.studysmart.app.feature.progress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.navigation.AppRoutes
import com.studysmart.app.testing.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class ProgressNavigationTest {
    @get:Rule val compose = createComposeRule()
    private var database: StudySmartDatabase? = null
    @Test fun noActivityCanBrowseCourses() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val graph = coreAlphaTestContainer(context, createInMemoryStudySmartDatabase(context).also { database = it })
        compose.setStudySmartContent(graph, AppRoutes.PROGRESS)
        compose.awaitNodeWithTag("progress_browse")
        compose.onNodeWithTag("progress_browse").performClick()
        compose.awaitNodeWithTag("courses_screen")
    }
    @After fun close() { database?.close() }

    @Test fun dailyGoalEditorUpdatesProgressAndDoesNotChangeSessionCount() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = createInMemoryStudySmartDatabase(context).also { database = it }
        val graph = coreAlphaTestContainer(context, db)
        runBlocking {
            db.preparationDao().insertReviewRecord(com.studysmart.app.core.database.ReviewRecordEntity("rated", "local-user", null,
                "question-bca-s1-dbms-direct", com.studysmart.app.core.model.Rating.GOOD, null, graph.clock.instant()))
        }
        compose.setStudySmartContent(graph, AppRoutes.PROGRESS)
        compose.awaitNodeWithText("1 of 10 cards")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.awaitNodeWithTag("daily_goal_increment")
        compose.onNodeWithTag("daily_goal_increment").performScrollTo().performClick()
        compose.awaitNodeWithText("11")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.awaitNodeWithText("1 of 11 cards")
        runBlocking { org.junit.Assert.assertEquals(10, graph.preferences.preferences.first().preparationDefaults.questionCount) }
    }
}
