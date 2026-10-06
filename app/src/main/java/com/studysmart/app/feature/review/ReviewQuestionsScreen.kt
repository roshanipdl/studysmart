package com.studysmart.app.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import com.studysmart.app.core.ui.*

@Composable
fun ReviewQuestionsScreen(state: ReviewUiState, filter: ReviewFilter, onBack: () -> Unit,
    onRetry: () -> Unit, onStart: () -> Unit, onChoose: () -> Unit) {
    val rows = if (state.isLoading || state.isContextRequired || state.displayError != null) emptyList()
        else state.statusPools[filter].orEmpty()
    Scaffold(topBar = { StudyTopBar(title = filter.title, subtitle = state.contextLabel,
        variant = StudyTopBarVariant.BACK, onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).testTag("review_questions"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                when {
                    state.isLoading -> LoadingState(message = "Loading questions")
                    state.displayError != null -> ErrorState(message = state.displayError!!, onRetry = onRetry)
                    state.isContextRequired -> PrimaryButton(text = "Choose study context", onClick = onChoose)
                    rows.isEmpty() -> EmptyState(title = "No questions", message = "No questions match this status in your selected context.")
                }
            }
            items(rows, key = { it.questionId }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.questionText, style = MaterialTheme.typography.bodyLarge)
                        Text(listOfNotNull(row.chapterTitle, row.topicTitle).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                PrimaryButton(text = if (state.isStarting) "Starting..." else "Start",
                    onClick = onStart, enabled = !state.isLoading && !state.isContextRequired && !state.isStarting && rows.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).then(
                        if (rows.isEmpty()) Modifier.clearAndSetSemantics {
                            disabled()
                            contentDescription = "Start"
                            testTag = "review_filter_start"
                        } else Modifier.testTag("review_filter_start")))
            }
        }
    }
}
