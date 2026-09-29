package com.studysmart.app.feature.academic

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
import kotlinx.coroutines.launch

class AcademicPeriodViewModel(
    private val courseId: String,
    private val curriculumRepository: CurriculumRepository,
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AcademicPeriodUiState())
    val uiState: StateFlow<AcademicPeriodUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observe()
    }

    fun retry() {
        _uiState.value = AcademicPeriodUiState()
        observe()
    }

    fun selectPeriod(periodId: String, onSelected: () -> Unit) {
        viewModelScope.launch(dispatchers.io) {
            if (!curriculumRepository.isValidPeriod(courseId, periodId)) {
                _uiState.value = _uiState.value.copy(
                    error = null,
                    isMissingContent = true,
                )
                return@launch
            }
            preferencesRepository.setSelectedContext(courseId, periodId)
            kotlinx.coroutines.withContext(dispatchers.main) { onSelected() }
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(dispatchers.main) {
            curriculumRepository.observeCoursePeriods(courseId)
                .catch { error ->
                    _uiState.value = AcademicPeriodUiState(
                        isLoading = false,
                        error = error.message ?: "Unable to load academic periods.",
                    )
                }
                .collect { content ->
                    _uiState.value = AcademicPeriodUiState(
                        isLoading = false,
                        content = content,
                        isMissingContent = content == null,
                    )
                }
        }
    }
}
