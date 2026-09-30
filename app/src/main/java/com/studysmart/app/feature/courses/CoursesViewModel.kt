package com.studysmart.app.feature.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.feature.home.HomeCourseUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CoursesUiState(
    val isLoading: Boolean = true,
    val courses: List<HomeCourseUiModel> = emptyList(),
    val selectedCourseId: String? = null,
    val selectedAcademicPeriodId: String? = null,
    val error: String? = null,
    val isSelecting: Boolean = false,
    val selectionError: String? = null,
)

class CoursesViewModel(
    private val curriculumRepository: CurriculumRepository,
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CoursesUiState())
    val uiState: StateFlow<CoursesUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var pendingSelection: Pair<String, () -> Unit>? = null

    init {
        observe()
    }

    fun retry() {
        if (_uiState.value.isSelecting) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        observe()
    }

    fun retrySelection() {
        if (_uiState.value.isSelecting) return
        pendingSelection?.let { (courseId, onSelected) -> persistSelection(courseId, onSelected) }
    }

    fun selectCourse(courseId: String, onSelected: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isLoading || state.isSelecting || state.selectionError != null ||
            state.courses.none { it.id == courseId }
        ) return
        pendingSelection = courseId to onSelected
        persistSelection(courseId, onSelected)
    }

    private fun persistSelection(courseId: String, onSelected: () -> Unit) {
        _uiState.value = _uiState.value.copy(isSelecting = true, selectionError = null)
        viewModelScope.launch(dispatchers.main) {
            try {
                withContext(dispatchers.io) { preferencesRepository.selectCourse(courseId) }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(selectionError = "Unable to save your course. Retry.")
                return@launch
            } finally {
                _uiState.value = _uiState.value.copy(isSelecting = false)
            }
            pendingSelection = null
            onSelected()
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
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Unable to load courses.",
                    )
                }
                .collectLatest { state ->
                    _uiState.value = state.copy(
                        isSelecting = _uiState.value.isSelecting,
                        selectionError = _uiState.value.selectionError,
                    )
                    try {
                        withContext(dispatchers.io) { validateSelectedContext(state) }
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        _uiState.value = _uiState.value.copy(error = "Unable to verify your study context. Retry.")
                    }
                }
        }
    }

    private suspend fun validateSelectedContext(state: CoursesUiState) {
        val courseId = state.selectedCourseId ?: return
        if (state.courses.none { it.id == courseId }) {
            preferencesRepository.clearSelectedCourseIfUnchanged(courseId, state.selectedAcademicPeriodId)
            return
        }
        val periodId = state.selectedAcademicPeriodId ?: return
        if (!curriculumRepository.isValidPeriod(courseId, periodId)) {
            preferencesRepository.clearSelectedPeriodIfUnchanged(courseId, periodId)
        }
    }
}
