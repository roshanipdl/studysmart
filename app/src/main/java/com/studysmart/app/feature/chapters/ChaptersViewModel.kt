package com.studysmart.app.feature.chapters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.feature.academic.ChaptersUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ChaptersViewModel(
    private val subjectId: String,
    private val curriculumRepository: CurriculumRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChaptersUiState())
    val uiState: StateFlow<ChaptersUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observe()
    }

    fun retry() {
        _uiState.value = ChaptersUiState()
        observe()
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(dispatchers.main) {
            curriculumRepository.observeSubjectChapters(subjectId)
                .catch { error ->
                    _uiState.value = ChaptersUiState(
                        isLoading = false,
                        error = error.message ?: "Unable to load chapters.",
                    )
                }
                .collect { content ->
                    _uiState.value = ChaptersUiState(
                        isLoading = false,
                        content = content,
                        isMissingContent = content == null,
                    )
                }
        }
    }
}
