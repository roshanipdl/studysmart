package com.studysmart.app.feature.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.studysmart.app.core.ui.CourseCard
import com.studysmart.app.core.model.structureLabel
import com.studysmart.app.core.ui.EmptyState
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.StudyTopBar
import com.studysmart.app.core.ui.StudyTopBarVariant

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRetry: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onCourseSelected: (String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(20.dp),
) {
    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = "Home",
                variant = StudyTopBarVariant.HOME,
                subtitle = "Ready for a focused session?",
                onSearch = onSearch,
                onSettings = onSettings,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(contentPadding)
                .testTag("home_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                uiState.isLoading -> LoadingState(
                    modifier = Modifier.testTag("home_loading"),
                )
                uiState.error != null -> ErrorState(
                    message = uiState.error,
                    onRetry = onRetry,
                    modifier = Modifier.testTag("home_error"),
                )
                uiState.courses.isEmpty() -> EmptyState(
                    title = "No courses configured",
                    message = "Courses will appear here when curriculum is available on this device.",
                )
                else -> {
                    val orderedCourses = uiState.courses.sortedByDescending {
                        it.id == uiState.selectedCourseId
                    }
                    Text(
                        text = "Choose Your Course",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    orderedCourses.forEach { course ->
                        CourseCard(
                            code = course.code,
                            name = course.shortName,
                            structureLabel = course.structureType.structureLabel(),
                            meta = "${course.periodCount} academic periods",
                            selected = course.id == uiState.selectedCourseId,
                            onClick = { onCourseSelected(course.id) },
                            modifier = Modifier.testTag("course_${course.id}"),
                        )
                    }
                }
            }
        }
    }
}
