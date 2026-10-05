package com.studysmart.app.feature.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.model.TestQuestionSource
import com.studysmart.app.core.ui.ChapterCard
import com.studysmart.app.core.ui.EmptyState
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.MissingContentScreen
import com.studysmart.app.core.ui.PrimaryButton
import com.studysmart.app.core.ui.StudyTopBar
import com.studysmart.app.core.ui.StudyTopBarVariant
import com.studysmart.app.feature.academic.ChaptersUiState
import com.studysmart.app.feature.test.TestAttemptLauncherUiState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChaptersScreen(
    uiState: ChaptersUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPrepare: (String) -> Unit,
    onTest: (String) -> Unit,
    testState: TestAttemptLauncherUiState = TestAttemptLauncherUiState(),
    onRetryTest: () -> Unit = {},
    onOpenTestDefaults: () -> Unit = {},
    onDismissNoQuestions: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(20.dp),
) {
    if (!uiState.isLoading && uiState.error == null && uiState.isMissingContent) {
        MissingContentScreen(title = "Chapters", onBack = onBack)
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = uiState.content?.subject?.code ?: "Chapters",
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
                .testTag("chapters_screen"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.testTag("chapters_loading"))
                uiState.error != null -> ErrorState(
                    message = uiState.error,
                    onRetry = onRetry,
                    modifier = Modifier.testTag("chapters_error"),
                )
                uiState.content == null -> MissingContentScreen(title = "Chapters", onBack = onBack)
                uiState.content.chapters.isEmpty() -> EmptyState(
                    title = "Nothing here yet",
                    message = "No chapters are configured for this subject.",
                    modifier = Modifier.testTag("chapters_empty"),
                )
                else -> {
                    Text("Chapters", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${uiState.content.chapters.size} chapters",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (testState.error != null || testState.isMissingScope) {
                        ErrorState(
                            message = testState.error ?: "This chapter is no longer available.",
                            onRetry = onRetryTest,
                            modifier = Modifier.testTag("test_creation_error"),
                        )
                    }
                    uiState.content.chapters.forEach { chapter ->
                        ChapterCard(
                            number = chapter.chapterNumber.toString().padStart(2, '0'),
                            title = chapter.title,
                            subtitle = "${chapter.questionCount} questions",
                            progress = 0f,
                            onPrepare = { onPrepare(chapter.id) },
                            onTest = { onTest(chapter.id) },
                            prepareEnabled = chapter.questionCount > 0,
                            testEnabled = chapter.questionCount > 0 &&
                                !testState.isCreating &&
                                testState.attemptId == null &&
                                testState.error == null &&
                                !testState.isMissingScope,
                            modifier = Modifier.testTag("chapter_${chapter.id}"),
                        )
                    }
                }
            }
        }
    }
    NoTestQuestionsSheet(testState, onOpenTestDefaults, onDismissNoQuestions)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NoTestQuestionsSheet(
    testState: TestAttemptLauncherUiState,
    onOpenTestDefaults: () -> Unit,
    onDismissNoQuestions: () -> Unit,
) {
    if (testState.isNoQuestions) {
        ModalBottomSheet(
            onDismissRequest = onDismissNoQuestions,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .testTag("no_test_questions_sheet"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("No questions available", style = MaterialTheme.typography.titleLarge)
                Text(
                    "No questions in this chapter match the current source: " +
                        (testState.noQuestionsSource?.displayName ?: "Selected") + ".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PrimaryButton(
                    text = "Open Test Defaults",
                    onClick = onOpenTestDefaults,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = onDismissNoQuestions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Not now")
                }
                Text(
                    "No test attempt was created.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private val TestQuestionSource.displayName: String
    get() = when (this) {
        TestQuestionSource.ALL -> "All"
        TestQuestionSource.RANDOM -> "Random"
        TestQuestionSource.WEAK -> "Weak"
        TestQuestionSource.UNSEEN -> "Unseen"
        TestQuestionSource.IMPORTANT -> "Important"
    }
