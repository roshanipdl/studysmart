package com.studysmart.app.feature.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.studysmart.app.R
import com.studysmart.app.core.data.SearchResult
import com.studysmart.app.core.ui.EmptyState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.StudyTopBar

@Composable
fun SearchScreen(state: SearchUiState, onQuery: (String) -> Unit, onRetry: () -> Unit,
    onResult: (SearchResult) -> Unit, onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (state.status == SearchStatus.EMPTY_QUERY) focus.requestFocus() }
    Column(Modifier.fillMaxSize().imePadding()) {
        StudyTopBar(stringResource(R.string.search_title), onBack = onBack,
            navigationContentDescription = stringResource(R.string.search_back))
        OutlinedTextField(value = state.query, onValueChange = onQuery, singleLine = true,
            label = { Text(stringResource(R.string.search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).focusRequester(focus).testTag("search_input"))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state.status) {
                SearchStatus.EMPTY_QUERY -> item { EmptyState(title = stringResource(R.string.search_title),
                    message = stringResource(R.string.search_empty)) }
                SearchStatus.LOADING -> item { LoadingState(message = stringResource(R.string.search_loading)) }
                SearchStatus.NO_RESULTS -> item { EmptyState(title = stringResource(R.string.search_no_results),
                    message = stringResource(R.string.search_no_results_message),
                    retryLabel = stringResource(R.string.search_edit), onRetry = { focus.requestFocus() }) }
                SearchStatus.ERROR -> item { EmptyState(title = stringResource(R.string.search_error),
                    message = stringResource(R.string.search_error_message),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    retryLabel = stringResource(R.string.search_retry), onRetry = onRetry) }
                SearchStatus.RESULTS -> {
                    val groups = listOf(R.string.search_subjects to state.results.subjects,
                        R.string.search_chapters to state.results.chapters, R.string.search_topics to state.results.topics,
                        R.string.search_questions to state.results.questions)
                    groups.forEach { (heading, rows) ->
                        if (rows.isNotEmpty()) {
                            item(key = heading) { Text(stringResource(heading), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() }) }
                            items(rows, key = { "${heading}/${it.id}" }) { result ->
                                SearchResultCard(result, onClick = { onResult(result) }, modifier = Modifier.testTag("search_result_${result.id}"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = result.context
    val labels = buildList {
        add(context.courseName)
        add(context.academicPeriodName)
        if (result !is SearchResult.Subject) add(context.subjectName)
        when (result) {
            is SearchResult.Topic -> add(result.chapterTitle)
            is SearchResult.Question -> add(result.chapterTitle)
            else -> Unit
        }
    }
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(result.title, style = MaterialTheme.typography.titleMedium)
            Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
