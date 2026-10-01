package com.studysmart.app.feature.academic

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import com.studysmart.app.testing.assertNoNodeWithTag
import com.studysmart.app.testing.awaitNodeWithText
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import com.studysmart.app.testing.setStudySmartContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
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

    @Test
    fun defaultsTabExitWaitsForPendingPreferenceWrite() {
        val backing = InMemoryPreferencesRepository()
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val preferences = object : PreferencesRepository by backing {
            override suspend fun updateTestDefaults(defaults: TestDefaults) {
                entered.complete(Unit)
                release.await()
                backing.updateTestDefaults(defaults)
            }
        }
        composeRule.setStudySmartContent(container(preferences), startRoute = AppRoutes.TEST_DEFAULTS)
        composeRule.awaitNodeWithText("Valid changes are saved automatically.")
        composeRule.onNodeWithContentDescription("Increase Question count").performClick()
        composeRule.waitUntil(5_000) { entered.isCompleted }
        composeRule.onNodeWithTag("bottom_navigation_courses").performClick()
        composeRule.onNodeWithTag("test_defaults_screen").assertIsDisplayed()
        composeRule.assertNoNodeWithTag("courses_screen")
        assertEquals(10, backing.current.testDefaults.questionCount)
        release.complete(Unit)
        composeRule.awaitNodeWithTag("courses_screen")
        assertEquals(11, backing.current.testDefaults.questionCount)
    }

    @Test
    fun defaultsFailedSaveRetainsPendingTabExitUntilRetrySucceeds() {
        val backing = InMemoryPreferencesRepository()
        var writes = 0
        val preferences = object : PreferencesRepository by backing {
            override suspend fun updateTestDefaults(defaults: TestDefaults) {
                writes++
                if (writes == 1) error("injected defaults save failure")
                backing.updateTestDefaults(defaults)
            }
        }
        composeRule.setStudySmartContent(container(preferences), startRoute = AppRoutes.TEST_DEFAULTS)
        composeRule.awaitNodeWithText("Valid changes are saved automatically.")
        composeRule.onNodeWithContentDescription("Increase Question count").performClick()
        composeRule.awaitNodeWithTag("test_defaults_save_error")
        composeRule.onNodeWithTag("bottom_navigation_courses").performClick()
        composeRule.onNodeWithTag("test_defaults_screen").assertIsDisplayed()
        composeRule.assertNoNodeWithTag("courses_screen")
        assertEquals(10, backing.current.testDefaults.questionCount)
        composeRule.onNodeWithText("Retry").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("courses_screen")
        assertEquals(11, backing.current.testDefaults.questionCount)
        assertEquals(2, writes)
    }

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
        composeRule.onNodeWithTag("period_period-example-year-1").performClick()
        composeRule.awaitNodeWithTag("subject_subject-year1-foundations")
        composeRule.onNodeWithTag("subject_subject-year1-foundations").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
        composeRule.onNodeWithTag("chapter_chapter-year1-foundations-01").assertIsDisplayed()
    }

    @Test
    fun homeWaitsForCoursePersistenceBeforeOpeningPeriods() {
        val backing = InMemoryPreferencesRepository()
        val release = CompletableDeferred<Unit>()
        val preferences = object : PreferencesRepository by backing {
            override suspend fun selectCourse(courseId: String) {
                release.await()
                backing.selectCourse(courseId)
            }
        }
        composeRule.setStudySmartContent(container(preferences))
        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performClick()
        composeRule.awaitNodeWithTag("home_selection_saving")
        composeRule.onNodeWithTag("course_course-bca").assertHasNoClickAction()
        composeRule.onNodeWithTag("course_course-yearly-example").assertHasNoClickAction()
        composeRule.assertNoNodeWithTag("academic_period_screen")
        assertEquals(null, backing.current.selectedCourseId)
        release.complete(Unit)
        composeRule.awaitNodeWithTag("period_period-bca-semester-1")
        assertEquals("course-bca", backing.current.selectedCourseId)
        composeRule.onNodeWithTag("period_period-bca-semester-1").performClick()
        composeRule.awaitNodeWithTag("subjects_screen")
        assertEquals(SelectedAcademicContext("course-bca", "period-bca-semester-1"), backing.current.selectedContext)
    }

    @Test
    fun coursesSelectionFailureKeepsCardsAndRetriesBeforeNavigation() {
        val backing = InMemoryPreferencesRepository()
        val writes = java.util.concurrent.CopyOnWriteArrayList<String>()
        val preferences = object : PreferencesRepository by backing {
            override suspend fun selectCourse(courseId: String) {
                writes += courseId
                if (writes.size == 1) error("injected course save failure")
                backing.selectCourse(courseId)
            }
        }
        composeRule.setStudySmartContent(container(preferences), startRoute = AppRoutes.COURSES)
        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performClick()
        composeRule.awaitNodeWithTag("courses_selection_error")
        composeRule.onNodeWithTag("course_course-bca").assertHasNoClickAction()
        composeRule.assertNoNodeWithTag("academic_period_screen")
        assertEquals(null, backing.current.selectedCourseId)
        composeRule.onNodeWithText("Retry").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("period_period-bca-semester-1")
        assertEquals(listOf("course-bca", "course-bca"), writes.toList())
        assertEquals("course-bca", backing.current.selectedCourseId)
    }

    @Test
    fun periodSelectionFailureAndPendingRetryDoNotAdvanceOrAcceptMoreTaps() {
        val backing = InMemoryPreferencesRepository()
        val release = CompletableDeferred<Unit>()
        val writes = java.util.concurrent.CopyOnWriteArrayList<String>()
        val preferences = object : PreferencesRepository by backing {
            override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) {
                writes += academicPeriodId
                if (writes.size == 1) error("injected period save failure")
                release.await()
                backing.setSelectedContext(courseId, academicPeriodId)
            }
        }
        composeRule.setStudySmartContent(container(preferences), startRoute = AppRoutes.academicPeriod("course-bca"))
        composeRule.awaitNodeWithTag("period_period-bca-semester-1")
        composeRule.onNodeWithTag("period_period-bca-semester-1").performClick()
        composeRule.awaitNodeWithTag("academic_period_selection_error")
        composeRule.onNodeWithTag("period_period-bca-semester-1").assertHasNoClickAction()
        composeRule.assertNoNodeWithTag("subjects_screen")
        composeRule.onNodeWithText("Retry").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("academic_period_selection_saving")
        composeRule.onNodeWithTag("period_period-bca-semester-1").assertHasNoClickAction()
        composeRule.assertNoNodeWithTag("subjects_screen")
        assertEquals(null, backing.current.selectedContext)
        release.complete(Unit)
        composeRule.awaitNodeWithTag("subjects_screen")
        assertEquals(listOf("period-bca-semester-1", "period-bca-semester-1"), writes.toList())
        assertEquals(SelectedAcademicContext("course-bca", "period-bca-semester-1"), backing.current.selectedContext)
    }

    @Test
    fun mixedResultRetestsOnlyWeakQuestionAndNewTestUsesUpdatedDefaults() {
        val preferences = InMemoryPreferencesRepository(TestDefaults(questionCount = 2, questionSource = TestQuestionSource.ALL))
        composeRule.setStudySmartContent(container(preferences))
        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performClick()
        composeRule.awaitNodeWithTag("period_period-bca-semester-1")
        composeRule.onNodeWithTag("period_period-bca-semester-1").performClick()
        composeRule.awaitNodeWithTag("subject_subject-bca-s1-dbms")
        composeRule.onNodeWithTag("subject_subject-bca-s1-dbms").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
        composeRule.onAllNodesWithText("Test").get(0).performClick()

        composeRule.awaitNodeWithText("What is a database?")
        composeRule.onNodeWithTag("test_answer_input").performTextInput("database organised collection")
        composeRule.onNodeWithTag("test_next").performScrollTo().performClick()
        composeRule.awaitNodeWithText("What is a DBMS?")
        composeRule.onNodeWithTag("test_answer_input").performTextInput("dbms")
        composeRule.onNodeWithTag("test_submit").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("test_confirm_submit")
        composeRule.onNodeWithTag("test_confirm_submit").performClick()
        composeRule.awaitNodeWithTag("test_result_screen")
        awaitEnabled("test_result_retest_weak")
        composeRule.onNodeWithText("2.67 / 4").assertIsDisplayed()
        composeRule.onNodeWithTag("test_result_review_count").assertIsDisplayed()
        composeRule.onNodeWithText("1").assertIsDisplayed()
        composeRule.onNodeWithTag("test_result_question_1").assertTextContains("No review flag", substring = true)
        composeRule.onNodeWithTag("test_result_question_2").assertTextContains("Needs review", substring = true)
        composeRule.onNodeWithTag("test_result_question_2").performScrollTo().performClick()
        composeRule.onNodeWithText("Your answer").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("dbms").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("test_result_retest_weak").performScrollTo().performClick()
        composeRule.awaitNodeWithText("Question 1 of 1")
        composeRule.onNodeWithText("What is a DBMS?").assertIsDisplayed()
        composeRule.onNodeWithTag("test_answer_input").performScrollTo().performTextInput("dbms storage access")
        composeRule.onNodeWithTag("test_submit").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("test_confirm_submit")
        composeRule.onNodeWithTag("test_confirm_submit").performClick()
        composeRule.awaitNodeWithTag("test_result_screen")
        awaitEnabled("test_result_new_test")
        runBlocking { preferences.updateTestDefaults(TestDefaults(questionCount = 1, questionSource = TestQuestionSource.ALL)) }
        composeRule.onNodeWithTag("test_result_new_test").performScrollTo().performClick()
        composeRule.awaitNodeWithText("Question 1 of 1")
        composeRule.onNodeWithTag("test_close").performClick()
        composeRule.onNodeWithTag("test_discard").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
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
        composeRule.awaitNodeWithTag("test_result_screen")
        composeRule.onNodeWithText("Test complete").assertIsDisplayed()
        composeRule.onNodeWithTag("test_result_keyword_match").assertIsDisplayed()
        composeRule.onNodeWithTag("test_result_question_1").performClick()
        composeRule.awaitNodeWithText("Your answer")
        composeRule.onNodeWithText("Keyword match is a text-matching estimate, not a measure of semantic correctness.")
            .assertIsDisplayed()
        awaitEnabled("test_result_new_test")
        composeRule.onNodeWithTag("test_result_new_test").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("test_answer_input")
        composeRule.onNodeWithTag("test_submit").performClick()
        composeRule.awaitNodeWithText("Submit test?")
        composeRule.onNodeWithTag("test_confirm_submit").performClick()
        composeRule.awaitNodeWithText("Test complete")
        awaitEnabled("test_result_retest_weak")
        composeRule.onNodeWithTag("test_result_retest_weak").performScrollTo().performClick()
        composeRule.awaitNodeWithText("Question 1 of 2")
        composeRule.onNodeWithTag("test_submit").performClick()
        composeRule.awaitNodeWithText("Submit test?")
        composeRule.onNodeWithTag("test_confirm_submit").performClick()
        composeRule.awaitNodeWithText("Test complete")
        composeRule.onNodeWithTag("test_result_back").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
    }

    @Test
    fun resultDisablesRetestWeakWhenNoAnswersNeedReview() {
        composeRule.setStudySmartContent(
            container(
                InMemoryPreferencesRepository(
                    TestDefaults(questionSource = TestQuestionSource.ALL),
                ),
            ),
        )

        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performClick()
        composeRule.awaitNodeWithText("Choose Semester")
        composeRule.onNodeWithTag("period_period-bca-semester-1").performClick()
        composeRule.awaitNodeWithText("Select subject")
        composeRule.onNodeWithTag("subject_subject-bca-s1-dbms").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
        composeRule.onAllNodesWithText("Test").get(0).performClick()
        composeRule.awaitNodeWithText("What is a database?")
        composeRule.onNodeWithTag("test_answer_input").performTextInput("database organised collection")
        composeRule.onNodeWithTag("test_next").performClick()
        composeRule.awaitNodeWithText("What is a DBMS?")
        composeRule.onNodeWithTag("test_answer_input").performTextInput("DBMS storage access")
        composeRule.onNodeWithTag("test_submit").performClick()
        composeRule.awaitNodeWithText("Submit test?")
        composeRule.onNodeWithTag("test_confirm_submit").performClick()

        composeRule.awaitNodeWithTag("test_result_retest_empty")
        composeRule.onNodeWithTag("test_result_retest_weak").assertIsNotEnabled()
        composeRule.onNodeWithTag("test_result_new_test").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("test_result_back").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
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
    fun emptyTestNotNowKeepsChapterContextAndCreatesNoAttempt() {
        composeRule.setStudySmartContent(
            container(InMemoryPreferencesRepository(TestDefaults(questionSource = TestQuestionSource.IMPORTANT))),
            startRoute = AppRoutes.subject("subject-bca-s1-dbms"),
        )
        composeRule.awaitNodeWithTag("chapters_screen")
        composeRule.onAllNodesWithText("Test").get(0).performClick()
        composeRule.awaitNodeWithTag("no_test_questions_sheet")
        assertEquals(0, attemptCount())
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
        composeRule.onNodeWithTag("chapter_chapter-bca-s1-dbms-01").assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("No test attempt was created.")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        assertEquals(0, attemptCount())
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
        preferences: PreferencesRepository = InMemoryPreferencesRepository(),
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

    private fun awaitEnabled(tag: String) {
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag(tag).assertIsEnabled() }.isSuccess
        }
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

    override suspend fun clearSelectedCourseIfUnchanged(courseId: String, academicPeriodId: String?): Boolean {
        if (state.value.selectedCourseId != courseId || state.value.selectedAcademicPeriodId != academicPeriodId) return false
        clearSelectedCourse()
        return true
    }

    override suspend fun clearSelectedPeriodIfUnchanged(courseId: String, academicPeriodId: String): Boolean {
        if (state.value.selectedCourseId != courseId || state.value.selectedAcademicPeriodId != academicPeriodId) return false
        clearSelectedPeriod()
        return true
    }
}
