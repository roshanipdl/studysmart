package com.studysmart.app.feature.search

import androidx.lifecycle.SavedStateHandle
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private fun dispatchers(dispatcher: CoroutineDispatcher) = object : DispatcherProvider {
        override val main = dispatcher
        override val io = dispatcher
    }
    private class Repository : SearchRepository {
        val calls = mutableListOf<String>()
        val updates = MutableSharedFlow<SearchResults>(extraBufferCapacity = 4)
        var fail = false
        var cancelled = 0
        override fun observeResults(query: String) = flow {
            calls += query
            try {
                check(!fail)
                emit(SearchResults())
                emitAll(updates)
            } finally { cancelled++ }
        }
    }
    private val result = SearchResults(subjects = listOf(SearchResult.Subject("s", "Subject",
        SearchContext("c", "p", "s", "Course", "Period", "S", "Subject"))))

    @Test fun debounceMinimumTokensAndImmediateObsoleteClearing() = runTest {
        val repo = Repository()
        val vm = SearchViewModel(repo, dispatchers(StandardTestDispatcher(testScheduler)), SavedStateHandle())
        vm.setQuery("*"); runCurrent(); advanceTimeBy(500); runCurrent()
        assertEquals(SearchStatus.EMPTY_QUERY, vm.uiState.value.status)
        assertTrue(repo.calls.isEmpty())
        vm.setQuery("ab"); runCurrent(); advanceTimeBy(299); runCurrent()
        assertTrue(repo.calls.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf("ab"), repo.calls)
        repo.updates.emit(result); runCurrent()
        assertEquals(SearchStatus.RESULTS, vm.uiState.value.status)
        vm.setQuery("cd")
        assertEquals(SearchStatus.LOADING, vm.uiState.value.status)
        assertEquals(SearchResults(), vm.uiState.value.results)
        runCurrent(); assertEquals(1, repo.cancelled)
        vm.setQuery(""); runCurrent()
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf("ab"), repo.calls)
    }

    @Test fun savedQueryOnlyAndReactiveUpdates() = runTest {
        val saved = SavedStateHandle(mapOf(SearchViewModel.QUERY_KEY to "retained"))
        val repo = Repository()
        val vm = SearchViewModel(repo, dispatchers(StandardTestDispatcher(testScheduler)), saved)
        runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals(listOf("retained"), repo.calls)
        repo.updates.emit(result); runCurrent()
        assertEquals(result, vm.uiState.value.results)
        repo.updates.emit(SearchResults()); runCurrent()
        assertEquals(SearchStatus.NO_RESULTS, vm.uiState.value.status)
        vm.retry(); runCurrent(); assertEquals(1, repo.calls.size)
        vm.setQuery("edited")
        assertEquals("edited", saved.get<String>(SearchViewModel.QUERY_KEY))
        assertEquals(setOf(SearchViewModel.QUERY_KEY), saved.keys())
        vm.setQuery(""); runCurrent()
    }

    @Test fun retryOnlyCurrentFailureAndNoDuplicateRetry() = runTest {
        val repo = Repository().apply { fail = true }
        val vm = SearchViewModel(repo, dispatchers(StandardTestDispatcher(testScheduler)), SavedStateHandle())
        vm.retry(); vm.setQuery("fail"); runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals(SearchStatus.ERROR, vm.uiState.value.status)
        repo.fail = false
        vm.retry(); vm.retry(); runCurrent()
        assertEquals(listOf("fail", "fail"), repo.calls)
        assertEquals(SearchStatus.NO_RESULTS, vm.uiState.value.status)
        vm.setQuery("other"); vm.retry(); runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals(listOf("fail", "fail", "other"), repo.calls)
        vm.setQuery(""); runCurrent()
    }

    @Test fun obsoleteCancellationFailureCannotReplaceCurrentQuery() = runTest {
        val repo = object : SearchRepository {
            override fun observeResults(query: String): Flow<SearchResults> = flow {
                if (query == "old") {
                    try { awaitCancellation() } finally { throw IllegalStateException("obsolete") }
                } else emit(result)
            }
        }
        val vm = SearchViewModel(repo, dispatchers(StandardTestDispatcher(testScheduler)), SavedStateHandle())
        vm.setQuery("old"); runCurrent(); advanceTimeBy(300); runCurrent()
        vm.setQuery("new"); runCurrent()
        assertEquals(SearchStatus.LOADING, vm.uiState.value.status)
        advanceTimeBy(300); runCurrent()
        assertEquals("new", vm.uiState.value.query)
        assertEquals(SearchStatus.RESULTS, vm.uiState.value.status)
        vm.setQuery(""); runCurrent()
    }
}
