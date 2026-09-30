package com.studysmart.app.feature.academic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.PreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AcademicPeriodViewModel(
    private val courseId: String,
    private val curriculumRepository: CurriculumRepository,
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AcademicPeriodUiState())
    val uiState: StateFlow<AcademicPeriodUiState> = _uiState.asStateFlow()

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

    fun selectPeriod(periodId: String, onSelected: () -> Unit) {
        if (_uiState.value.isSelecting || _uiState.value.selectionError != null) return
        pendingSelection = periodId to onSelected
        persistSelection(periodId, onSelected)
    }

    fun retrySelection() {
        if (_uiState.value.isSelecting) return
        pendingSelection?.let { (periodId, onSelected) -> persistSelection(periodId, onSelected) }
    }

    private fun persistSelection(periodId: String, onSelected: () -> Unit) {
        _uiState.value = _uiState.value.copy(isSelecting = true, selectionError = null)
        viewModelScope.launch(dispatchers.main) {
            val persisted = try {
                withContext(dispatchers.io) {
                    if (!curriculumRepository.isValidPeriod(courseId, periodId)) {
                        false
                    } else {
                        preferencesRepository.setSelectedContext(courseId, periodId)
                        true
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(selectionError = "Unable to save your academic period. Retry.")
                return@launch
            } finally {
                _uiState.value = _uiState.value.copy(isSelecting = false)
            }
            pendingSelection = null
            if (!persisted) {
                _uiState.value = _uiState.value.copy(
                    error = null,
                    isMissingContent = true,
                )
                return@launch
            }
            onSelected()
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(dispatchers.main) {
            curriculumRepository.observeCoursePeriods(courseId)
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Unable to load academic periods.",
                    )
                }
                .collect { content ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        content = content,
                        isMissingContent = content == null,
                        error = null,
                    )
                }
        }
    }
}
