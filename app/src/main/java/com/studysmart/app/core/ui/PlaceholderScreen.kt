package com.studysmart.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlaceholderScreen(
    title: String,
    supportingText: String = "This screen is part of the StudySmart navigation foundation.",
    onBack: (() -> Unit)? = null,
) {
    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = title,
                onBack = onBack,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "StudySmart navigation placeholder",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Text(
                text = supportingText,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
