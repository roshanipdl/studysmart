package com.studysmart.app.feature.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.feature.home.HomeCourseUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class CoursesUiState(
    val isLoading: Boolean = true,
    val courses: List<HomeCourseUiModel> = emptyList(),
    val selectedCourseId: String? = null,
    val selectedAcademicPeriodId: String? = null,
    val error: String? = null,
)

class CoursesViewModel(
    private val curriculumRepository: CurriculumRepository,
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CoursesUiState())
    val uiState: StateFlow<CoursesUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observe()
    }

    fun retry() {
        _uiState.value = CoursesUiState()
        observe()
    }

    fun selectCourse(courseId: String) {
        viewModelScope.launch(dispatchers.io) {
            preferencesRepository.selectCourse(courseId)
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(dispatchers.main) {
            combine(
                curriculumRepository.observeCourses(),
                preferencesRepository.preferences,
            ) { courses, preferences ->
                CoursesUiState(
                    isLoading = false,
                    courses = courses.map { course ->
                        HomeCourseUiModel(
                            id = course.id,
                            code = course.code,
                            shortName = course.shortName,
                            name = course.name,
                            periodCount = course.periodCount,
                            structureType = course.structureType,
                        )
                    },
                    selectedCourseId = preferences.selectedCourseId,
                    selectedAcademicPeriodId = preferences.selectedAcademicPeriodId,
                )
            }
                .catch { error ->
                    _uiState.value = CoursesUiState(
                        isLoading = false,
                        error = error.message ?: "Unable to load courses.",
                    )
                }
                .collect { state ->
                    _uiState.value = state
                    validateSelectedContext(state)
                }
        }
    }

    private fun validateSelectedContext(state: CoursesUiState) {
        val courseId = state.selectedCourseId ?: return
        if (state.courses.none { it.id == courseId }) {
            viewModelScope.launch(dispatchers.io) {
                preferencesRepository.clearSelectedCourse()
            }
            return
        }
        val periodId = state.selectedAcademicPeriodId ?: return
        viewModelScope.launch(dispatchers.io) {
            if (!curriculumRepository.isValidPeriod(courseId, periodId)) {
                preferencesRepository.clearSelectedPeriod()
            }
        }
    }
}
