package com.studysmart.app.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.studysmart.app.core.ui.LocalStudySmartSemanticColors
import com.studysmart.app.core.ui.EmptyState
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.PrimaryButton
import com.studysmart.app.core.ui.ReadinessEstimateCard
import com.studysmart.app.core.ui.ResultMetric
import com.studysmart.app.core.ui.StudyTopBar
import com.studysmart.app.core.ui.StudyTopBarVariant

@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onRetry: () -> Unit,
    onStartDueReview: () -> Unit,
    onChangeContext: () -> Unit = {},
    onOpenFilter: (ReviewFilter) -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = "Review",
                subtitle = null,
                variant = StudyTopBarVariant.HOME,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(20.dp)
                .testTag("review_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.contextLabel.ifBlank { "Choose your study context" }, modifier = Modifier.weight(1f))
                TextButton(onClick = onChangeContext) { Text(if (state.isContextRequired) "Choose" else "Change") }
            }
            when {
                state.isLoading -> LoadingState(
                    message = "Loading due reviews",
                    modifier = Modifier.testTag("review_loading"),
                )
                state.displayError != null -> ErrorState(
                    message = state.displayError!!,
                    onRetry = onRetry,
                    modifier = Modifier.testTag("review_error"),
                )
                state.isContextRequired -> EmptyState(
                    title = "Choose your study context",
                    message = "Select a course and academic period before opening Review.",
                    modifier = Modifier.testTag("review_context_required"),
                )
                else -> DueReviewContent(
                    state = state,
                    onRetryReadiness = onRetry,
                    onStartDueReview = onStartDueReview,
                    onOpenFilter = onOpenFilter,
                )
            }
        }
    }
}

@Composable
private fun DueReviewContent(
    state: ReviewUiState,
    onRetryReadiness: () -> Unit,
    onStartDueReview: () -> Unit,
    onOpenFilter: (ReviewFilter) -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
        text = "Due now",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "Review the questions scheduled for your selected course and period.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.isNoDue) {
        EmptyState(
            title = "Nothing due right now",
            message = "Your next review will appear here when it is scheduled.",
            modifier = Modifier.testTag("review_no_due"),
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ResultMetric(
                value = state.dueCount.toString(),
                label = "Due",
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_due_count"),
            )
            ResultMetric(
                value = state.overdueCount.toString(),
                label = "Overdue",
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_overdue_count"),
            )
        }
        PrimaryButton(
            text = if (state.isStarting) "Starting review..." else "Start Due Review",
            onClick = onStartDueReview,
            enabled = !state.isStarting,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("review_start_due"),
        )
    }
    }
    }
    Text("Review by status", style = MaterialTheme.typography.titleLarge)
    ReviewFilter.entries.forEach { filter ->
        Card(onClick = { onOpenFilter(filter) }, shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("review_${filter.routeValue}")) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(filter.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(state.statusPools[filter].orEmpty().size.toString(), fontWeight = FontWeight.Bold,
                    color = when (filter) {
                        ReviewFilter.NEEDS_IMPROVEMENT -> MaterialTheme.colorScheme.error
                        ReviewFilter.NOT_STUDIED -> MaterialTheme.colorScheme.onSurfaceVariant
                        ReviewFilter.GOOD -> LocalStudySmartSemanticColors.current.success
                        ReviewFilter.MASTERED -> LocalStudySmartSemanticColors.current.info
                    })
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    ReadinessEstimateCard(
        estimate = state.readiness,
        isLoading = state.isReadinessLoading,
        error = state.readinessError,
        onRetry = onRetryReadiness,
        testTag = "review_readiness",
    )
    if (state.isStarting) {
        LoadingState(
            message = "Preparing due review",
            modifier = Modifier.testTag("review_starting"),
        )
    }
}
