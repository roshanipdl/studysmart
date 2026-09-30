package com.studysmart.app.feature.academic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.model.screenTitle
import com.studysmart.app.core.model.structureLabel
import com.studysmart.app.core.ui.AcademicPeriodCard
import com.studysmart.app.core.ui.EmptyState
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.MissingContentScreen
import com.studysmart.app.core.ui.StudyTopBar
import com.studysmart.app.core.ui.StudyTopBarVariant

@Composable
fun AcademicPeriodScreen(
    uiState: AcademicPeriodUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPeriodSelected: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onRetrySelection: () -> Unit = onRetry,
) {
    if (!uiState.isLoading && uiState.error == null && uiState.isMissingContent) {
        MissingContentScreen(title = "Academic Period", onBack = onBack)
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = uiState.content?.course?.code ?: "Academic Period",
                subtitle = uiState.content?.course?.structureType?.structureLabel(),
                variant = StudyTopBarVariant.BACK_SUBTITLE,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(contentPadding)
                .testTag("academic_period_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.isSelecting) {
                LoadingState(message = "Saving academic period", modifier = Modifier.testTag("academic_period_selection_saving"))
            }
            if (uiState.selectionError != null) {
                ErrorState(
                    message = uiState.selectionError,
                    onRetry = onRetrySelection,
                    modifier = Modifier.testTag("academic_period_selection_error"),
                )
            }
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.testTag("academic_period_loading"))
                uiState.error != null -> ErrorState(
                    message = uiState.error,
                    onRetry = onRetry,
                    modifier = Modifier.testTag("academic_period_error"),
                )
                uiState.content == null -> MissingContentScreen(
                    title = "Academic Period",
                    onBack = onBack,
                )
                uiState.content.periods.isEmpty() -> EmptyState(
                    title = "Nothing here yet",
                    message = "No academic periods are configured for this course.",
                    modifier = Modifier.testTag("academic_period_empty"),
                )
                else -> {
                    Text(
                        text = uiState.content.course.structureType.screenTitle(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Pick where you want to study next.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    uiState.content.periods.forEach { period ->
                        AcademicPeriodCard(
                            period = period,
                            onClick = { onPeriodSelected(period.id) },
                            enabled = !uiState.isSelecting && uiState.selectionError == null,
                            modifier = Modifier.testTag("period_${period.id}"),
                        )
                    }
                }
            }
        }
    }
}
