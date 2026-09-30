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
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.TestQuestionSource
import com.studysmart.app.core.navigation.AppRoutes
import com.studysmart.app.testing.awaitNodeWithTag
import com.studysmart.app.testing.awaitNodeWithText
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import com.studysmart.app.testing.setStudySmartContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
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
        composeRule.awaitNodeWithTag("test_answer_input")
        composeRule.onNodeWithText("Question 1 of 2").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("test_submit").performClick()
        composeRule.awaitNodeWithText("Submit test?")
        composeRule.onNodeWithTag("test_confirm_submit").performClick()
        composeRule.awaitNodeWithText("Test results are available after MVP-9.")
    }

    @Test
    fun settingsCanEditDefaultsAndBackReturnsToSettings() {
        val preferences = InMemoryPreferencesRepository()
        composeRule.setStudySmartContent(container(preferences), startRoute = AppRoutes.SETTINGS)

        composeRule.awaitNodeWithTag("settings_screen")
        composeRule.onNodeWithText("Test defaults").performClick()
        composeRule.awaitNodeWithTag("test_defaults_screen")
        composeRule.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Increase Default duration in minutes").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Increase Question count").performClick()
        composeRule.awaitNodeWithText("11")
        assertEquals(11, preferences.current.testDefaults.questionCount)

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.awaitNodeWithTag("settings_screen")
    }

    @Test
    fun emptyChapterTestCanChangeDefaultsAndStartFromTheChapter() {
        val preferences = InMemoryPreferencesRepository(
            TestDefaults(questionSource = TestQuestionSource.IMPORTANT),
        )
        composeRule.setStudySmartContent(container(preferences))

        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performClick()
        composeRule.awaitNodeWithText("Choose Semester")
        composeRule.onNodeWithTag("period_period-bca-semester-1").performClick()
        composeRule.awaitNodeWithText("Select subject")
        composeRule.onNodeWithTag("subject_subject-bca-s1-dbms").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")

        assertEquals(TestQuestionSource.IMPORTANT, preferences.current.testDefaults.questionSource)
        composeRule.onAllNodesWithText("Test").get(0).performClick()
        composeRule.awaitNodeWithTag("no_test_questions_sheet")
        composeRule.awaitNodeWithText("No test attempt was created.")
        assertEquals(0, attemptCount())

        composeRule.onNodeWithText("Open Test Defaults").performClick()
        composeRule.awaitNodeWithTag("test_defaults_screen")
        composeRule.onNodeWithContentDescription("All question source").performClick()
        composeRule.waitUntil(5_000) {
            preferences.current.testDefaults.questionSource == TestQuestionSource.ALL
        }
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")

        composeRule.onAllNodesWithText("Test").get(0).performClick()
        composeRule.awaitNodeWithTag("test_answer_input")
        assertEquals(1, attemptCount())
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

    private fun container(
        preferences: InMemoryPreferencesRepository = InMemoryPreferencesRepository(),
    ): AppContainer {
        database = createInMemoryStudySmartDatabase(
            ApplicationProvider.getApplicationContext(),
        )
        return AppContainer(
            context = ApplicationProvider.getApplicationContext(),
            database = database!!,
            preferences = preferences,
        )
    }

    private fun attemptCount(): Int = database!!.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM test_attempts")
        .use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}

private class InMemoryPreferencesRepository(
    defaults: TestDefaults = TestDefaults(),
) : PreferencesRepository {
    private val state = MutableStateFlow(StudyPreferences(testDefaults = defaults))
    val current: StudyPreferences get() = state.value
    override val preferences: Flow<StudyPreferences> = state.asStateFlow()

    override suspend fun selectCourse(courseId: String) {
        state.value = state.value.copy(
            selectedContext = null,
            selectedCourseIdOverride = courseId,
            selectedAcademicPeriodIdOverride = null,
        )
    }

    override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) {
        state.value = state.value.copy(
            selectedContext = SelectedAcademicContext(courseId, academicPeriodId),
            selectedCourseIdOverride = courseId,
            selectedAcademicPeriodIdOverride = academicPeriodId,
        )
    }

    override suspend fun clearSelectedCourse() {
        state.value = state.value.copy(
            selectedContext = null,
            selectedCourseIdOverride = null,
            selectedAcademicPeriodIdOverride = null,
        )
    }

    override suspend fun clearSelectedPeriod() {
        state.value = state.value.copy(
            selectedContext = null,
            selectedCourseIdOverride = state.value.selectedCourseId,
            selectedAcademicPeriodIdOverride = null,
        )
    }

    override suspend fun clearSelectedContext() {
        clearSelectedCourse()
    }

    override suspend fun updateTestDefaults(defaults: TestDefaults) {
        state.value = state.value.copy(testDefaults = defaults)
    }
}
