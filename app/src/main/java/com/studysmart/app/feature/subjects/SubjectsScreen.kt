package com.studysmart.app.feature.subjects

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
import com.studysmart.app.core.ui.EmptyState
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.MissingContentScreen
import com.studysmart.app.core.ui.StudyTopBar
import com.studysmart.app.core.ui.StudyTopBarVariant
import com.studysmart.app.core.ui.SubjectCard
import com.studysmart.app.feature.academic.SubjectsUiState

@Composable
fun SubjectsScreen(
    uiState: SubjectsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSubjectSelected: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(20.dp),
) {
    if (!uiState.isLoading && uiState.error == null && uiState.isMissingContent) {
        MissingContentScreen(title = "Subjects", onBack = onBack)
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = "Subjects",
                subtitle = uiState.content?.let { "${it.course.code} · ${it.period.name}" },
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
                .testTag("subjects_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.testTag("subjects_loading"))
                uiState.error != null -> ErrorState(
                    message = uiState.error,
                    onRetry = onRetry,
                    modifier = Modifier.testTag("subjects_error"),
                )
                uiState.content == null -> MissingContentScreen(title = "Subjects", onBack = onBack)
                uiState.content.subjects.isEmpty() -> EmptyState(
                    title = "Nothing here yet",
                    message = "No subjects are configured for this academic period.",
                    modifier = Modifier.testTag("subjects_empty"),
                )
                else -> {
                    Text("Select subject", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Choose a subject to browse chapters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    uiState.content.subjects.forEach { subject ->
                        SubjectCard(
                            subject = subject,
                            onClick = { onSubjectSelected(subject.id) },
                            modifier = Modifier.testTag("subject_${subject.id}"),
                        )
                    }
                }
            }
        }
    }
}
