package com.studysmart.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.studysmart.app.feature.readiness.ReadinessEstimate

@Composable
fun ReadinessEstimateCard(
    estimate: ReadinessEstimate?,
    isLoading: Boolean,
    error: String?,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    testTag: String = "readiness_estimate",
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics {
                contentDescription = estimate?.let {
                    "Exam readiness estimate: ${it.displayPercent} percent"
                } ?: "Exam readiness estimate"
            },
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Exam readiness estimate", style = MaterialTheme.typography.labelLarge)
            when {
                isLoading -> Text("Calculating your estimate…", style = MaterialTheme.typography.bodyMedium)
                error != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Readiness estimate unavailable.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (onRetry != null) {
                            TextButton(
                                onClick = onRetry,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                estimate == null -> Text(
                    "No active questions are available in this scope.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> {
                    Text(
                        text = "${estimate.displayPercent}%",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Estimate from learning state, submitted tests, rating coverage, and review freshness.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
