package com.studysmart.app.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.data.SearchQuery
import com.studysmart.app.core.data.SearchRepository
import com.studysmart.app.core.data.SearchResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

enum class SearchStatus { EMPTY_QUERY, LOADING, RESULTS, NO_RESULTS, ERROR }
data class SearchUiState(
    val query: String = "",
    val status: SearchStatus = SearchStatus.EMPTY_QUERY,
    val results: SearchResults = SearchResults(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: SearchRepository,
    dispatchers: DispatcherProvider,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private data class Request(val query: String, val generation: Long, val retry: Boolean = false)
    private var generation = 0L
    private val state = MutableStateFlow(initial(savedState[QUERY_KEY] ?: ""))
    val uiState = state.asStateFlow()
    private val requests = MutableStateFlow(Request(state.value.query, generation))

    init {
        viewModelScope.launch(dispatchers.main) {
            requests.flatMapLatest { request ->
                flow {
                    if (SearchQuery.parse(request.query) == null) return@flow
                    if (!request.retry) delay(300)
                    try {
                        repository.observeResults(request.query).collect { results ->
                            val empty = results.subjects.isEmpty() && results.chapters.isEmpty() &&
                                results.topics.isEmpty() && results.questions.isEmpty()
                            emit(request.generation to SearchUiState(request.query,
                                if (empty) SearchStatus.NO_RESULTS else SearchStatus.RESULTS, results))
                        }
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) {
                        emit(request.generation to SearchUiState(request.query, SearchStatus.ERROR))
                    }
                }
            }.collect { (requestGeneration, next) ->
                if (requestGeneration == generation) state.value = next
            }
        }
    }

    fun setQuery(query: String) {
        if (query == state.value.query) return
        savedState[QUERY_KEY] = query
        generation++
        state.value = initial(query) // Never show results/errors belonging to an obsolete query.
        requests.value = Request(query, generation)
    }

    fun retry() {
        if (state.value.status != SearchStatus.ERROR) return
        generation++
        state.value = initial(state.value.query)
        requests.value = Request(state.value.query, generation, retry = true)
    }

    private fun initial(query: String) = SearchUiState(query,
        if (SearchQuery.parse(query) == null) SearchStatus.EMPTY_QUERY else SearchStatus.LOADING)

    companion object { const val QUERY_KEY = "search_query" }
}
