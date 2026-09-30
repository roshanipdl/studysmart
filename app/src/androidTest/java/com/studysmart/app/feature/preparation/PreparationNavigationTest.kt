package com.studysmart.app.feature.preparation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.studysmart.app.app.AppContainer
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.data.SelectedAcademicContext
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.testing.awaitNodeWithTag
import com.studysmart.app.testing.assertNoNodeWithTag
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
class PreparationNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var database: com.studysmart.app.core.database.StudySmartDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun chapterPreparationRevealsRatesAndOpensSummary() {
        composeRule.setStudySmartContent(container())

        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performClick()
        composeRule.awaitNodeWithTag("period_period-bca-semester-1")
        composeRule.onNodeWithTag("period_period-bca-semester-1").performClick()
        composeRule.awaitNodeWithTag("subject_subject-bca-s1-dbms")
        composeRule.onNodeWithTag("subject_subject-bca-s1-dbms").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
        composeRule.onAllNodesWithText("Prepare").get(0).performClick()

        composeRule.awaitNodeWithTag("preparation_session_screen")
        composeRule.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        composeRule.assertNoNodeWithTag("preparation_ratings")
        composeRule.onNodeWithTag("preparation_reveal").performClick()
        composeRule.onNodeWithTag("preparation_card_back").assertIsDisplayed()
        composeRule.awaitNodeWithTag("preparation_answer")
        composeRule.onNodeWithTag("preparation_ratings").assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_rating_good").performClick()
        composeRule.awaitNodeWithTag("preparation_reveal")
        composeRule.onNodeWithTag("preparation_reveal").performClick()
        composeRule.awaitNodeWithTag("preparation_answer")
        composeRule.onNodeWithTag("preparation_rating_easy").performClick()

        composeRule.awaitNodeWithTag("preparation_summary_screen")
        composeRule.onNodeWithText("Your review history has been saved.").assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_readiness_slot").assertIsDisplayed()
        composeRule.onNodeWithText("Exam readiness estimate").assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_recently_reviewed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_summary_item_question-bca-s1-dbms-direct")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_summary_item_question-bca-s1-dbms-topic")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_summary_back").performScrollTo().performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
    }

    @Test
    fun bottomNavigationFromActivePreparationRequiresDiscard() {
        composeRule.setStudySmartContent(container())

        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performClick()
        composeRule.awaitNodeWithTag("period_period-bca-semester-1")
        composeRule.onNodeWithTag("period_period-bca-semester-1").performClick()
        composeRule.awaitNodeWithTag("subject_subject-bca-s1-dbms")
        composeRule.onNodeWithTag("subject_subject-bca-s1-dbms").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
        composeRule.onAllNodesWithText("Prepare").get(0).performClick()
        composeRule.awaitNodeWithTag("preparation_session_screen")

        composeRule.onNodeWithTag("bottom_navigation_review").performClick()
        composeRule.onNodeWithTag("preparation_cancel_discard").assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_discard").performClick()
        composeRule.awaitNodeWithTag("review_screen")
        composeRule.onNodeWithTag("review_screen").assertIsDisplayed()
    }

    @Test
    fun closingPreparationRequiresDiscardConfirmation() {
        composeRule.setStudySmartContent(container())

        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performClick()
        composeRule.awaitNodeWithTag("period_period-bca-semester-1")
        composeRule.onNodeWithTag("period_period-bca-semester-1").performClick()
        composeRule.awaitNodeWithTag("subject_subject-bca-s1-dbms")
        composeRule.onNodeWithTag("subject_subject-bca-s1-dbms").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
        composeRule.onAllNodesWithText("Prepare").get(0).performClick()
        composeRule.awaitNodeWithTag("preparation_session_screen")

        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.onNodeWithTag("preparation_cancel_discard").assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_cancel_discard").performClick()
        composeRule.onNodeWithTag("preparation_session_screen").assertIsDisplayed()
    }

    @Test
    fun discardingPreparationReturnsToTheOriginatingSubject() {
        composeRule.setStudySmartContent(container())

        composeRule.awaitNodeWithTag("course_course-bca")
        composeRule.onNodeWithTag("course_course-bca").performClick()
        composeRule.awaitNodeWithTag("period_period-bca-semester-1")
        composeRule.onNodeWithTag("period_period-bca-semester-1").performClick()
        composeRule.awaitNodeWithTag("subject_subject-bca-s1-dbms")
        composeRule.onNodeWithTag("subject_subject-bca-s1-dbms").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
        composeRule.onAllNodesWithText("Prepare").get(0).performClick()
        composeRule.awaitNodeWithTag("preparation_session_screen")

        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.onNodeWithTag("preparation_discard").performClick()
        composeRule.awaitNodeWithTag("chapters_screen")
    }

    private fun container(): AppContainer {
        database = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext())
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
