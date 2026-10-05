package com.studysmart.app.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class StudyTopBarVariant {
    HOME,
    BACK,
    BACK_SUBTITLE,
    TEST_TIMER,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    variant: StudyTopBarVariant = if (onBack == null) StudyTopBarVariant.HOME else StudyTopBarVariant.BACK,
    subtitle: String? = null,
    initials: String = "S",
    timerText: String? = null,
    onSearch: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    navigationContentDescription: String = "Back",
    backEnabled: Boolean = true,
    trailingContent: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (variant == StudyTopBarVariant.HOME || variant == StudyTopBarVariant.TEST_TIMER) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Text(
                            text = initials.take(1).uppercase(),
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        navigationIcon = {
            if (onBack != null && variant != StudyTopBarVariant.HOME && variant != StudyTopBarVariant.TEST_TIMER) {
                IconButton(onClick = onBack, enabled = backEnabled) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = navigationContentDescription,
                    )
                }
            }
        },
        actions = {
            if (variant == StudyTopBarVariant.TEST_TIMER && timerText != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = timerText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (onSearch != null) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search")
                }
            }
            if (onSettings != null) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
            if (variant == StudyTopBarVariant.TEST_TIMER && timerText == null) {
                Icon(Icons.Outlined.Timer, contentDescription = "Timer")
            }
            trailingContent()
        },
    )
}
