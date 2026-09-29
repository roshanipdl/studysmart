package com.studysmart.app.feature.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.MissingContentScreen
import com.studysmart.app.core.ui.StudyTopBar

@Composable
fun TestAttemptScreen(
    state: TestAttemptUiState,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onDiscard: () -> Unit,
    onCancelDiscard: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(20.dp),
) {
    if (!state.isLoading && state.error == null && state.isMissingContent) {
        MissingContentScreen(title = "Mock Test", onBack = onBack)
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = "Mock Test",
                onBack = if (state.attempt?.attempt?.status?.isTransient() == true) onClose else onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(contentPadding)
                .testTag("test_attempt_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.isLoading -> LoadingState(modifier = Modifier.testTag("test_attempt_loading"))
                state.error != null -> ErrorState(
                    message = state.error,
                    onRetry = onRetry,
                    modifier = Modifier.testTag("test_attempt_error"),
                )
                state.attempt == null -> MissingContentScreen(title = "Mock Test", onBack = onBack)
                state.attempt.attempt.status == com.studysmart.app.core.model.TestAttemptStatus.SUBMITTED -> {
                    Text("This test has already been submitted.", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onBack) { Text("Back") }
                }
                else -> {
                    Text("Test attempt created", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${state.attempt.attempt.actualQuestionCount} questions are ready. " +
                            "The answering screen will be available in the next MVP phase.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onClose, enabled = !state.isDiscarding) {
                        Text("Discard Test")
                    }
                }
            }
        }
    }
    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelDiscard,
            title = { Text("Discard test?") },
            text = { Text("This test attempt and its draft answers will be deleted.") },
            confirmButton = {
                TextButton(onClick = onDiscard, enabled = !state.isDiscarding) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDiscard, enabled = !state.isDiscarding) {
                    Text("Cancel")
                }
            },
        )
    }
}
