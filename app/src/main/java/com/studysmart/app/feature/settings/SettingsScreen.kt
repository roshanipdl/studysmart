package com.studysmart.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.ui.PrimaryButton
import com.studysmart.app.core.ui.StudyTopBar
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.model.PreparationDefaults

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenTestDefaults: () -> Unit,
    onRetry: () -> Unit,
    onDecrementCount: () -> Unit,
    onIncrementCount: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(20.dp),
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { StudyTopBar(title = "Settings", onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(contentPadding)
                .testTag("settings_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Preferences", style = MaterialTheme.typography.headlineSmall)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Preparation & review", style = MaterialTheme.typography.titleMedium)
                    Text("Preparation & review question count", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Up to this many unique questions in a new preparation or due-review session. Repeated questions may appear again.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    when {
                        state.isLoading -> LoadingState(
                            message = "Loading session question count",
                            modifier = Modifier.testTag("review_defaults_loading"),
                        )
                        state.preparationDefaults == null -> ErrorState(
                            message = state.error ?: "Session question count is unavailable.",
                            onRetry = onRetry, modifier = Modifier.testTag("review_defaults_error"),
                        )
                        else -> {
                            if (state.error != null) ErrorState(
                                message = state.error, onRetry = if (state.isSaving) null else onRetry,
                                modifier = Modifier.testTag("review_defaults_error"),
                            )
                            val count = state.preparationDefaults.questionCount
                            val enabled = !state.isSaving && !state.isSaveError
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onDecrementCount,
                                    enabled = enabled && count > PreparationDefaults.MIN_QUESTION_COUNT,
                                    modifier = Modifier.testTag("review_count_decrement").semantics {
                                        contentDescription = "Decrease preparation and review question count"
                                    },
                                ) { Text("−") }
                                Text(
                                    "$count", style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.testTag("review_question_count"),
                                )
                                IconButton(
                                    onClick = onIncrementCount,
                                    enabled = enabled && count < PreparationDefaults.MAX_QUESTION_COUNT,
                                    modifier = Modifier.testTag("review_count_increment").semantics {
                                        contentDescription = "Increase preparation and review question count"
                                    },
                                ) { Text("+") }
                            }
                            Text(
                                if (state.isSaving) "Saving changes…" else "1–30 unique questions. Valid changes are saved automatically.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Testing", style = MaterialTheme.typography.titleMedium)
                    Text("Configure the defaults used when a test starts.", style = MaterialTheme.typography.bodyMedium)
                    PrimaryButton(
                        text = "Test defaults",
                        onClick = onOpenTestDefaults,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
