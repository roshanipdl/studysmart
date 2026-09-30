package com.studysmart.app.feature.test

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.studysmart.app.core.model.TestAttemptStatus
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.MissingContentScreen
import com.studysmart.app.core.ui.PrimaryButton
import com.studysmart.app.core.ui.SecondaryButton
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TestAttemptScreen(
    state: TestAttemptUiState,
    onRetry: () -> Unit,
    onRetryOrigin: () -> Unit,
    onRetryMutation: () -> Unit,
    onAnswerChanged: (String) -> Unit,
    onFlag: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectQuestion: (Int) -> Unit,
    onFlushDraft: () -> Unit,
    onRequestSubmit: () -> Unit,
    onConfirmSubmit: () -> Unit,
    onCancelSubmit: () -> Unit,
    onConfirmExpiredSubmit: () -> Unit,
    onDiscardAfterExpiry: () -> Unit,
    onClose: () -> Unit,
    onDiscard: () -> Unit,
    onCancelDiscard: () -> Unit,
    onBack: () -> Unit,
    onViewResult: () -> Unit,
    onAppBackgrounded: () -> Unit,
    onAppForegrounded: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(20.dp),
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, onAppBackgrounded, onAppForegrounded) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> onAppBackgrounded()
                Lifecycle.Event.ON_START -> onAppForegrounded()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (!state.isLoading && state.error == null && state.isMissingContent) {
        MissingContentScreen(title = "Mock Test", onBack = onBack)
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mock test", style = MaterialTheme.typography.titleLarge)
                        state.attempt?.attempt?.let { attempt ->
                            Text(
                                text = "${attempt.actualQuestionCount} questions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    val canClose = !state.isDiscarding && !state.isSubmitting &&
                        (state.attempt?.attempt?.status?.isTransient() == true || state.canNavigateBack)
                    androidx.compose.material3.IconButton(
                        onClick = if (state.attempt?.attempt?.status?.isTransient() == true) onClose else onBack,
                        enabled = canClose,
                        modifier = Modifier.testTag("test_close"),
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close test",
                        )
                    }
                },
                actions = {
                    if (state.attempt?.attempt?.isTimed == true) {
                        Text(
                            text = formatTimer(state.timeRemainingSeconds ?: 0L),
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .testTag("test_timer"),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.isExpired) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .testTag("test_attempt_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.isLoading -> LoadingState(modifier = Modifier.testTag("test_attempt_loading"))
                state.isCorruptContent -> {
                    ErrorState(
                        message = state.error ?: "This test attempt cannot be opened safely.",
                        onRetry = onRetry,
                        modifier = Modifier.testTag("test_attempt_corrupt"),
                    )
                    SecondaryButton(
                        text = "Discard attempt",
                        onClick = onClose,
                        enabled = !state.isDiscarding,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.error != null -> ErrorState(
                    message = state.error,
                    onRetry = onRetry,
                    modifier = Modifier.testTag("test_attempt_error"),
                )
                state.attempt == null -> MissingContentScreen(title = "Mock Test", onBack = onBack)
                state.attempt.attempt.status == TestAttemptStatus.SUBMITTED -> {
                    Text("This test has already been submitted.", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Submitted attempts are read-only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryButton(
                        text = "View test result",
                        onClick = onViewResult,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = "Back",
                        onClick = onBack,
                        enabled = state.canNavigateBack,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    ActiveTestContent(
                        state = state,
                        onAnswerChanged = onAnswerChanged,
                        onFlag = onFlag,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onSelectQuestion = onSelectQuestion,
                        onFlushDraft = onFlushDraft,
                        onRequestSubmit = onRequestSubmit,
                        onRetryMutation = onRetryMutation,
                    )
                }
            }
            if (state.isLoadingOrigin) {
                Text(
                    "Checking where to return…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("test_attempt_origin_loading"),
                )
            }
            if (state.originError != null) {
                Text(
                    "Unable to verify the saved return destination. Retry before leaving this test.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("test_attempt_origin_error"),
                )
                OutlinedButton(
                    onClick = onRetryOrigin,
                    enabled = !state.isLoadingOrigin,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("test_attempt_retry_origin"),
                ) { Text("Retry origin lookup") }
            }
        }
    }

    if (state.showSubmitConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelSubmit,
            title = { Text("Submit test?") },
            text = {
                Text(
                    buildString {
                        if (state.mutationError != null) append("${state.mutationError} ")
                        append("Answered: ${state.answeredCount} · Unanswered: ${state.unansweredCount} · ")
                        append("Flagged: ${state.flaggedCount}. Your answers will be evaluated once submitted.")
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmSubmit,
                    enabled = !state.isSubmitting && !state.isSaving,
                    modifier = Modifier.testTag("test_confirm_submit"),
                ) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = onCancelSubmit, enabled = !state.isSubmitting) { Text("Cancel") }
            },
        )
    }
    if (state.showExpiredConfirmation) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Time is up") },
            text = {
                Text(
                    when {
                        state.mutationError != null -> state.mutationError
                        state.isDraftSaveFailed -> "Submit to save and evaluate your locked answers, or discard the attempt."
                        else -> "Your answers are locked. Submit to evaluate them, or discard the attempt."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmExpiredSubmit,
                    enabled = !state.isSubmitting && !state.isSaving,
                    modifier = Modifier.testTag("test_expired_submit"),
                ) { Text("Submit") }
            },
            dismissButton = {
                TextButton(
                    onClick = onDiscardAfterExpiry,
                    enabled = !state.isDiscarding && !state.isSubmitting,
                    modifier = Modifier.testTag("test_expired_discard"),
                ) { Text("Discard") }
            },
        )
    }
    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelDiscard,
            title = { Text("Discard test?") },
            text = {
                Column {
                    Text("This test attempt, snapshots, and draft answers will be deleted.")
                    state.discardError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("test_discard_error"))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDiscard,
                    enabled = !state.isDiscarding,
                    modifier = Modifier.testTag("test_discard"),
                ) { Text(if (state.discardError == null) "Discard" else "Retry discard") }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancelDiscard,
                    enabled = !state.isDiscarding,
                    modifier = Modifier.testTag("test_cancel_discard"),
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ActiveTestContent(
    state: TestAttemptUiState,
    onAnswerChanged: (String) -> Unit,
    onFlag: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectQuestion: (Int) -> Unit,
    onFlushDraft: () -> Unit,
    onRequestSubmit: () -> Unit,
    onRetryMutation: () -> Unit,
) {
    val model = state.attempt ?: return
    val question = model.questions.getOrNull(state.currentQuestionIndex) ?: return
    val canEdit = !state.isExpired && !state.isSaving && !state.isSubmitting && !state.isDiscarding
    val canNavigate = canEdit && state.mutationError == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("test_attempt_content"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Question ${question.displayOrder + 1} of ${model.attempt.actualQuestionCount}",
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Question navigator", style = MaterialTheme.typography.titleSmall)
            Text(
                "${state.answeredCount} answered · ${state.unansweredCount} unanswered · ${state.flaggedCount} flagged",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag("test_question_navigator"),
            maxItemsInEachRow = 5,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.questionNavigation.forEachIndexed { index, item ->
                val label = buildString {
                    append("Question ${item.number}")
                    append(if (item.isCurrent) ", current" else "")
                    append(if (item.isAnswered) ", answered" else ", unanswered")
                    append(if (item.isFlagged) ", flagged" else "")
                }
                val container = when {
                    item.isCurrent -> MaterialTheme.colorScheme.primary
                    item.isAnswered -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
                val contentColor = if (item.isCurrent) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Surface(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(enabled = canNavigate && !item.isCurrent) { onSelectQuestion(index) }
                        .semantics {
                            contentDescription = label
                            stateDescription = label
                            selected = item.isCurrent
                            role = Role.Button
                        }
                        .testTag("test_question_${item.number}"),
                    color = container,
                    contentColor = contentColor,
                    shape = RoundedCornerShape(12.dp),
                    border = if (item.isFlagged) BorderStroke(2.dp, MaterialTheme.colorScheme.error) else null,
                ) {
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.number.toString(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (state.isSaving) {
            Text(
                "Saving answer…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("test_saving"),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${question.maxScoreSnapshot} marks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(question.questionTextSnapshot, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = state.draftAnswer,
                    onValueChange = onAnswerChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .onFocusChanged { if (!it.isFocused) onFlushDraft() }
                        .testTag("test_answer_input"),
                    enabled = canEdit,
                    label = { Text("Your answer") },
                    placeholder = { Text("Write your answer…") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                    ),
                    minLines = 4,
                    maxLines = 10,
                )
                OutlinedButton(
                    onClick = onFlag,
                    enabled = canEdit,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("test_flag"),
                ) {
                    Text(if (state.isCurrentFlagged) "⚑ Flagged for review" else "⚑ Flag for review")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton(
                text = "Previous",
                onClick = onPrevious,
                enabled = canNavigate && state.currentQuestionIndex > 0,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onNext,
                enabled = canNavigate && state.currentQuestionIndex < model.questions.lastIndex,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("test_next"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text("Next")
            }
        }
        if (state.mutationError != null) {
            ErrorState(
                message = state.mutationError,
                onRetry = onRetryMutation,
                modifier = Modifier.testTag("test_attempt_save_error"),
            )
        }
        PrimaryButton(
            text = if (state.isSubmitting) "Submitting…" else "Submit test",
            onClick = onRequestSubmit,
            enabled = canNavigate,
            modifier = Modifier.fillMaxWidth().testTag("test_submit"),
        )
    }
}

private fun formatTimer(seconds: Long): String {
    val bounded = seconds.coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%02d:%02d", bounded / 60L, bounded % 60L)
}
