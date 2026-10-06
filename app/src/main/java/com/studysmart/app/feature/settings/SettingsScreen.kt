package com.studysmart.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import com.studysmart.app.core.model.ThemePreference
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.studysmart.app.R
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
    onDecrementGoal: () -> Unit = {},
    onIncrementGoal: () -> Unit = {},
    onChooseCourse: () -> Unit = {},
    onChoosePeriod: () -> Unit = {},
    onTheme: (ThemePreference) -> Unit = {},
    onEditName: () -> Unit = {},
    onNameChange: (String) -> Unit = {},
    onSaveName: () -> Unit = {},
    onCancelName: () -> Unit = {},
    onRequestClear: () -> Unit = {},
    onConfirmClear: () -> Unit = {},
    onCancelClear: () -> Unit = {},
) {
    if (state.clearConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!state.isSaving) onCancelClear() },
            title = { Text(stringResource(R.string.settings_clear_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_clear_deletes))
                    Text(stringResource(R.string.settings_clear_keeps))
                    Text(stringResource(R.string.settings_clear_irreversible))
                    if (state.isSaving) Text(stringResource(R.string.settings_clear_pending))
                    if (state.clearError != null) Text(stringResource(R.string.settings_clear_failure), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onConfirmClear, enabled = !state.isSaving) {
                    Text(stringResource(if (state.clearError == null) R.string.settings_clear_confirm else R.string.settings_clear_retry))
                }
            },
            dismissButton = { TextButton(onCancelClear, enabled = !state.isSaving) { Text(stringResource(R.string.settings_clear_cancel)) } },
        )
    }
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
            Text("Study context", style = MaterialTheme.typography.headlineSmall)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onChooseCourse, enabled = !state.isSaving && !state.hasSaveError,
                        modifier = Modifier.testTag("settings_course")) { Text("Current course: ${state.courseName ?: "Choose course"}") }
                    TextButton(onChoosePeriod, enabled = !state.isSaving && !state.hasSaveError && state.courseId != null,
                        modifier = Modifier.testTag("settings_period")) { Text("Academic period: ${state.periodName ?: "Choose period"}") }
                    state.contextError?.let { ErrorState(it, onRetry = onRetry) }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Local name", style = MaterialTheme.typography.titleMedium)
                    if (state.nameDraft == null) {
                        Text(state.displayName ?: "Local name unavailable")
                        TextButton(onEditName, enabled = state.canMutate && state.displayName != null) { Text("Edit name") }
                    } else {
                        OutlinedTextField(value = state.nameDraft, onValueChange = onNameChange,
                            label = { Text("Name") }, enabled = !state.isSaving && !state.hasSaveError,
                            isError = state.nameError != null, modifier = Modifier.fillMaxWidth().testTag("settings_name_input"))
                        Row {
                            TextButton(onSaveName, enabled = state.canMutate) { Text("Save") }
                            TextButton(onCancelName, enabled = !state.isSaving) { Text("Cancel") }
                        }
                    }
                    state.nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.displayName == null && state.nameError != null) TextButton(onRetry) { Text("Retry") }
                }
            }
            Text("Preferences", style = MaterialTheme.typography.headlineSmall)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Theme: ${state.theme.label}", style = MaterialTheme.typography.titleMedium)
                    ThemeSelector(state.theme, state.canMutate, onTheme)
                    state.preferenceError?.let { ErrorState(it, onRetry = if (state.isSaving) null else onRetry) }
                }
            }
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
                            val enabled = state.canMutate
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.progress_daily_goal), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_goal_description))
                    when {
                        state.isLoading -> LoadingState()
                        state.dailyCardGoal == null -> ErrorState(state.error ?: stringResource(R.string.settings_goal_error), onRetry = onRetry)
                        else -> {
                            state.goalError?.let { ErrorState(it, onRetry = if (state.isSaving) null else onRetry) }
                            val goal = state.dailyCardGoal
                            val enabled = state.canMutate
                            val decrease = stringResource(R.string.settings_goal_decrease)
                            val increase = stringResource(R.string.settings_goal_increase)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onDecrementGoal, enabled = enabled && goal > com.studysmart.app.core.model.DailyCardGoal.MIN,
                                    modifier = Modifier.testTag("daily_goal_decrement").semantics { contentDescription = decrease }) { Text("−") }
                                Text("$goal", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("daily_card_goal"))
                                IconButton(onIncrementGoal, enabled = enabled && goal < com.studysmart.app.core.model.DailyCardGoal.MAX,
                                    modifier = Modifier.testTag("daily_goal_increment").semantics { contentDescription = increase }) { Text("+") }
                            }
                            Text(stringResource(R.string.settings_goal_range), style = MaterialTheme.typography.bodySmall)
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
                    state.testDefaults?.let { defaults ->
                        Text("${defaults.questionCount} questions · ${defaults.questionSource.name.lowercase().replaceFirstChar { it.uppercase() }} · " +
                            if (defaults.timedMode) "${defaults.durationMinutes} minutes" else "Untimed",
                            modifier = Modifier.testTag("settings_test_summary"))
                    }
                    PrimaryButton(
                        text = "Test defaults",
                        onClick = onOpenTestDefaults,
                        enabled = !state.isSaving && !state.hasSaveError,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_privacy_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_privacy_notice))
                    Text(stringResource(R.string.settings_crash_reporting_off))
                    Text(stringResource(R.string.settings_diagnostics_notice))
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_learning_data), style = MaterialTheme.typography.titleMedium)
                    TextButton(onRequestClear, enabled = state.canMutate,
                        modifier = Modifier.testTag("settings_clear_progress")) { Text(stringResource(R.string.settings_clear_progress), color = MaterialTheme.colorScheme.error) }
                    if (state.clearSucceeded) Text(stringResource(R.string.settings_clear_success),
                        modifier = Modifier.testTag("settings_clear_success"))
                }
            }
        }
    }
}

@Composable
internal fun ThemeSelector(
    selectedTheme: ThemePreference,
    enabled: Boolean,
    onTheme: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().selectableGroup().testTag("settings_theme_group"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemePreference.entries.forEach { theme ->
            val selected = theme == selectedTheme
            val foreground = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .testTag("settings_theme_${theme.name}")
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onTheme(theme) }),
                shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        when (theme) {
                            ThemePreference.LIGHT -> Icons.Filled.LightMode
                            ThemePreference.DARK -> Icons.Filled.DarkMode
                            ThemePreference.SYSTEM -> Icons.Filled.SettingsBrightness
                        },
                        contentDescription = null, tint = foreground,
                        modifier = Modifier.size(24.dp).testTag("settings_theme_icon_${theme.name}"),
                    )
                    Text(theme.label, color = foreground, style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center, modifier = Modifier.testTag("settings_theme_label_${theme.name}"))
                    // Reserve the indicator slot so selection never changes tile geometry.
                    Icon(Icons.Filled.Check, contentDescription = null,
                        tint = if (selected) foreground else androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.size(18.dp).testTag("settings_theme_check_${theme.name}"))
                }
            }
        }
    }
}
