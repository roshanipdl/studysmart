package com.studysmart.app.feature.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
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
import com.studysmart.app.core.ui.LocalStudySmartSemanticColors
import com.studysmart.app.core.model.AcademicPeriodType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRetry: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onCourseSelected: (String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onRetrySelection: () -> Unit = onRetry,
    onDueReview: () -> Unit = {},
    onSaved: () -> Unit = {},
) {
    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Hello,", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(uiState.displayName, style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            }
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
            Surface(color = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("YOUR STUDY RHYTHM", color = MaterialTheme.colorScheme.primaryContainer, style = MaterialTheme.typography.labelSmall)
                    Text("Small steps.\nBig progress.", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineSmall)
                }
            }
            listOfNotNull(uiState.profileError, uiState.historyError, uiState.actionsError).forEach {
                ErrorState(message = it, onRetry = onRetry)
            }
            if (uiState.isSelecting) {
                LoadingState(message = "Saving course", modifier = Modifier.testTag("home_selection_saving"))
            }
            if (uiState.selectionError != null) {
                ErrorState(
                    message = uiState.selectionError,
                    onRetry = onRetrySelection,
                    modifier = Modifier.testTag("home_selection_error"),
                )
            }
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
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        orderedCourses.forEach { course ->
                            val isSelected = course.id == uiState.selectedCourseId
                            HomeTile(course.code, "${course.periodCount} ${if (course.structureType == AcademicPeriodType.YEAR) "years" else "semesters"}",
                                !uiState.isSelecting && uiState.selectionError == null, { onCourseSelected(course.id) },
                                Modifier.width(96.dp).testTag("course_${course.id}").semantics { selected = isSelected }, isSelected)
                        }
                    }
                }
            }
            uiState.recentContext?.let { recent ->
                Text("Continue studying", style = MaterialTheme.typography.titleMedium)
                Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("home_recent")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(recent.subjectName, style = MaterialTheme.typography.titleMedium)
                        Text("${recent.courseCode} · ${recent.periodName}")
                        val progress = if (recent.total > 0) recent.studied.toFloat() / recent.total else 0f
                        Text("${(progress * 100).toInt()}% studied")
                        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, trackColor = LocalStudySmartSemanticColors.current.track, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            val actionsReady = !uiState.isLoading && !uiState.isSelecting && uiState.error == null && uiState.actionsError == null
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeTile("Due review", "${uiState.dueCount} due", uiState.dueCount > 0 && actionsReady, onDueReview, Modifier.weight(1f).testTag("home_due"))
                HomeTile("Saved", "${uiState.savedCount} saved", uiState.savedCount > 0 && actionsReady, onSaved, Modifier.weight(1f).testTag("home_saved"))
            }
        }
    }
}

@Composable
private fun HomeTile(title: String, detail: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier, selected: Boolean = false) {
    Surface(shape = RoundedCornerShape(14.dp), color = if (selected) LocalStudySmartSemanticColors.current.surfaceSelected else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.heightIn(min = 74.dp).then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier.semantics { disabled() })) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant else if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
