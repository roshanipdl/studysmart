package com.studysmart.app.feature.settings

import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.data.SelectedAcademicContext
import com.studysmart.app.core.model.AcademicPeriodType
import com.studysmart.app.core.model.AcademicPeriodBrowseModel
import com.studysmart.app.core.model.CourseBrowseModel
import com.studysmart.app.core.model.CoursePeriodsBrowseModel
import com.studysmart.app.core.model.PeriodSubjectsBrowseModel
import com.studysmart.app.core.model.SubjectChaptersBrowseModel
import com.studysmart.app.core.model.EligibleQuestionModel
import com.studysmart.app.testing.TestDispatcherProvider
import com.studysmart.app.testing.TestPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsContextTest {
    @Test fun resolvesYearlyContextAndClearsStalePeriodWithoutChangingCourseOrDefaults() = runTest {
        val course = CourseBrowseModel("yearly", "YP", "Yearly programme", "YP", AcademicPeriodType.YEAR, 2)
        val period = AcademicPeriodBrowseModel("year2", course.id, 2, AcademicPeriodType.YEAR, "Year 2", "Y2", 0)
        val courses = MutableStateFlow(listOf(course))
        val periods = MutableStateFlow<CoursePeriodsBrowseModel?>(CoursePeriodsBrowseModel(course, listOf(period)))
        val curriculum = object : CurriculumRepository {
            override fun observeCourses() = courses
            override fun observeCoursePeriods(courseId: String) = periods
            override fun observePeriodSubjects(academicPeriodId: String) = flowOf<PeriodSubjectsBrowseModel?>(null)
            override fun observeSubjectChapters(subjectId: String) = flowOf<SubjectChaptersBrowseModel?>(null)
            override suspend fun getEligibleQuestionsForChapter(chapterId: String) = emptyList<EligibleQuestionModel>()
            override suspend fun getEligibleQuestionsForTopic(topicId: String) = emptyList<EligibleQuestionModel>()
            override suspend fun isValidPeriod(courseId: String, academicPeriodId: String) = false
        }
        val preferences = TestPreferencesRepository(StudyPreferences(selectedContext = SelectedAcademicContext(course.id, period.id)))
        val vm = SettingsViewModel(preferences, TestDispatcherProvider(), curriculum)
        assertEquals("Yearly programme", vm.uiState.value.courseName)
        assertEquals("Year 2", vm.uiState.value.periodName)
        periods.value = CoursePeriodsBrowseModel(course, emptyList())
        assertNull(preferences.preferences.first().selectedAcademicPeriodId)
        assertEquals(course.id, preferences.preferences.first().selectedCourseId)
        assertNull(vm.uiState.value.periodName)
        assertEquals(10, preferences.preferences.first().testDefaults.questionCount)
        courses.value = emptyList()
        assertNull(preferences.preferences.first().selectedCourseId)
        assertNull(vm.uiState.value.courseId)
    }
}
