package com.studysmart.app.feature.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.studysmart.app.R
import com.studysmart.app.core.ui.EmptyState
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.PrimaryButton
import com.studysmart.app.core.ui.SecondaryButton
import com.studysmart.app.core.ui.StudyTopBar
import com.studysmart.app.feature.test.TestAttemptLauncherUiState

@Composable
fun ChapterDetailScreen(
    state: ChapterDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPrepare: () -> Unit,
    onTest: () -> Unit,
    testState: TestAttemptLauncherUiState,
    onRetryTest: () -> Unit,
    onOpenTestDefaults: () -> Unit,
    onDismissNoQuestions: () -> Unit,
) {
    val listState = rememberLazyListState()
    val content = state.content
    LaunchedEffect(state.highlightKey, content?.items) {
        val key = state.highlightKey ?: return@LaunchedEffect
        val index = if (key == "chapter_header") 0 else {
            content?.items?.indexOfFirst { it.key == key }?.takeIf { it >= 0 }?.plus(1)
                ?: return@LaunchedEffect
        }
        listState.scrollToItem(index)
    }
    Scaffold(topBar = {
        StudyTopBar(title = content?.title ?: stringResource(R.string.chapter_detail_title),
            subtitle = content?.contextLabel, onBack = onBack)
    }) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).testTag("chapter_detail_screen"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.isLoading -> item { LoadingState() }
                state.hasError -> item { ErrorState(stringResource(R.string.chapter_detail_error), onRetry = onRetry) }
                content == null -> item {
                    EmptyState(stringResource(R.string.chapter_detail_missing), stringResource(R.string.chapter_detail_missing_body))
                }
                else -> {
                    item(key = "chapter_header") {
                        Card(Modifier.fillMaxWidth().testTag("chapter_header").semantics { selected = state.highlightKey == "chapter_header" },
                            colors = CardDefaults.cardColors(containerColor = if (state.highlightKey == "chapter_header")
                                MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(content.title, style = MaterialTheme.typography.headlineSmall)
                                Text(content.contextLabel, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    items(content.items, key = { it.key }) { row ->
                        val highlighted = row.key == state.highlightKey
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag(row.key).semantics { selected = highlighted },
                            colors = CardDefaults.cardColors(containerColor = if (highlighted)
                                MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                        ) {
                            Text(when (row) {
                                is ChapterDetailItem.Topic -> row.title
                                is ChapterDetailItem.Question -> row.text
                            }, modifier = Modifier.padding(16.dp), style = if (row is ChapterDetailItem.Topic)
                                MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (content.items.isEmpty()) EmptyState(stringResource(R.string.chapter_detail_empty),
                                stringResource(R.string.chapter_detail_empty_body))
                            if (testState.error != null || testState.isMissingScope) {
                                ErrorState(stringResource(R.string.chapter_test_error), onRetry = onRetryTest)
                            }
                            val prepareLabel = stringResource(R.string.chapter_prepare)
                            val testLabel = stringResource(R.string.chapter_test)
                            val prepareEnabled = content.questionCount > 0 && !testState.isCreating
                            val testEnabled = prepareEnabled && testState.attemptId == null &&
                                testState.error == null && !testState.isMissingScope
                            PrimaryButton(prepareLabel, onPrepare, enabled = prepareEnabled,
                                modifier = Modifier.fillMaxWidth().testTag("chapter_detail_prepare")
                                    .withoutDisabledClick(prepareEnabled, prepareLabel))
                            SecondaryButton(testLabel, onTest, enabled = testEnabled,
                                modifier = Modifier.fillMaxWidth().testTag("chapter_detail_test")
                                    .withoutDisabledClick(testEnabled, testLabel))
                        }
                    }
                }
            }
        }
    }
    NoTestQuestionsSheet(testState, onOpenTestDefaults, onDismissNoQuestions)
}

private fun Modifier.withoutDisabledClick(enabled: Boolean, label: String): Modifier =
    if (enabled) this else clearAndSetSemantics {
        disabled()
        role = Role.Button
        contentDescription = label
    }
