package com.studysmart.app.feature.academic

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.studysmart.app.app.AppContainer
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.data.SelectedAcademicContext
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.navigation.AppRoutes
import com.studysmart.app.testing.awaitNodeWithTag
import com.studysmart.app.testing.awaitNodeWithText
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import com.studysmart.app.testing.setStudySmartContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var database: com.studysmart.app.core.database.StudySmartDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun homeBrowsePathReachesChaptersAndKeepsBottomNavigationAvailable() {
        composeRule.setStudySmartContent(container())

        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performClick()
        composeRule.awaitNodeWithText("Choose Semester")
        composeRule.onNodeWithTag("period_period-bca-semester-1").performClick()
        composeRule.awaitNodeWithText("Select subject")
        composeRule.onNodeWithTag("subject_subject-bca-s1-dbms").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")

        composeRule.onNodeWithTag("chapter_chapter-bca-s1-dbms-01").assertIsDisplayed()
        composeRule.onAllNodesWithText("Prepare").get(0).assertIsEnabled()
        composeRule.onAllNodesWithText("Prepare").get(1).assertIsNotEnabled()
        composeRule.onAllNodesWithText("Test").get(0).assertIsEnabled()
        composeRule.onAllNodesWithText("Test").get(1).assertIsNotEnabled()
        composeRule.onNodeWithTag("bottom_navigation").assertIsDisplayed()
    }

    @Test
    fun yearlyCourseUsesYearLabel() {
        composeRule.setStudySmartContent(container())

        composeRule.awaitNodeWithTag("course_course-yearly-example")
        composeRule.onNodeWithTag("course_course-yearly-example").performClick()
        composeRule.awaitNodeWithText("Choose Year")
        composeRule.onNodeWithText("Choose Year").assertIsDisplayed()
    }

    @Test
    fun chapterTestCreatesAttemptBeforeShowingMockTestRoute() {
        composeRule.setStudySmartContent(container())

        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performClick()
        composeRule.awaitNodeWithText("Choose Semester")
        composeRule.onNodeWithTag("period_period-bca-semester-1").performClick()
        composeRule.awaitNodeWithText("Select subject")
        composeRule.onNodeWithTag("subject_subject-bca-s1-dbms").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")

        composeRule.onAllNodesWithText("Test").get(0).performClick()
        composeRule.awaitNodeWithText("Test attempt created")
    }

    @Test
    fun coursesOriginIsRestoredByBackNavigation() {
        composeRule.setStudySmartContent(container(), startRoute = AppRoutes.COURSES)

        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performClick()
        composeRule.awaitNodeWithText("Choose Semester")
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.awaitNodeWithText("Choose a course to start studying")
        composeRule.onNodeWithTag("bottom_navigation_courses").assertIsDisplayed()
    }

    @Test
    fun missingCourseRouteShowsNonDestructiveRecovery() {
        composeRule.setStudySmartContent(
            container(),
            startRoute = AppRoutes.academicPeriod("deleted-course"),
        )

        composeRule.awaitNodeWithText("This content is no longer available.")
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.awaitNodeWithTag("home_screen")
    }

    @Test
    fun emptyCoursesStateIsSafe() {
        database = createInMemoryStudySmartDatabase(
            ApplicationProvider.getApplicationContext(),
            seed = false,
        )
        val container = AppContainer(
            context = ApplicationProvider.getApplicationContext(),
            database = database!!,
            preferences = InMemoryPreferencesRepository(),
        )
        composeRule.setStudySmartContent(container, startRoute = AppRoutes.COURSES)

        composeRule.awaitNodeWithText("No courses are configured on this device.")
        composeRule.onNodeWithTag("courses_empty").assertIsDisplayed()
    }

    private fun container(): AppContainer {
        database = createInMemoryStudySmartDatabase(
            ApplicationProvider.getApplicationContext(),
        )
        return AppContainer(
            context = ApplicationProvider.getApplicationContext(),
            database = database!!,
            preferences = InMemoryPreferencesRepository(),
        )
    }
}

private class InMemoryPreferencesRepository : PreferencesRepository {
    private val state = MutableStateFlow(StudyPreferences())
    override val preferences: Flow<StudyPreferences> = state.asStateFlow()

    override suspend fun selectCourse(courseId: String) {
        state.value = StudyPreferences(selectedCourseIdOverride = courseId)
    }

    override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) {
        state.value = StudyPreferences(SelectedAcademicContext(courseId, academicPeriodId))
    }

    override suspend fun clearSelectedCourse() {
        state.value = StudyPreferences()
    }

    override suspend fun clearSelectedPeriod() {
        state.value = StudyPreferences(selectedCourseIdOverride = state.value.selectedCourseId)
    }

    override suspend fun clearSelectedContext() {
        state.value = StudyPreferences()
    }
}
