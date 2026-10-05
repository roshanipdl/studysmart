package com.studysmart.app.feature.preparation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The frame owns padding/header; this bounded body alone owns reading scroll. */
@Composable
internal fun PreparationScrollableContent(
    scrollState: ScrollState,
    enabled: Boolean,
    settled: Boolean,
    isAnswer: Boolean,
    onMeasured: (PreparationBodyMeasurement) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var viewportHeight by remember { mutableIntStateOf(0) }
    val latestMeasured by rememberUpdatedState(onMeasured)
    val measurement = PreparationBodyMeasurement(viewportHeight, scrollState.maxValue)
    LaunchedEffect(measurement, settled) {
        latestMeasured(if (settled) measurement else PreparationBodyMeasurement())
    }
    val track = MaterialTheme.colorScheme.outlineVariant
    val thumb = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.fillMaxWidth().clipToBounds().onSizeChanged { viewportHeight = it.height }
        .testTag(if (isAnswer) "preparation_answer_body" else "preparation_question_body")
        .semantics { contentDescription = if (isAnswer) "Answer content" else "Question content" }) {
        Column(Modifier.fillMaxWidth().verticalScroll(scrollState, enabled = enabled)
            .testTag("preparation_content_scroll").absolutePadding(right = 12.dp)) { content() }
        if (measurement.overflows) {
            Canvas(Modifier.matchParentSize().testTag("preparation_content_scrollbar")) {
                val inset = 2.dp.toPx()
                val height = (size.height - inset * 2).coerceAtLeast(0f)
                val total = size.height + measurement.maxScroll.toFloat()
                val thumbHeight = (height * size.height / total).coerceIn(minOf(24.dp.toPx(), height), height)
                val fraction = (scrollState.value.toFloat() / measurement.maxScroll).coerceIn(0f, 1f)
                drawRect(track, Offset(size.width - 4.dp.toPx(), inset), Size(2.dp.toPx(), height))
                drawRect(thumb, Offset(size.width - 4.5.dp.toPx(), inset + (height - thumbHeight) * fraction),
                    Size(3.dp.toPx(), thumbHeight))
            }
        }
    }
}
