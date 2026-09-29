package com.studysmart.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.PreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val courses: List<HomeCourseUiModel> = emptyList(),
    val selectedCourseId: String? = null,
    val selectedAcademicPeriodId: String? = null,
    val recentStudy: String? = null,
    val error: String? = null,
)

class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: DispatcherProvider,
    private val curriculumRepository: CurriculumRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeHome()
    }

    fun retry() {
        observeHome()
    }

    private fun observeHome() {
        observeJob?.cancel()
        _uiState.value = HomeUiState()
        observeJob = viewModelScope.launch(dispatchers.main) {
            combine(
                homeRepository.observeCourses(),
                preferencesRepository.preferences,
            ) { courses, preferences ->
                HomeUiState(
                    isLoading = false,
                    courses = courses,
                    selectedCourseId = preferences.selectedCourseId,
                    selectedAcademicPeriodId = preferences.selectedAcademicPeriodId,
                )
            }
                .catch { error ->
                    _uiState.value = HomeUiState(
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

    fun selectCourse(courseId: String) {
        viewModelScope.launch(dispatchers.io) {
            preferencesRepository.selectCourse(courseId)
        }
    }

    private fun validateSelectedContext(state: HomeUiState) {
        val preferences = state.selectedCourseId ?: return
        if (state.courses.none { it.id == preferences }) {
            viewModelScope.launch(dispatchers.io) {
                preferencesRepository.clearSelectedCourse()
            }
            return
        }
        val periodId = state.selectedAcademicPeriodId ?: return
        val repository = curriculumRepository ?: return
        viewModelScope.launch(dispatchers.io) {
            if (!repository.isValidPeriod(preferences, periodId)) {
                preferencesRepository.clearSelectedPeriod()
            }
        }
    }
}
