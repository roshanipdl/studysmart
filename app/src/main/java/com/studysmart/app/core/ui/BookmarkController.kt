package com.studysmart.app.core.ui

import com.studysmart.app.core.data.BookmarkRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class BookmarkUiState(
    val isLoading: Boolean = true,
    val savedIds: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val hasLoadError: Boolean = false,
    val hasWriteError: Boolean = false,
    val isMissingQuestion: Boolean = false,
    val unavailableIds: Set<String> = emptySet(),
) {
    val canChange: Boolean get() = !isLoading && !isSaving && !hasLoadError && !hasWriteError
}

/** Shared ViewModel helper; publishes into the owning screen's single UI state. */
class BookmarkController(
    private val repository: BookmarkRepository,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val onState: (BookmarkUiState) -> Unit,
) {
    var state = BookmarkUiState()
        private set
    private var observer: Job? = null
    private var pending: Pair<String, Boolean>? = null

    init { observe() }

    fun toggle(questionId: String) {
        if (!state.canChange || pending != null || questionId in state.unavailableIds) return
        pending = questionId to (questionId !in state.savedIds)
        submit()
    }

    fun retry() {
        if (state.isSaving) return
        if (pending != null) submit() else observe()
    }

    private fun publish(value: BookmarkUiState) {
        state = value
        onState(value)
    }

    private fun observe() {
        observer?.cancel()
        publish(state.copy(isLoading = true, hasLoadError = false))
        observer = scope.launch(dispatcher) {
            repository.observeBookmarkedIds().catch { error ->
                if (error is CancellationException) throw error
                publish(state.copy(isLoading = false, hasLoadError = true))
            }.collect { ids -> publish(state.copy(isLoading = false, savedIds = ids, hasLoadError = false)) }
        }
    }

    private fun submit() {
        val (questionId, bookmarked) = pending ?: return
        publish(state.copy(isSaving = true, hasWriteError = false, isMissingQuestion = false))
        scope.launch(dispatcher) {
            try {
                val written = repository.setBookmarked(questionId, bookmarked)
                if (written) {
                    pending = null
                    publish(state.copy(isSaving = false, savedIds = if (bookmarked)
                        state.savedIds + questionId else state.savedIds - questionId))
                } else {
                    pending = null
                    publish(state.copy(isSaving = false, isMissingQuestion = true, unavailableIds = state.unavailableIds + questionId))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                publish(state.copy(isSaving = false, hasWriteError = true))
            }
        }
    }
}
