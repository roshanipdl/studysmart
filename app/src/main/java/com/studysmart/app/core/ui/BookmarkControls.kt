package com.studysmart.app.core.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.studysmart.app.R

@Composable
fun BookmarkControl(
    state: BookmarkUiState,
    questionId: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconOnly: Boolean = false,
) {
    val saved = questionId in state.savedIds
    val label = stringResource(if (saved) R.string.bookmark_remove else R.string.bookmark_save)
    if (iconOnly) {
        IconToggleButton(checked = saved, onCheckedChange = { onToggle() }, enabled = enabled && state.canChange && questionId !in state.unavailableIds,
            modifier = modifier.heightIn(min = 48.dp)) {
            Icon(if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = label)
        }
    } else {
        TextButton(onClick = onToggle, enabled = enabled && state.canChange && questionId !in state.unavailableIds, modifier = modifier.heightIn(min = 48.dp)) {
            Text(label)
        }
    }
}

@Composable
fun BookmarkStatus(state: BookmarkUiState, onRetry: () -> Unit) {
    when {
        state.hasLoadError || state.hasWriteError -> ErrorState(
            message = stringResource(R.string.bookmark_error), onRetry = onRetry, modifier = Modifier.testTag("bookmark_error"))
        state.isMissingQuestion -> Text(stringResource(R.string.bookmark_missing), modifier = Modifier.testTag("bookmark_missing"))
        state.isSaving -> Text(stringResource(R.string.bookmark_saving), modifier = Modifier.testTag("bookmark_saving"))
    }
}
