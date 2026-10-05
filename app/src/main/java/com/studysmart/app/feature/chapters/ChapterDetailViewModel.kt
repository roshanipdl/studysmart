package com.studysmart.app.feature.chapters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.database.ChapterAcademicParent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChapterDetailUiState(
    val isLoading: Boolean = true,
    val content: ChapterDetailContent? = null,
    val hasError: Boolean = false,
    val highlightKey: String? = null,
    val parent: ChapterAcademicParent? = null,
)

class ChapterDetailViewModel(
    private val chapterId: String,
    private val highlight: ChapterHighlight,
    private val repository: ChapterDetailRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val state = MutableStateFlow(ChapterDetailUiState())
    val uiState = state.asStateFlow()
    private var observeJob: Job? = null

    init { retry() }

    fun retry() {
        observeJob?.cancel()
        state.value = ChapterDetailUiState()
        observeJob = viewModelScope.launch(dispatchers.main) {
            repository.observeChapter(chapterId).map { content ->
                ChapterDetailUiState(
                    isLoading = false, content = content, highlightKey = content?.highlightKey(highlight),
                    parent = withContext(dispatchers.io) { repository.getAcademicParent(chapterId) },
                )
            }.catch { error ->
                if (error is CancellationException) throw error
                state.value = ChapterDetailUiState(isLoading = false, hasError = true)
            }.collect { state.value = it }
        }
    }
}
