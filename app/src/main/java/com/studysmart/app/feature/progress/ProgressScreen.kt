package com.studysmart.app.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.unit.dp
import com.studysmart.app.R
import com.studysmart.app.core.ui.*
import java.util.Locale

@Composable
fun ProgressScreen(state: ProgressUiState, onChooseContext: () -> Unit, onBrowse: () -> Unit,
    onRetry: () -> Unit, onStartDue: () -> Unit, onRetryDue: () -> Unit, onSettings: () -> Unit) {
    Scaffold(topBar = { StudyTopBar(stringResource(R.string.progress_title), variant = StudyTopBarVariant.BACK_SUBTITLE,
        subtitle = state.context?.let { "${it.course.code} · ${it.period.name}" }, onSettings = onSettings) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("progress_screen"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.isLoading -> item { LoadingState() }
                state.hasError -> item { ErrorState(stringResource(R.string.progress_error), onRetry = onRetry) }
                state.context == null -> item {
                    EmptyState(stringResource(R.string.important_context_title), stringResource(R.string.progress_context_body))
                    PrimaryButton(stringResource(R.string.important_choose_context), onChooseContext)
                }
                else -> {
                    item { SecondaryButton("${state.context.course.code} · ${state.context.period.name}", onChooseContext,
                        enabled = !state.isStarting, modifier = Modifier.fillMaxWidth().testTag("progress_context")) }
                    val snapshot = state.snapshot
                    if (snapshot == null || !snapshot.metrics.hasActivity) {
                        item {
                            EmptyState(stringResource(R.string.progress_no_activity), stringResource(R.string.progress_no_activity_body))
                            PrimaryButton(stringResource(R.string.progress_browse), onBrowse, modifier = Modifier.testTag("progress_browse"))
                        }
                    } else {
                        item {
                            ProgressMetricCard(stringResource(R.string.progress_readiness), state.readiness?.let { "${it.displayPercent}%" }
                                ?: stringResource(R.string.progress_unavailable), state.readiness?.score?.div(100)?.toFloat())
                        }
                        item {
                            val count = snapshot.metrics.dailyRatedCount
                            ProgressMetricCard(stringResource(R.string.progress_daily_goal), stringResource(R.string.progress_daily_count, count, state.dailyGoal),
                                (count.toFloat() / state.dailyGoal).coerceIn(0f, 1f))
                            Text(stringResource(R.string.progress_cards_left, (state.dailyGoal - count).coerceAtLeast(0)))
                            SecondaryButton(stringResource(R.string.progress_edit_goal), onSettings)
                        }
                        item {
                            val metrics = snapshot.metrics
                            ProgressMetricCard(stringResource(R.string.progress_coverage), "${metrics.studiedCount} / ${metrics.activeCount}",
                                if (metrics.activeCount == 0) 0f else metrics.studiedCount.toFloat() / metrics.activeCount)
                            Text(stringResource(R.string.progress_not_studied, metrics.activeCount - metrics.studiedCount))
                        }
                        item {
                            ProgressMetricCard(stringResource(R.string.progress_review_health), stringResource(R.string.progress_review_counts,
                                state.reviews.dueCount, state.reviews.overdueCount, snapshot.metrics.learningCount))
                            val label = stringResource(R.string.progress_start_due)
                            val modifier = Modifier.fillMaxWidth().testTag("progress_start_due")
                            PrimaryButton(label, onStartDue, enabled = state.canStartDue,
                                modifier = if (state.canStartDue) modifier else modifier.clearAndSetSemantics { contentDescription = label; disabled() })
                            if (state.isStarting) Text(stringResource(R.string.progress_starting))
                            if (state.hasActionError) ErrorState(stringResource(R.string.progress_start_error), onRetry = onRetryDue)
                        }
                        item {
                            val test = snapshot.latestTest
                            ProgressMetricCard(stringResource(R.string.progress_test_performance), test?.let {
                                stringResource(R.string.progress_test_score, it.score, it.maxScore, it.weightedKeywordMatchPercent,
                                    String.format(Locale.ROOT, "%02d:%02d", it.timeTakenSeconds / 60, it.timeTakenSeconds % 60))
                            } ?: stringResource(R.string.progress_no_test))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressMetricCard(title: String, value: String, progress: Float? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
            if (progress != null) LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
