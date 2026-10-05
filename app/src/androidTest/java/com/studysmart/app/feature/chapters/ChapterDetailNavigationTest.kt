package com.studysmart.app.feature.chapters

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.navigation.compose.rememberNavController
import com.studysmart.app.app.AppContainer
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.data.SelectedAcademicContext
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.navigation.AppRoutes
import com.studysmart.app.core.navigation.AppNavHost
import com.studysmart.app.core.navigation.navigateContextSelection
import com.studysmart.app.core.navigation.navigateChapterDetail
import com.studysmart.app.core.navigation.navigateTopLevel
import com.studysmart.app.core.ui.StudySmartTheme
import com.studysmart.app.testing.awaitNodeWithTag
import com.studysmart.app.testing.awaitNodeWithText
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import com.studysmart.app.testing.setStudySmartContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Owns an isolated in-memory database; never clears installed application data. */
class ChapterDetailNavigationTest {
    @get:Rule val compose = createComposeRule()
    private var database: StudySmartDatabase? = null

    private fun container(): AppContainer {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = createInMemoryStudySmartDatabase(context).also { database = it }
        return AppContainer(context, database = db, preferences = ChapterDetailTestPreferences())
    }

    @Test fun chapterDetailPrepareUsesExistingSessionRoute() {
        compose.setStudySmartContent(container(), AppRoutes.chapter("chapter-bca-s1-dbms-01"))
        compose.awaitNodeWithTag("chapter_detail_prepare")
        compose.onNodeWithTag("chapter_detail_prepare").performScrollTo().performClick()
        compose.awaitNodeWithTag("preparation_session_screen")
    }

    @Test fun emptyChapterDisablesActions() {
        compose.setStudySmartContent(container(), AppRoutes.chapter("chapter-bca-s1-dbms-02"))
        compose.awaitNodeWithTag("chapter_detail_prepare")
        compose.onNodeWithTag("chapter_detail_prepare").assertHasNoClickAction()
        compose.onNodeWithTag("chapter_detail_test").assertHasNoClickAction()
    }

    @Test fun missingChapterAllowsSafeBack() {
        compose.setStudySmartContent(container(), AppRoutes.chapter("missing"))
        compose.awaitNodeWithText("Chapter unavailable")
        compose.onNodeWithText("Chapter unavailable").assertIsDisplayed()
        compose.onNodeWithTag("bottom_navigation_courses").performClick()
        compose.awaitNodeWithTag("courses_screen")
    }

    @Test fun progressContextSelectionReturnsToProgressInsteadOfSubjects() = verifyContextReturn(AppRoutes.PROGRESS)

    @Test fun importantContextSelectionReturnsToImportantInsteadOfSubjects() = verifyContextReturn(AppRoutes.IMPORTANT)

    private fun verifyContextReturn(route: String) {
        val container = container()
        lateinit var controller: androidx.navigation.NavHostController
        compose.setContent {
            StudySmartTheme {
                val navController = rememberNavController()
                controller = navController
                AppNavHost(navController, container)
                LaunchedEffect(Unit) { navController.navigateContextSelection(route) }
            }
        }
        compose.awaitNodeWithTag("course_course-bca")
        compose.onNodeWithTag("course_course-bca").performClick()
        compose.awaitNodeWithTag("period_period-bca-semester-1")
        compose.onNodeWithTag("period_period-bca-semester-1").performClick()
        compose.waitUntil(5_000) { controller.currentDestination?.route == route }
        compose.runOnIdle {
            assertEquals(route, controller.currentDestination?.route)
            controller.navigateTopLevel(AppRoutes.COURSES)
            controller.navigateTopLevel(AppRoutes.REVIEW)
            controller.navigateTopLevel(AppRoutes.IMPORTANT)
            org.junit.Assert.assertTrue(controller.popBackStack())
            assertEquals(AppRoutes.HOME, controller.currentDestination?.route)
        }
    }

    @Test fun chapterToolbarBackReturnsToItsSubject() {
        val (controller, _) = openChapterFromHome(container())
        compose.onNodeWithContentDescription("Back").performClick()
        compose.awaitNodeWithTag("chapters_screen")
        compose.onNodeWithTag("chapter_chapter-bca-s1-dbms-01").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.awaitNodeWithTag("subjects_screen")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.awaitNodeWithTag("academic_period_screen")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.awaitNodeWithTag("home_screen")
        compose.runOnIdle {
            assertEquals(AppRoutes.HOME, controller.currentDestination?.route)
            assertNull(controller.previousBackStackEntry)
        }
    }

    @Test fun chapterSystemBackReusesExistingAcademicAncestry() {
        val (controller, dispatcher) = openChapterFromHome(container(), withAcademicAncestry = true)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.awaitNodeWithTag("chapters_screen")
        compose.runOnIdle {
            assertEquals(AppRoutes.SUBJECT, controller.currentDestination?.route)
            assertEquals(AppRoutes.SUBJECTS, controller.previousBackStackEntry?.destination?.route)
            dispatcher.onBackPressed()
        }
        compose.awaitNodeWithTag("subjects_screen")
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.awaitNodeWithTag("academic_period_screen")
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.awaitNodeWithTag("home_screen")
        compose.runOnIdle { assertNull(controller.previousBackStackEntry) }
    }

