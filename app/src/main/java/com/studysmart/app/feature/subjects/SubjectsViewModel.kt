package com.studysmart.app.feature.subjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.feature.academic.SubjectsUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class SubjectsViewModel(
    private val academicPeriodId: String,
    private val curriculumRepository: CurriculumRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SubjectsUiState())
    val uiState: StateFlow<SubjectsUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observe()
    }

    fun retry() {
        _uiState.value = SubjectsUiState()
        observe()
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(dispatchers.main) {
            curriculumRepository.observePeriodSubjects(academicPeriodId)
                .catch { error ->
                    _uiState.value = SubjectsUiState(
                        isLoading = false,
                        error = error.message ?: "Unable to load subjects.",
                    )
                }
                .collect { content ->
                    _uiState.value = SubjectsUiState(
                        isLoading = false,
                        content = content,
                        isMissingContent = content == null,
                    )
                }
        }
    }
}
