package com.studysmart.app.feature.important

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.studysmart.app.R
import com.studysmart.app.core.database.SavedQuestionRow
import com.studysmart.app.core.ui.EmptyState
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.PrimaryButton
import com.studysmart.app.core.ui.SecondaryButton
import com.studysmart.app.core.ui.StudyTopBar
import com.studysmart.app.core.ui.StudyTopBarVariant

@Composable
fun ImportantScreen(
    state: ImportantUiState,
    onChooseContext: () -> Unit,
    onSubject: (String?) -> Unit,
    onChapter: (String?) -> Unit,
    onRefresh: () -> Unit,
    onRemove: (String) -> Unit,
    onRetryRemove: () -> Unit,
    onOpenQuestion: (SavedQuestionRow) -> Unit,
    onPrepare: () -> Unit,
    onTest: () -> Unit,
    onRetryLaunch: () -> Unit,
    onOpenDefaults: () -> Unit,
) {
    Scaffold(topBar = {
        StudyTopBar(stringResource(R.string.important_title), variant = StudyTopBarVariant.BACK_SUBTITLE,
            subtitle = state.context?.let { "${it.course.code} · ${it.period.name}" })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("important_screen"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.isLoading -> item { LoadingState() }
                state.hasError -> item { ErrorState(stringResource(R.string.important_error), onRetry = onRefresh) }
                state.context == null -> item {
                    EmptyState(stringResource(R.string.important_context_title), stringResource(R.string.important_context_body))
                    PrimaryButton(stringResource(R.string.important_choose_context), onChooseContext)
                }
                else -> {
                    item {
                        SecondaryButton("${state.context.course.code} · ${state.context.period.name}", onChooseContext,
                            enabled = state.removingId == null && !state.isStarting, modifier = Modifier.fillMaxWidth().testTag("important_context"))
                        FilterMenu(state.scope?.subjectId?.let { id -> state.context.subjects.find { it.id == id }?.name }
                            ?: stringResource(R.string.important_all_subjects),
                            state.context.subjects.map { it.id to it.name }, stringResource(R.string.important_all_subjects),
                            state.removingId == null && !state.isStarting, "important_subject_filter", onSubject)
                        FilterMenu(state.scope?.chapterId?.let { id -> state.chapters.find { it.id == id }?.title }
                            ?: stringResource(R.string.important_all_chapters),
                            state.chapters.map { it.id to it.title }, stringResource(R.string.important_all_chapters),
                            state.removingId == null && !state.isStarting && state.scope?.subjectId != null, "important_chapter_filter", onChapter)
                    }
                    item {
                        val enabled = state.rows.isNotEmpty() && state.removingId == null && !state.isStarting && !state.hasRemoveError && !state.hasLaunchError
                        SavedAction(stringResource(R.string.important_prepare_saved), "important_prepare", enabled, onPrepare)
                        SavedAction(stringResource(R.string.important_test_saved), "important_test", enabled, onTest)
                        Text(stringResource(R.string.important_saved_count, state.rows.size), style = MaterialTheme.typography.titleMedium)
                        TextButton(onRefresh, enabled = state.removingId == null && !state.isStarting, modifier = Modifier.testTag("important_refresh")) {
                            Text(stringResource(R.string.important_refresh))
                        }
                    }
                    if (state.hasRemoveError) item {
                        ErrorState(stringResource(R.string.important_remove_error), onRetry = onRetryRemove)
                    }
                    if (state.isStarting) item { Text(stringResource(R.string.important_starting)) }
                    if (state.hasLaunchError) item { ErrorState(stringResource(R.string.important_launch_error), onRetry = onRetryLaunch) }
                    if (state.noQuestions) item {
                        Text(stringResource(R.string.important_no_questions))
                        if (state.noTestQuestions) SecondaryButton(stringResource(R.string.important_test_defaults), onOpenDefaults)
                    }
                    if (state.displayedRows.isEmpty()) item {
                        EmptyState(stringResource(R.string.important_empty_title), stringResource(R.string.important_empty_body))
                    }
                    items(state.displayedRows, key = { it.questionId }) { row ->
                        val removed = state.removedRows.any { it.questionId == row.questionId }
                        Card(Modifier.fillMaxWidth().testTag("important_row_${row.questionId}")) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (removed) {
                                    Text(row.question)
                                    Text(stringResource(R.string.important_removed), modifier = Modifier.semantics { disabled() })
                                } else {
                                    TextButton({ onOpenQuestion(row) }, enabled = state.removingId == null && !state.isStarting,
                                        modifier = Modifier.testTag("important_open_${row.questionId}")) { Text(row.question) }
                                }
                                Text("${row.courseCode} · ${row.periodName} · ${row.subjectCode} · ${row.chapterTitle}",
                                    style = MaterialTheme.typography.bodySmall)
                                if (!removed) TextButton({ onRemove(row.questionId) },
                                    enabled = state.removingId == null && !state.isStarting && !state.hasRemoveError && !state.hasLaunchError,
                                    modifier = Modifier.testTag("important_remove_${row.questionId}")) {
                                    Text(stringResource(if (state.removingId == row.questionId) R.string.bookmark_saving else R.string.important_remove))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedAction(label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().testTag(tag)
    PrimaryButton(label, onClick, enabled = enabled, modifier = if (enabled) modifier else modifier.clearAndSetSemantics {
        contentDescription = label; disabled()
    })
}

@Composable
private fun FilterMenu(label: String, options: List<Pair<String, String>>, allLabel: String,
    enabled: Boolean, tag: String, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        SecondaryButton(label, { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag))
        DropdownMenu(expanded && enabled, { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { expanded = false; onSelect(null) })
            options.forEach { (id, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; onSelect(id) })
            }
        }
    }
}