    private fun openChapterFromHome(container: AppContainer, withAcademicAncestry: Boolean = false):
        Pair<androidx.navigation.NavHostController, androidx.activity.OnBackPressedDispatcher> {
        lateinit var controller: androidx.navigation.NavHostController
        lateinit var dispatcher: androidx.activity.OnBackPressedDispatcher
        compose.setContent {
            StudySmartTheme {
                val navController = rememberNavController()
                controller = navController
                dispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                AppNavHost(navController, container)
                LaunchedEffect(Unit) {
                    if (withAcademicAncestry) {
                        navController.navigate(AppRoutes.academicPeriod("course-bca"))
                        navController.navigate(AppRoutes.subjects("period-bca-semester-1"))
                        navController.navigate(AppRoutes.subject("subject-bca-s1-dbms"))
                    }
                    navController.navigateChapterDetail("chapter-bca-s1-dbms-01")
                }
            }
        }
        compose.awaitNodeWithTag("chapter_detail_prepare")
        return controller to dispatcher
    }

    @Test fun inactiveChapterStillReturnsToItsActiveSubject() {
        val container = container()
        database!!.openHelper.writableDatabase.execSQL("UPDATE chapters SET isActive = 0 WHERE id = 'chapter-bca-s1-dbms-01'")
        compose.setStudySmartContent(container, AppRoutes.chapter("chapter-bca-s1-dbms-01"))
        compose.awaitNodeWithText("Chapter unavailable")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.awaitNodeWithTag("chapters_screen")
    }

    @Test fun matchingQuestionIsHighlightedWithoutChangingStudyContext() {
        val container = container()
        compose.setContent {
            StudySmartTheme {
                val navController = rememberNavController()
                AppNavHost(navController, container)
                LaunchedEffect(Unit) {
                    navController.navigateChapterDetail("chapter-bca-s1-dbms-01",
                        questionId = "question-bca-s1-dbms-topic")
                }
            }
        }
        compose.awaitNodeWithTag("question_question-bca-s1-dbms-topic")
        compose.onNodeWithTag("question_question-bca-s1-dbms-topic").assertIsSelected().assertIsDisplayed()
        assertNull(runBlocking { container.preferences.preferences.first().selectedContext })
    }

    @Test fun openingDetailsIsReadOnlyAndAncestorDeactivationHidesContent() = runBlocking {
        val container = container()
        val repository = container.chapterDetailRepository
        assertEquals(2, repository.observeChapter("chapter-bca-s1-dbms-01").first()!!.questionCount)
        database!!.openHelper.writableDatabase.query("SELECT COUNT(*) FROM preparation_sessions").use {
            it.moveToFirst(); assertEquals(0, it.getInt(0))
        }
        database!!.openHelper.writableDatabase.execSQL("UPDATE courses SET isActive = 0 WHERE id = 'course-bca'")
        assertNull(repository.observeChapter("chapter-bca-s1-dbms-01").first())
    }

    @After fun close() { database?.close() }
}

private class ChapterDetailTestPreferences : PreferencesRepository {
    override val preferences = MutableStateFlow(StudyPreferences())
    override suspend fun selectCourse(courseId: String) {
        preferences.value = preferences.value.copy(selectedContext = null, selectedCourseIdOverride = courseId,
            selectedAcademicPeriodIdOverride = null)
    }
    override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) {
        preferences.value = preferences.value.copy(selectedContext = SelectedAcademicContext(courseId, academicPeriodId),
            selectedCourseIdOverride = null, selectedAcademicPeriodIdOverride = null)
    }
    override suspend fun clearSelectedCourse() = clearSelectedContext()
    override suspend fun clearSelectedPeriod() {
        preferences.value = preferences.value.copy(selectedContext = null,
            selectedCourseIdOverride = preferences.value.selectedCourseId, selectedAcademicPeriodIdOverride = null)
    }
    override suspend fun clearSelectedContext() { preferences.value = StudyPreferences() }
    override suspend fun clearSelectedCourseIfUnchanged(courseId: String, academicPeriodId: String?): Boolean {
        if (preferences.value.selectedCourseId != courseId || preferences.value.selectedAcademicPeriodId != academicPeriodId) return false
        clearSelectedCourse(); return true
    }
    override suspend fun clearSelectedPeriodIfUnchanged(courseId: String, academicPeriodId: String): Boolean {
        if (preferences.value.selectedCourseId != courseId || preferences.value.selectedAcademicPeriodId != academicPeriodId) return false
        clearSelectedPeriod(); return true
    }
    override suspend fun updatePreparationDefaults(defaults: com.studysmart.app.core.model.PreparationDefaults) {
        preferences.value = preferences.value.copy(preparationDefaults = defaults)
    }
}
