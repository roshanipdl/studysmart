package com.studysmart.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.model.AcademicPeriodBrowseModel
import com.studysmart.app.core.model.SubjectBrowseModel

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = Color(0xFFE6E6EC),
            disabledContentColor = Color(0xFF85869A),
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SmallActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = Color(0xFFE6E6EC),
            disabledContentColor = Color(0xFF85869A),
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

enum class RatingButtonVariant(val label: String) {
    AGAIN("Again"),
    HARD("Hard"),
    GOOD("Good"),
    EASY("Easy"),
}

@Composable
fun RatingButton(
    rating: RatingButtonVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val semanticColors = LocalStudySmartSemanticColors.current
    val (container, content) = when (rating) {
        RatingButtonVariant.AGAIN -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.error
        RatingButtonVariant.HARD -> semanticColors.warningContainer to semanticColors.warning
        RatingButtonVariant.GOOD -> semanticColors.successContainer to semanticColors.success
        RatingButtonVariant.EASY -> semanticColors.infoContainer to semanticColors.info
    }
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = Color(0xFFE6E6EC),
            disabledContentColor = Color(0xFF85869A),
        ),
    ) {
        Text(rating.label, style = MaterialTheme.typography.labelLarge)
    }
}

enum class StatusPillVariant(val label: String) {
    DUE("Due"),
    NEEDS_REVIEW("Needs review"),
    MASTERED("Mastered"),
    GOOD("Good"),
    EASY("Easy"),
    HARD("Hard"),
    AGAIN("Again"),
}

enum class CourseCardVariant {
    COMPACT,
    FULL_WIDTH,
}

@Composable
fun StatusPill(
    status: StatusPillVariant,
    modifier: Modifier = Modifier,
) {
    val semanticColors = LocalStudySmartSemanticColors.current
    val (container, content) = when (status) {
        StatusPillVariant.DUE, StatusPillVariant.HARD ->
            semanticColors.warningContainer to semanticColors.warning
        StatusPillVariant.NEEDS_REVIEW, StatusPillVariant.AGAIN ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        StatusPillVariant.MASTERED, StatusPillVariant.GOOD ->
            semanticColors.successContainer to semanticColors.success
        StatusPillVariant.EASY -> semanticColors.infoContainer to semanticColors.info
    }
    Surface(
        modifier = modifier,
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = status.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun CourseCard(
    code: String,
    name: String,
    structureLabel: String,
    meta: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: CourseCardVariant = CourseCardVariant.COMPACT,
) {
    val selectionBorder = if (selected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else {
        null
    }
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = if (selected) {
                    LocalStudySmartSemanticColors.current.surfaceSelected
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(code, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusPillLabel(structureLabel)
            }
        }
    }
    val clickAction = onClick?.takeIf { enabled }
    if (clickAction == null) {
        Card(
            modifier = modifier.heightIn(min = if (variant == CourseCardVariant.FULL_WIDTH) 80.dp else 96.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = selectionBorder,
        ) {
            content()
        }
    } else {
        Card(
            onClick = clickAction,
            modifier = modifier.heightIn(min = if (variant == CourseCardVariant.FULL_WIDTH) 80.dp else 96.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = selectionBorder,
        ) {
            content()
        }
    }
}

@Composable
private fun StatusPillLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
fun ChapterCard(
    number: String,
    title: String,
    subtitle: String,
    progress: Float,
    onPrepare: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prepareEnabled: Boolean = enabled,
    testEnabled: Boolean = enabled,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = LocalStudySmartSemanticColors.current.track,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallActionButton(text = "Prepare", onClick = onPrepare, enabled = prepareEnabled)
                SecondaryButton(text = "Test", onClick = onTest, enabled = testEnabled)
            }
        }
    }
}

@Composable
fun AcademicPeriodCard(
    period: AcademicPeriodBrowseModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = period.periodNumber.toString().padStart(2, '0'),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(period.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${period.subjectCount} subjects",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SubjectCard(
    subject: SubjectBrowseModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = subject.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(subject.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${subject.chapterCount} chapters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProgressCard(
    title: String,
    value: String,
    detail: String,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onPrimaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = MaterialTheme.colorScheme.primaryContainer, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(value, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineSmall)
                Text(detail, color = MaterialTheme.colorScheme.primaryContainer, style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.primaryContainer,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
fun ResultMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = "Loading",
) {
    StatePanel(modifier = modifier, title = message) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

@Composable
fun EmptyState(
    title: String = "Nothing here yet",
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    StatePanel(modifier = modifier, title = title, message = message) {
        if (retryLabel != null && onRetry != null) {
            SmallActionButton(text = retryLabel, onClick = onRetry)
        }
    }
}

@Composable
fun OfflineState(
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    StatePanel(
        modifier = modifier,
        title = "You are offline",
        message = "Cached content remains available on this device.",
    ) {
        if (onRetry != null) SmallActionButton(text = "Retry", onClick = onRetry)
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    StatePanel(modifier = modifier, title = "Something went wrong", message = message) {
        if (onRetry != null) SmallActionButton(text = "Retry", onClick = onRetry)
    }
}

@Composable
private fun StatePanel(
    modifier: Modifier,
    title: String,
    message: String? = null,
    content: @Composable () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
