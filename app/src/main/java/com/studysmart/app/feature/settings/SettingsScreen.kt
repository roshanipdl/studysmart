package com.studysmart.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.ui.PrimaryButton
import com.studysmart.app.core.ui.StudyTopBar

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTestDefaults: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(20.dp),
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { StudyTopBar(title = "Settings", onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(contentPadding)
                .testTag("settings_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Preferences", style = MaterialTheme.typography.headlineSmall)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Testing", style = MaterialTheme.typography.titleMedium)
                    Text("Configure the defaults used when a test starts.", style = MaterialTheme.typography.bodyMedium)
                    PrimaryButton(
                        text = "Test defaults",
                        onClick = onOpenTestDefaults,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
