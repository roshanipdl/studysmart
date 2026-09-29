package com.studysmart.app.feature.academic

import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.model.AcademicPeriodBrowseModel
import com.studysmart.app.core.model.AcademicPeriodType
import com.studysmart.app.core.model.ChapterBrowseModel
import com.studysmart.app.core.model.CourseBrowseModel
import com.studysmart.app.core.model.CoursePeriodsBrowseModel
import com.studysmart.app.core.model.EligibleQuestionModel
import com.studysmart.app.core.model.PeriodSubjectsBrowseModel
import com.studysmart.app.core.model.SubjectBrowseModel
import com.studysmart.app.core.model.SubjectChaptersBrowseModel
import com.studysmart.app.feature.chapters.ChaptersViewModel
import com.studysmart.app.feature.courses.CoursesViewModel
import com.studysmart.app.feature.subjects.SubjectsViewModel
import com.studysmart.app.testing.TestDispatcherProvider
import com.studysmart.app.testing.TestPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseViewModelTest {
    private val course = CourseBrowseModel(
        id = "course-1",
        code = "BCA",
        name = "Course",
        shortName = "BCA",
        structureType = AcademicPeriodType.SEMESTER,
        periodCount = 1,
    )
    private val period = AcademicPeriodBrowseModel(
        id = "period-1",
        courseId = course.id,
        periodNumber = 1,
        periodType = AcademicPeriodType.SEMESTER,
        name = "Semester 1",
        shortName = "Sem 1",
        subjectCount = 1,
    )
    private val subject = SubjectBrowseModel("subject-1", period.id, "DBMS", "Database", 1)

    @Test
    fun courseAndChildViewModelsExposeSuccessAndEmptyStates() = runTest {
        val repository = FakeBrowseRepository(
            courses = listOf(course),
            coursePeriods = CoursePeriodsBrowseModel(course, listOf(period)),
            periodSubjects = PeriodSubjectsBrowseModel(course, period, listOf(subject)),
            subjectChapters = SubjectChaptersBrowseModel(
                course,
                period,
                subject,
                chapters = emptyList(),
            ),
        )
        val preferences: PreferencesRepository = TestPreferencesRepository()
        val dispatchers = TestDispatcherProvider()

        val courses = CoursesViewModel(repository, preferences, dispatchers)
        val academic = AcademicPeriodViewModel("course-1", repository, preferences, dispatchers)
        val subjects = SubjectsViewModel("period-1", repository, dispatchers)
        val chapters = ChaptersViewModel("subject-1", repository, dispatchers)

        assertFalse(courses.uiState.value.isLoading)
        assertEquals(1, courses.uiState.value.courses.size)
        assertEquals(1, academic.uiState.value.content?.periods?.size)
        assertEquals(1, subjects.uiState.value.content?.subjects?.size)
        assertFalse(chapters.uiState.value.isLoading)
        assertTrue(chapters.uiState.value.content?.chapters?.isEmpty() == true)
    }

    @Test
    fun missingRouteContentIsReportedWithoutReplacement() = runTest {
        val repository = FakeBrowseRepository()
        val viewModel = AcademicPeriodViewModel(
            courseId = "deleted-course",
            curriculumRepository = repository,
            preferencesRepository = TestPreferencesRepository(),
            dispatchers = TestDispatcherProvider(),
        )

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isMissingContent)
        assertEquals(null, viewModel.uiState.value.content)
    }

    @Test
    fun invalidPeriodSelectionDoesNotPersistOrNavigate() = runTest {
        val preferences = TestPreferencesRepository()
        val viewModel = AcademicPeriodViewModel(
            courseId = "course-1",
            curriculumRepository = FakeBrowseRepository(),
            preferencesRepository = preferences,
            dispatchers = TestDispatcherProvider(),
        )
        var navigated = false

        viewModel.selectPeriod("deleted-period") { navigated = true }

        assertFalse(navigated)
        assertTrue(viewModel.uiState.value.isMissingContent)
        assertEquals(null, preferences.preferences.first().selectedAcademicPeriodId)
    }
}

private class FakeBrowseRepository(
    private val courses: List<CourseBrowseModel> = emptyList(),
    private val coursePeriods: CoursePeriodsBrowseModel? = null,
    private val periodSubjects: PeriodSubjectsBrowseModel? = null,
    private val subjectChapters: SubjectChaptersBrowseModel? = null,
) : CurriculumRepository {
    override fun observeCourses(): Flow<List<CourseBrowseModel>> = flowOf(courses)

    override fun observeCoursePeriods(courseId: String): Flow<CoursePeriodsBrowseModel?> = flowOf(
        coursePeriods?.takeIf { it.course.id == courseId },
    )

    override fun observePeriodSubjects(academicPeriodId: String): Flow<PeriodSubjectsBrowseModel?> = flowOf(
        periodSubjects?.takeIf { it.period.id == academicPeriodId },
    )

    override fun observeSubjectChapters(subjectId: String): Flow<SubjectChaptersBrowseModel?> = flowOf(
        subjectChapters?.takeIf { it.subject.id == subjectId },
    )

    override suspend fun getEligibleQuestionsForChapter(chapterId: String): List<EligibleQuestionModel> =
        emptyList()

    override suspend fun getEligibleQuestionsForTopic(topicId: String): List<EligibleQuestionModel> =
        emptyList()

    override suspend fun isValidPeriod(courseId: String, academicPeriodId: String): Boolean =
        coursePeriods?.course?.id == courseId && coursePeriods.periods.any { it.id == academicPeriodId }
}
