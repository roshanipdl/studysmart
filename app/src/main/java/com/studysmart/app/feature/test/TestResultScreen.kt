package com.studysmart.app.feature.test

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.PrimaryButton
import com.studysmart.app.core.ui.StudyTopBar
import com.studysmart.app.core.ui.StudyTopBarVariant
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestResultScreen(
    state: TestResultUiState,
    onRetry: () -> Unit,
    onRetryOrigin: () -> Unit,
    onRetryFollowUp: () -> Unit,
    onRetryActionContext: () -> Unit,
    onToggleQuestion: (String) -> Unit,
    onRetestWeak: () -> Unit,
    onNewTest: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = "Test result",
                subtitle = state.report?.let { "${it.attempt.actualQuestionCount} questions" },
                variant = StudyTopBarVariant.BACK_SUBTITLE,
                onBack = onBack,
                backEnabled = state.canNavigateBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(20.dp)
                .testTag("test_result_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                state.isLoading -> LoadingState(modifier = Modifier.testTag("test_result_loading"))
                state.isMissingContent -> {
                    Text("This test result is no longer available.", style = MaterialTheme.typography.titleLarge)
                    TextButton(
                        onClick = onBack,
                        enabled = state.canNavigateBack,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("test_result_back_missing"),
                    ) { Text("Back") }
                }
                state.error != null && !state.isCorruptContent -> ErrorState(
                    message = state.error,
                    onRetry = onRetry,
                    modifier = Modifier.testTag("test_result_error"),
                )
                state.isCorruptContent -> ErrorState(
                    message = "This result is incomplete and cannot be displayed safely.",
                    modifier = Modifier.testTag("test_result_corrupt"),
                )
                state.isNotSubmitted -> {
                    Text("This test has not been submitted.", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Only completed test results can be viewed here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onBack,
                        enabled = state.canNavigateBack,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("test_result_back_unsubmitted"),
                    ) { Text("Back") }
                }
                state.report == null -> {
                    Text("This test result cannot be displayed.", style = MaterialTheme.typography.titleLarge)
                    TextButton(
                        onClick = onBack,
                        enabled = state.canNavigateBack,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("test_result_back_unavailable"),
                    ) { Text("Back") }
                }
                else -> TestResultContent(
                    state = state,
                    onRetryFollowUp = onRetryFollowUp,
                    onRetryActionContext = onRetryActionContext,
                    onToggleQuestion = onToggleQuestion,
                    onRetestWeak = onRetestWeak,
                    onNewTest = onNewTest,
                    onBack = onBack,
                )
            }
            if (state.isLoadingOrigin) {
                Text(
                    "Checking where to return…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("test_result_origin_loading"),
                )
            }
            if (state.originError != null) {
                Text(
                    "Unable to verify the saved return destination. Retry before leaving this result.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("test_result_origin_error"),
                )
                OutlinedButton(
                    onClick = onRetryOrigin,
                    enabled = !state.isLoadingOrigin,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("test_result_retry_origin"),
                ) { Text("Retry origin lookup") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestResultContent(
    state: TestResultUiState,
    onRetryFollowUp: () -> Unit,
    onRetryActionContext: () -> Unit,
    onToggleQuestion: (String) -> Unit,
    onRetestWeak: () -> Unit,
    onNewTest: () -> Unit,
    onBack: () -> Unit,
) {
    val report = state.report ?: return
    Text("Test complete", style = MaterialTheme.typography.headlineMedium)
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("test_result_score"),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Score", style = MaterialTheme.typography.labelLarge)
            Text(
                "${formatScore(report.score)} / ${formatScore(report.maxScore)}",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text("${formatPercent(report.percentage)} of available marks", style = MaterialTheme.typography.bodyMedium)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        com.studysmart.app.core.ui.ResultMetric(
            value = if (report.evaluableQuestionCount == 0) "N/A" else formatPercent(report.weightedKeywordMatchPercent),
            label = "Keyword match",
            modifier = Modifier.weight(1f).testTag("test_result_keyword_match"),
        )
        com.studysmart.app.core.ui.ResultMetric(
            value = formatDuration(report.timeTakenSeconds),
            label = "Time taken",
            modifier = Modifier.weight(1f).testTag("test_result_time"),
        )
        com.studysmart.app.core.ui.ResultMetric(
            value = report.needsReviewCount.toString(),
            label = "Need review",
            modifier = Modifier.weight(1f).testTag("test_result_review_count"),
        )
    }
    Text(
        "Keyword match is a text-matching estimate, not a measure of semantic correctness.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("Question report", style = MaterialTheme.typography.titleLarge)
    report.questions.forEachIndexed { index, question ->
        val expanded = state.expandedQuestionId == question.snapshot.id
        QuestionReportRow(
            questionNumber = index + 1,
            report = question,
            expanded = expanded,
            onClick = { onToggleQuestion(question.snapshot.id) },
        )
    }
    Text(
        "Select a question to view your answer, reference answer, and frozen keyword evidence.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (state.mutationError != null) {
        Text(
            state.mutationError,
            modifier = Modifier.testTag("test_result_action_error"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        if (state.canRetryFollowUp) {
            OutlinedButton(
                onClick = onRetryFollowUp,
                enabled = !state.isCreatingFollowUp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("test_result_retry_follow_up"),
            ) { Text("Retry") }
        }
        if (state.canRetryActionContext) {
            OutlinedButton(
                onClick = onRetryActionContext,
                enabled = !state.isLoadingActionContext,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("test_result_retry_action_context"),
            ) { Text("Retry follow-up options") }
        }
    }
    PrimaryButton(
        text = if (state.isCreatingFollowUp) "Creating test…" else "Retest weak questions",
        onClick = onRetestWeak,
        enabled = state.isRetestWeakEnabled,
        modifier = Modifier.fillMaxWidth().testTag("test_result_retest_weak"),
    )
    when {
        state.isLoadingActionContext -> Text(
            "Checking active questions…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.actionContext?.activeReviewQuestionCount == 0 -> Text(
            "No active questions currently require review. Retest Weak is unavailable.",
            modifier = Modifier.testTag("test_result_retest_empty"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.actionContext == null -> Text(
            "Follow-up tests are unavailable because the saved source context could not be verified.",
            modifier = Modifier.testTag("test_result_origin_unavailable"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedButton(
        onClick = onNewTest,
        enabled = state.areFollowUpsEnabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("test_result_new_test"),
    ) {
        Text(if (state.isCreatingFollowUp) "Creating test…" else "New test")
    }
    OutlinedButton(
        onClick = onBack,
        enabled = state.canNavigateBack,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("test_result_back"),
    ) { Text("Back to origin") }
}

@Composable
private fun QuestionReportRow(
    questionNumber: Int,
    report: TestQuestionReport,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val statusLabel = if (report.answer.requiresReview == true) "Needs review" else "No review flag"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Question $questionNumber, $statusLabel, " +
                    "score ${formatScore(report.answer.score ?: 0.0)} of ${formatScore(report.answer.maxScore ?: 0.0)}"
            }
            .testTag("test_result_question_$questionNumber"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Question $questionNumber · $statusLabel", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${formatScore(report.answer.score ?: 0.0)} / ${formatScore(report.answer.maxScore ?: 0.0)} marks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(if (expanded) "Hide details" else "View details", style = MaterialTheme.typography.labelMedium)
            }
            Text(report.snapshot.questionTextSnapshot, style = MaterialTheme.typography.bodyMedium)
            if (expanded) ExpandedQuestion(report = report)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedQuestion(report: TestQuestionReport) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("test_result_question_detail_${report.snapshot.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EvidenceTextBlock(
            title = "Your answer",
            value = report.answer.answerText.ifBlank { "No answer entered." },
        )
        EvidenceTextBlock(title = "Reference answer", value = report.snapshot.referenceAnswerSnapshot)
        if (report.status == TestQuestionReportStatus.NOT_EVALUABLE) {
            Text(
                "This question had no keyword data, so keyword match could not be evaluated.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            KeywordEvidenceChips(title = "Matched keywords", values = report.matchedKeywords, matched = true)
            KeywordEvidenceChips(title = "Missing keywords", values = report.missingKeywords, matched = false)
        }
        Text(
            "Score ${formatScore(report.answer.score ?: 0.0)} / ${formatScore(report.answer.maxScore ?: 0.0)}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (report.answer.requiresReview == true) {
                "Review this question before your next test."
            } else {
                "No review flag was recorded for this answer. Test results do not determine mastery."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EvidenceTextBlock(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordEvidenceChips(
    title: String,
    values: List<String>,
    matched: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (values.isEmpty()) {
            Text("None", style = MaterialTheme.typography.bodySmall)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                values.forEach { value ->
                    Surface(
                        color = if (matched) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (matched) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(value, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

private fun formatScore(value: Double): String = BigDecimal.valueOf(value)
    .stripTrailingZeros()
    .toPlainString()

private fun formatPercent(value: Double): String = "${BigDecimal.valueOf(value)
    .setScale(1, RoundingMode.HALF_UP)
    .stripTrailingZeros()
    .toPlainString()}%"

private fun formatDuration(seconds: Long): String {
    val bounded = seconds.coerceAtLeast(0L)
    val hours = bounded / 3600
    val minutes = bounded % 3600 / 60
    val remainingSeconds = bounded % 60
    return when {
        hours > 0 -> String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds)
        minutes > 0 -> String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds)
        else -> "$remainingSeconds sec"
    }
}
