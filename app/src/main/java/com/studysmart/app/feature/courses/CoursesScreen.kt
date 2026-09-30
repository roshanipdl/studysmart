package com.studysmart.app.feature.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.model.structureLabel
import com.studysmart.app.core.ui.CourseCard
import com.studysmart.app.core.ui.CourseCardVariant
import com.studysmart.app.core.ui.EmptyState
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.StudyTopBar
import com.studysmart.app.core.ui.StudyTopBarVariant

@Composable
fun CoursesScreen(
    uiState: CoursesUiState,
    onRetry: () -> Unit,
    onCourseSelected: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onRetrySelection: () -> Unit = onRetry,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = "Courses",
                subtitle = "Choose a course to start studying",
                variant = StudyTopBarVariant.BACK,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(contentPadding)
                .testTag("courses_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.isSelecting) {
                LoadingState(message = "Saving course", modifier = Modifier.testTag("courses_selection_saving"))
            }
            if (uiState.selectionError != null) {
                ErrorState(
                    message = uiState.selectionError,
                    onRetry = onRetrySelection,
                    modifier = Modifier.testTag("courses_selection_error"),
                )
            }
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.testTag("courses_loading"))
                uiState.error != null -> ErrorState(
                    message = uiState.error,
                    onRetry = onRetry,
                    modifier = Modifier.testTag("courses_error"),
                )
                uiState.courses.isEmpty() -> EmptyState(
                    title = "Nothing here yet",
                    message = "No courses are configured on this device.",
                    modifier = Modifier.testTag("courses_empty"),
                )
                else -> {
                    val orderedCourses = uiState.courses.sortedByDescending {
                        it.id == uiState.selectedCourseId
                    }
                    orderedCourses.forEach { course ->
                        CourseCard(
                            code = course.code,
                            name = course.shortName,
                            structureLabel = course.structureType.structureLabel(),
                            meta = "${course.periodCount} academic periods",
                            selected = course.id == uiState.selectedCourseId,
                            onClick = { onCourseSelected(course.id) },
                            enabled = !uiState.isSelecting && uiState.selectionError == null,
                            variant = CourseCardVariant.FULL_WIDTH,
                            modifier = Modifier.testTag("course_${course.id}"),
                        )
                    }
                }
            }
        }
    }
}
