package com.studysmart.app.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MissingContentScreen(
    title: String,
    message: String = "This content is no longer available.",
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(20.dp),
) {
    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        topBar = { StudyTopBar(title = title, onBack = onBack) },
    ) { padding ->
        ErrorState(
            message = message,
            onRetry = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(contentPadding),
        )
    }
}
