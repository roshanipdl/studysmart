package com.studysmart.app.feature.review

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.studysmart.app.core.database.ReviewScheduleEntity
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.model.LearningState
import com.studysmart.app.core.navigation.AppRoutes
import com.studysmart.app.core.ui.StudySmartTheme
import com.studysmart.app.testing.awaitNodeWithTag
import com.studysmart.app.testing.assertNoNodeWithTag
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import com.studysmart.app.testing.setStudySmartContent
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var database: StudySmartDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun loadingStateRendersBeforeReviewData() {
        composeRule.setContent {
            StudySmartTheme {
                ReviewScreen(
                    state = ReviewUiState(),
                    onRetry = {},
                    onStartDueReview = {},
                )
            }
        }

        composeRule.onNodeWithTag("review_loading").assertIsDisplayed()
    }

    @Test
    fun missingContextShowsContextRequiredState() {
        composeRule.setStudySmartContent(
            container = container(preferences = TestReviewPreferencesRepository()),
            startRoute = AppRoutes.REVIEW,
        )

        composeRule.awaitNodeWithTag("review_context_required")
        composeRule.onNodeWithTag("review_context_required").assertIsDisplayed()
    }

    @Test
    fun validEmptyPoolShowsNoDueWithoutRetry() {
        composeRule.setStudySmartContent(
            container = container(
                preferences = TestReviewPreferencesRepository(
                    StudyPreferences(SelectedAcademicContext("course-bca", "period-bca-semester-1")),
                ),
            ),
            startRoute = AppRoutes.REVIEW,
        )

        composeRule.awaitNodeWithTag("review_no_due")
        composeRule.onNodeWithTag("review_no_due").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Retry")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .size,
        )
    }

    @Test
    fun startDueReviewCreatesSessionBeforeOpeningActiveRoute() {
        val preferences = TestReviewPreferencesRepository(
            StudyPreferences(SelectedAcademicContext("course-bca", "period-bca-semester-1")),
        )
        val container = container(preferences)
        runBlocking {
            database!!.preparationDao().upsertReviewSchedule(
                ReviewScheduleEntity(
                    userId = "local-user",
                    questionId = "question-bca-s1-dbms-direct",
                    learningState = LearningState.LEARNING,
                    lastReviewedAt = Instant.EPOCH,
                    nextReviewAt = Instant.EPOCH,
                    intervalSeconds = 600,
                    repetitionCount = 1,
                    lapseCount = 0,
                ),
            )
        }

        composeRule.setStudySmartContent(container = container, startRoute = AppRoutes.REVIEW)
        composeRule.awaitNodeWithTag("review_due_count")
        composeRule.onNodeWithTag("review_start_due").performClick()

        composeRule.awaitNodeWithTag("preparation_session_screen")
        composeRule.onNodeWithText("Due review").assertIsDisplayed()
        composeRule.assertNoNodeWithTag("review_start_due")
    }

    private fun container(
        preferences: PreferencesRepository,
    ): AppContainer {
        database = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext())
        return AppContainer(
            context = ApplicationProvider.getApplicationContext(),
            database = database!!,
            preferences = preferences,
        )
    }
}

private class TestReviewPreferencesRepository(
    initial: StudyPreferences = StudyPreferences(),
) : PreferencesRepository {
    private val state = MutableStateFlow(initial)
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
