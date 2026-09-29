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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.ui.EmptyState
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.PrimaryButton
import com.studysmart.app.core.ui.ResultMetric
import com.studysmart.app.core.ui.StudyTopBar
import com.studysmart.app.core.ui.StudyTopBarVariant

@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onRetry: () -> Unit,
    onStartDueReview: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = "Review",
                subtitle = "Keep your learning fresh",
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
            when {
                state.isLoading -> LoadingState(
                    message = "Loading due reviews",
                    modifier = Modifier.testTag("review_loading"),
                )
                state.error != null -> ErrorState(
                    message = state.error,
                    onRetry = onRetry,
                    modifier = Modifier.testTag("review_error"),
                )
                state.isContextRequired -> EmptyState(
                    title = "Choose your study context",
                    message = "Select a course and academic period before opening Review.",
                    modifier = Modifier.testTag("review_context_required"),
                )
                state.isNoDue -> EmptyState(
                    title = "No due reviews",
                    message = "There are no scheduled questions to review in this context.",
                    modifier = Modifier.testTag("review_no_due"),
                )
                else -> DueReviewContent(
                    state = state,
                    onStartDueReview = onStartDueReview,
                )
            }
        }
    }
}

@Composable
private fun DueReviewContent(
    state: ReviewUiState,
    onStartDueReview: () -> Unit,
) {
    Text(
        text = "Due reviews",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "Review the questions scheduled for your selected course and period.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    if (state.isStarting) {
        LoadingState(
            message = "Preparing due review",
            modifier = Modifier.testTag("review_starting"),
        )
    }
}
