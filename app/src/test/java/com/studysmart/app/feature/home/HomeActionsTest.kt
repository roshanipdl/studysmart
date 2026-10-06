package com.studysmart.app.feature.home

import com.studysmart.app.core.data.BookmarkRepository
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.SelectedAcademicContext
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.database.SavedQuestionRow
import com.studysmart.app.core.model.*
import com.studysmart.app.feature.review.DueReviewSummary
import com.studysmart.app.feature.review.ReviewRepository
import com.studysmart.app.testing.TestDispatcherProvider
import com.studysmart.app.testing.TestHomeRepository
import com.studysmart.app.testing.TestPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeActionsTest {
    @Test
    fun dueAvailabilityTracksSelectedContextAndCurriculumRemoval() = runTest {
        val course = CourseBrowseModel("c", "C", "Course", "C", AcademicPeriodType.YEAR, 1)
        val period = AcademicPeriodBrowseModel("p", "c", 1, AcademicPeriodType.YEAR, "Year 1", "Y1", 0)
        val periods = MutableStateFlow<CoursePeriodsBrowseModel?>(CoursePeriodsBrowseModel(course, listOf(period)))
        val curriculum = object : CurriculumRepository {
            override fun observeCourses() = flowOf(listOf(course))
            override fun observeCoursePeriods(courseId: String) = periods
            override fun observePeriodSubjects(academicPeriodId: String) = flowOf<PeriodSubjectsBrowseModel?>(null)
            override fun observeSubjectChapters(subjectId: String) = flowOf<SubjectChaptersBrowseModel?>(null)
            override suspend fun getEligibleQuestionsForChapter(chapterId: String) = emptyList<EligibleQuestionModel>()
            override suspend fun getEligibleQuestionsForTopic(topicId: String) = emptyList<EligibleQuestionModel>()
            override suspend fun isValidPeriod(courseId: String, academicPeriodId: String) = periods.value?.periods?.any { it.id == academicPeriodId } == true
        }
        val due = MutableStateFlow(DueReviewSummary(3, 1))
        val reviews = object : ReviewRepository {
            override fun observeDueReview(courseId: String, academicPeriodId: String) = due.also {
                assertEquals("c", courseId)
                assertEquals("p", academicPeriodId)
            }
        }
        var savedScope: SavedQuestionScope? = null
        val saved = MutableStateFlow(listOf(SavedQuestionRow("q", "Question", "c", "C", "p", "Year 1", "s", "S", "Subject", "ch", "Chapter", null, java.time.Instant.EPOCH)))
        val bookmarks = object : BookmarkRepository {
            override fun observeBookmarkedIds() = flowOf(emptySet<String>())
            override fun observeSavedQuestions(scope: SavedQuestionScope) = saved.also { savedScope = scope }
            override suspend fun setBookmarked(questionId: String, bookmarked: Boolean) = false
        }
        val preferences = TestPreferencesRepository(StudyPreferences(selectedContext = SelectedAcademicContext("c", "p")))
        val vm = HomeViewModel(TestHomeRepository(listOf(HomeCourseUiModel("c", "C", "C", "Course", 1))), preferences,
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)), curriculum, reviewRepository = reviews, bookmarkRepository = bookmarks)
        runCurrent()
        assertEquals(3, vm.uiState.value.dueCount)
        assertEquals(SavedQuestionScope("c", "p"), savedScope)
        assertEquals(1, vm.uiState.value.savedCount)
        saved.value = emptyList()
        runCurrent()
        assertEquals(0, vm.uiState.value.savedCount)
        due.value = DueReviewSummary(0, 0)
        runCurrent()
        assertEquals(0, vm.uiState.value.dueCount)
        due.value = DueReviewSummary(4, 0)
        runCurrent()
        assertEquals(4, vm.uiState.value.dueCount)
        periods.value = CoursePeriodsBrowseModel(course, emptyList())
        runCurrent()
        assertEquals(0, vm.uiState.value.dueCount)
        due.value = DueReviewSummary(9, 0)
        runCurrent()
        assertEquals(0, vm.uiState.value.dueCount)
        preferences.selectCourse("other")
        runCurrent()
        assertEquals(0, vm.uiState.value.dueCount)
    }
}
