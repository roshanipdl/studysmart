package com.studysmart.app.testing

import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.data.SelectedAcademicContext
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.model.TestDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TestPreferencesRepository(
    initial: StudyPreferences = StudyPreferences(),
) : PreferencesRepository {
    private val state = MutableStateFlow(initial)
    override val preferences: Flow<StudyPreferences> = state.asStateFlow()

    override suspend fun selectCourse(courseId: String) {
        state.value = StudyPreferences(
            selectedCourseIdOverride = courseId,
            testDefaults = state.value.testDefaults,
        )
    }

    override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) {
        state.value = state.value.copy(
            selectedContext = SelectedAcademicContext(courseId, academicPeriodId),
            selectedCourseIdOverride = null,
            selectedAcademicPeriodIdOverride = null,
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
        val courseId = state.value.selectedCourseId
        state.value = if (courseId == null) {
            state.value.copy(
                selectedContext = null,
                selectedCourseIdOverride = null,
                selectedAcademicPeriodIdOverride = null,
            )
        } else {
            state.value.copy(
                selectedContext = null,
                selectedCourseIdOverride = courseId,
                selectedAcademicPeriodIdOverride = null,
            )
        }
    }

    override suspend fun clearSelectedContext() {
        clearSelectedCourse()
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

    override suspend fun updatePreparationDefaults(defaults: com.studysmart.app.core.model.PreparationDefaults) {
        state.value = state.value.copy(preparationDefaults = defaults)
    }

    override suspend fun updateTestDefaults(defaults: TestDefaults) {
        state.value = state.value.copy(testDefaults = defaults)
    }
}
