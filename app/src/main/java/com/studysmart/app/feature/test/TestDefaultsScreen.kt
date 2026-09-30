package com.studysmart.app.feature.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.TestQuestionSource
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.StudyTopBar

@Composable
fun TestDefaultsScreen(
    state: TestDefaultsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDecrementCount: () -> Unit,
    onIncrementCount: () -> Unit,
    onSetTimedMode: (Boolean) -> Unit,
    onDecrementDuration: () -> Unit,
    onIncrementDuration: () -> Unit,
    onSetSource: (TestQuestionSource) -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { StudyTopBar(title = "Test Defaults", onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(contentPadding)
                .testTag("test_defaults_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "These defaults are applied when a test starts from a chapter or saved questions.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            when {
                state.isLoading -> LoadingState(
                    message = "Loading test defaults",
                    modifier = Modifier.testTag("test_defaults_loading"),
                )
                state.defaults == null -> ErrorState(
                    message = state.error ?: "Test defaults are unavailable.",
                    onRetry = onRetry,
                    modifier = Modifier.testTag("test_defaults_error"),
                )
                else -> {
                    if (state.error != null) {
                        ErrorState(
                            message = state.error,
                            onRetry = onRetry,
                            modifier = Modifier.testTag("test_defaults_save_error"),
                        )
                    }
                    DefaultsControls(
                        defaults = state.defaults,
                        controlsEnabled = !state.isSaving && !state.isSaveError,
                        onDecrementCount = onDecrementCount,
                        onIncrementCount = onIncrementCount,
                        onSetTimedMode = onSetTimedMode,
                        onDecrementDuration = onDecrementDuration,
                        onIncrementDuration = onIncrementDuration,
                        onSetSource = onSetSource,
                    )
                    Text(
                        if (state.isSaving) "Saving changes…" else "Valid changes are saved automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultsControls(
    defaults: TestDefaults,
    controlsEnabled: Boolean,
    onDecrementCount: () -> Unit,
    onIncrementCount: () -> Unit,
    onSetTimedMode: (Boolean) -> Unit,
    onDecrementDuration: () -> Unit,
    onIncrementDuration: () -> Unit,
    onSetSource: (TestQuestionSource) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Default question count", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Up to 30 eligible questions", style = MaterialTheme.typography.bodySmall)
                    StepperRow(
                        label = "Question count",
                        value = defaults.questionCount.toString(),
                        onDecrement = onDecrementCount,
                        onIncrement = onIncrementCount,
                        decrementEnabled = controlsEnabled && defaults.questionCount > TestDefaults.MIN_QUESTION_COUNT,
                        incrementEnabled = controlsEnabled && defaults.questionCount < TestDefaults.MAX_QUESTION_COUNT,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Timing", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Timed test", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = defaults.timedMode,
                        onCheckedChange = onSetTimedMode,
                        enabled = controlsEnabled,
                        modifier = Modifier.semantics { contentDescription = "Timed test" },
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Default duration", style = MaterialTheme.typography.bodyLarge)
                    StepperRow(
                        label = "Default duration in minutes",
                        value = "${defaults.durationMinutes} minutes",
                        onDecrement = onDecrementDuration,
                        onIncrement = onIncrementDuration,
                        decrementEnabled = controlsEnabled && defaults.timedMode &&
                            defaults.durationMinutes > TestDefaults.MIN_DURATION_MINUTES,
                        incrementEnabled = controlsEnabled && defaults.timedMode &&
                            defaults.durationMinutes < TestDefaults.MAX_DURATION_MINUTES,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Question source", style = MaterialTheme.typography.titleMedium)
            SourceChipRow(
                sources = listOf(TestQuestionSource.ALL, TestQuestionSource.RANDOM, TestQuestionSource.WEAK),
                selected = defaults.questionSource,
                enabled = controlsEnabled,
                onSetSource = onSetSource,
            )
            SourceChipRow(
                sources = listOf(TestQuestionSource.UNSEEN, TestQuestionSource.IMPORTANT),
                selected = defaults.questionSource,
                enabled = controlsEnabled,
                onSetSource = onSetSource,
            )
            Text(
                "Random samples the eligible pool. Weak, unseen and important filter before sampling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onDecrement,
            enabled = decrementEnabled,
            modifier = Modifier.semantics { contentDescription = "Decrease $label" },
        ) { Text("−") }
        Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.small) {
            Text(
                value,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { contentDescription = "$label: $value" },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        IconButton(
            onClick = onIncrement,
            enabled = incrementEnabled,
            modifier = Modifier.semantics { contentDescription = "Increase $label" },
        ) { Text("+") }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SourceChipRow(
    sources: List<TestQuestionSource>,
    selected: TestQuestionSource,
    enabled: Boolean,
    onSetSource: (TestQuestionSource) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        sources.forEach { source ->
            FilterChip(
                selected = selected == source,
                onClick = { onSetSource(source) },
                enabled = enabled,
                label = { Text(source.label) },
                modifier = Modifier.semantics { contentDescription = "${source.label} question source" },
            )
        }
    }
}

private val TestQuestionSource.label: String
    get() = name.lowercase().replaceFirstChar { character -> character.uppercase() }
