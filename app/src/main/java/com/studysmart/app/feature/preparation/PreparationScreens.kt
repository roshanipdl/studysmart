package com.studysmart.app.feature.preparation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.delay
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.ui.EmptyState
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.MissingContentScreen
import com.studysmart.app.core.ui.PrimaryButton
import com.studysmart.app.core.ui.SecondaryButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.studysmart.app.core.ui.ReadinessEstimateCard
import com.studysmart.app.core.ui.ResultMetric
import com.studysmart.app.core.ui.StatusPill
import com.studysmart.app.core.ui.StatusPillVariant
import com.studysmart.app.core.ui.StudyTopBar
import com.studysmart.app.core.ui.StudyTopBarVariant

@Composable
fun PreparationEntryScreen(
    title: String,
    state: PreparationEntryUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    if (!state.isLoading && state.isMissingScope) {
        MissingContentScreen(title = title, onBack = onBack)
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = title,
                variant = StudyTopBarVariant.BACK,
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .testTag("preparation_entry_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.isLoading || state.isCreating -> LoadingState(
                    message = "Preparing your cards",
                    modifier = Modifier.testTag("preparation_entry_loading"),
                )
                state.error != null -> ErrorState(
                    message = state.error,
                    onRetry = onRetry,
                    modifier = Modifier.testTag("preparation_entry_error"),
                )
                state.isNoQuestions -> EmptyState(
                    title = "No questions available",
                    message = "There are no active questions in this preparation scope.",
                    modifier = Modifier.testTag("preparation_entry_empty"),
                )
                else -> LoadingState(
                    message = "Opening preparation",
                    modifier = Modifier.testTag("preparation_entry_loading"),
                )
            }
        }
    }
}

@Composable
fun PreparationSessionScreen(
    state: PreparationSessionUiState,
    onReveal: () -> Unit,
    onRate: (Rating) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onDiscard: () -> Unit,
    onCancelDiscard: () -> Unit,
    onLoadRetry: () -> Unit,
    onBackFallback: () -> Unit,
    onOpenSummary: () -> Unit,
    onRatingAnimationFinished: (String) -> Unit = {},
) {
    if (!state.isLoading && state.isMissingContent) {
        MissingContentScreen(title = "Preparation", onBack = onBackFallback)
        return
    }
    if (!state.isLoading && state.error != null) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                StudyTopBar(
                    title = "Preparation",
                    variant = StudyTopBarVariant.BACK,
                    onBack = onBackFallback,
                )
            },
        ) { padding ->
            ErrorState(
                message = state.error,
                onRetry = onLoadRetry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .testTag("preparation_session_error"),
            )
        }
        return
    }
    if (!state.isLoading && state.isStaleSession) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                StudyTopBar(
                    title = "Preparation",
                    variant = StudyTopBarVariant.BACK,
                    onBack = onBackFallback,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .testTag("preparation_session_stale"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "This preparation session is no longer active.",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "You can open its saved summary or return to the previous study screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.session?.status == com.studysmart.app.core.model.PreparationSessionStatus.COMPLETED) {
                    PrimaryButton(
                        text = "Open summary",
                        onClick = onOpenSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preparation_stale_summary"),
                    )
                }
                TextButton(
                    onClick = onBackFallback,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Back")
                }
            }
        }
        return
    }

    val session = state.session
    val item = state.ratingFeedback?.item ?: session?.currentItem
    var entranceDirection by remember { mutableStateOf<RatingSwipeDirection?>(null) }
    val currentCardKey = item?.cardKey()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = session?.headerTitle() ?: "Preparation",
                subtitle = session?.headerSubtitle(),
                variant = StudyTopBarVariant.BACK_SUBTITLE,
                onBack = onClose,
                navigationContentDescription = "Close",
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("preparation_session_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading || session == null || item == null) {
                LoadingState(
                    message = "Loading card",
                    modifier = Modifier.testTag("preparation_session_loading"),
                )
            } else {
                val interactionEnabled = !state.isSaving && state.mutationError == null &&
                    state.ratingFeedback == null && !state.showDiscardConfirmation && !state.completionReady
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("preparation_study_viewport"),
                ) {
                    val viewportHeight = constraints.maxHeight
                    val scrollState = androidx.compose.runtime.key(session.id, currentCardKey) {
                        rememberScrollState()
                    }
                    val outerScrollState = androidx.compose.runtime.key(session.id, currentCardKey) { rememberScrollState() }
                    val padWidth = preparationRatingPadWidth()
                            androidx.compose.runtime.key(session.id, currentCardKey) {
                                PreparationSwipeCard(
                                    enabled = interactionEnabled && item.isRevealed,
                                    feedback = state.ratingFeedback,
                                    viewportHeight = viewportHeight,
                                    entranceDirection = entranceDirection,
                                    onRate = onRate,
                                    onExitFinished = { id ->
                                        entranceDirection = state.ratingFeedback?.rating?.swipeDirection()
                                        onRatingAnimationFinished(id)
                                    },
                                    onBusyChanged = {},
                                    ratingPad = { modifier, ready ->
                                        PreparationRatingPad(
                                            enabled = interactionEnabled && item.isRevealed && ready,
                                            onRate = onRate,
                                            modifier = modifier.testTag("preparation_rating_pad"),
                                        )
                                    },
                                    layout = { modifier, card, pad ->
                                        PreparationStudyLayout(
                                            viewportHeight = viewportHeight,
                                            padWidth = padWidth,
                                            cardScroll = scrollState,
                                            outerScroll = outerScrollState,
                                            modifier = modifier.fillMaxWidth(),
                                            card = card,
                                            pad = pad,
                                            controls = {
                                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    PreparationProgress(projectPreparationProgress(session))
                                                    Text(
                                                        if (item.isRevealed) "Rate after viewing the answer; swipe the header or rating pad."
                                                        else "Think first, then tap the card to view the answer.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    state.mutationError?.let { message ->
                                                        ErrorState(message = message, onRetry = onRetry,
                                                            modifier = Modifier.testTag("preparation_save_error"))
                                                    }
                                                    if (state.isSaving) LoadingState(message = "Saving",
                                                        modifier = Modifier.testTag("preparation_saving"))
                                                }
                                            },
                                        )
                                    },
                                ) { swipeModifier, tint, onHeaderBounds, onFlipRunning, cardReady ->
                                    PreparationFlashcard(
                                        item = item,
                                        sessionId = session.id,
                                        topicLabel = item.topicLabel(session),
                                        onReveal = onReveal,
                                        enabled = interactionEnabled && cardReady,
                                        onFaceChanged = { scrollState.scrollTo(0); outerScrollState.scrollTo(0) },
                                        onHeaderBounds = onHeaderBounds,
                                        onFlipRunning = onFlipRunning,
                                        containerColor = tint,
                                        modifier = swipeModifier.testTag("preparation_card"),
                                    )
                                }
                            }
                }
            }
        }
    }

    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelDiscard,
            title = { Text("Discard this session?") },
            text = {
                Column {
                    Text("Committed review history will remain, but this active session will be deleted.")
                    state.discardError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("preparation_discard_error"))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDiscard,
                    enabled = !state.isSaving,
                    modifier = Modifier.testTag("preparation_discard"),
                ) { Text(if (state.discardError == null) "Discard session" else "Retry discard") }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancelDiscard,
                    enabled = !state.isSaving,
                    modifier = Modifier.testTag("preparation_cancel_discard"),
                ) { Text("Cancel") }
            },
        )
    }
}

private data class PreparationCardKey(
    val questionId: String,
    val presentationCount: Int,
)

private fun PreparationItemModel.cardKey(): PreparationCardKey = PreparationCardKey(
    questionId = questionId,
    presentationCount = presentationCount,
)

/** Anchor the card's lower edge; overflow scrolls without hiding study content or controls. */
@Composable
private fun PreparationStudyLayout(
    viewportHeight: Int,
    padWidth: androidx.compose.ui.unit.Dp,
    cardScroll: androidx.compose.foundation.ScrollState,
    outerScroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    card: @Composable () -> Unit,
    pad: @Composable () -> Unit,
    controls: @Composable () -> Unit,
) {
    var overflow by remember { mutableStateOf(false) }
    SubcomposeLayout(modifier.verticalScroll(outerScroll, enabled = overflow)
        .then(if (overflow) Modifier.testTag("preparation_study_scroll") else Modifier)) { constraints ->
        val gap = 8.dp.roundToPx()
        val controlsPlaceable = subcompose("controls", controls).single().measure(Constraints.fixedWidth(constraints.maxWidth))
        val padPixels = padWidth.roundToPx().coerceAtMost((constraints.maxWidth - gap - 1).coerceAtLeast(1))
        val padPlaceable = subcompose("pad", pad).single().measure(Constraints.fixedWidth(padPixels))
        val available = (viewportHeight - controlsPlaceable.height - gap).coerceAtLeast(0)
        val fallback = available < maxOf(padPlaceable.height, 120.dp.roundToPx())
        overflow = fallback
        val cardWidth = (constraints.maxWidth - padPixels - gap).coerceAtLeast(1)
        val cardPlaceable = subcompose("card") {
            Box(Modifier.clipToBounds().testTag("preparation_left_viewport")) {
                Layout(
                    modifier = if (fallback) Modifier else Modifier.verticalScroll(cardScroll)
                        .testTag("preparation_study_scroll"),
                    content = card,
                ) { measurables, innerConstraints ->
                    val measured = measurables.single().measure(Constraints.fixedWidth(innerConstraints.maxWidth))
                    val top = if (fallback) 0 else ((available * 0.75f).toInt() - measured.height).coerceAtLeast(0)
                    layout(innerConstraints.maxWidth, maxOf(measured.height + top, if (fallback) 0 else available)) {
                        measured.place(0, top)
                    }
                }
            }
        }.single().measure(if (fallback) Constraints.fixedWidth(cardWidth) else Constraints.fixed(cardWidth, available))
        val rowHeight = maxOf(cardPlaceable.height, padPlaceable.height)
        val height = maxOf(viewportHeight, controlsPlaceable.height + gap + rowHeight)
        layout(constraints.maxWidth, height) {
            controlsPlaceable.place(0, 0)
            cardPlaceable.place(0, controlsPlaceable.height + gap)
            padPlaceable.place(cardWidth + gap, controlsPlaceable.height + gap +
                if (fallback) 0 else ((available - padPlaceable.height) / 2).coerceAtLeast(0))
        }
    }
}

@Composable
private fun PreparationProgress(progress: PreparationProgressProjection) {
    Text(
        "${progress.uniqueReviewed} / ${progress.uniqueTotal} unique reviewed · ${progress.totalRatings} ratings",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("preparation_card_position"),
    )
    val completion by animateFloatAsState(progress.completion, label = "session_completion")
    LinearProgressIndicator(
        progress = { completion },
        modifier = Modifier.fillMaxWidth().height(8.dp).testTag("preparation_progress"),
    )
    val colors = Rating.entries.map { preparationRatingColor(it) }
    val fractions = Rating.entries.map { rating ->
        animateFloatAsState(
            if (progress.totalRatings == 0) 0f else progress.ratingCounts.getValue(rating).toFloat() / progress.totalRatings,
            label = "rating_distribution_${rating.name}",
        ).value
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
            .testTag("preparation_rating_distribution").semantics {
                contentDescription = Rating.entries.joinToString(", ") {
                    "${it.name.lowercase()}: ${progress.ratingCounts.getValue(it)}"
                }
            },
    ) {
        drawRect(track)
        val sum = fractions.sum()
        var left = 0f
        if (sum > 0f) fractions.forEachIndexed { index, value ->
            val width = size.width * value / sum
            drawRect(colors[index], topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                size = androidx.compose.ui.geometry.Size(width, size.height))
            left += width
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Rating.entries.forEachIndexed { index, rating ->
            val count = progress.ratingCounts.getValue(rating)
            var previous by remember { mutableStateOf(count) }
            var highlighted by remember { mutableStateOf(false) }
            LaunchedEffect(count) {
                if (count > previous) { highlighted = true; delay(300); highlighted = false }
                previous = count
            }
            Surface(
                modifier = Modifier.weight(1f),
                color = if (highlighted) colors[index] else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text("$count", Modifier.testTag("preparation_count_${rating.name.lowercase()}")
                    .semantics { contentDescription = "${rating.name.lowercase()}, $count committed ratings" },
                    style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PreparationCardHeader(label: String, modifier: Modifier = Modifier, onBounds: (PreparationGestureBounds) -> Unit) {
    Column(Modifier.onGloballyPositioned {
        val bounds = it.boundsInRoot()
        onBounds(PreparationGestureBounds(bounds.left, bounds.top, bounds.right, bounds.bottom))
    }.testTag("preparation_swipe_grip")) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Tap to flip",
                modifier = Modifier.weight(1f).testTag("preparation_flip_hint"),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "↕ Swipe here to rate",
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreparationFlashcard(
    item: PreparationItemModel,
    sessionId: String,
    topicLabel: String,
    onReveal: () -> Unit,
    enabled: Boolean,
    onFaceChanged: suspend () -> Unit,
    onHeaderBounds: (PreparationGestureBounds) -> Unit,
    onFlipRunning: (Boolean) -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var showQuestion by rememberSaveable(sessionId, item.questionId, item.presentationCount) {
        mutableStateOf(false)
    }
    val showAnswer = item.isRevealed && !showQuestion
    LaunchedEffect(showAnswer) { onFaceChanged() }
    val targetRotation = if (showAnswer) 180f else 0f
    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(FLIP_DURATION_MILLIS),
        label = "preparation_card_flip",
    )
    LaunchedEffect(rotation, targetRotation) { onFlipRunning(rotation != targetRotation) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { stateDescription = if (rotation <= 90f) "Question" else "Answer" }
            .clickable(
                enabled = enabled && rotation == targetRotation,
                role = Role.Button,
                onClickLabel = if (showAnswer) "Show question" else "Show answer",
            ) {
                if (item.isRevealed) showQuestion = !showQuestion else onReveal()
            }
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density.density
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        if (rotation <= 90f) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("preparation_card_front"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PreparationCardHeader(topicLabel, onBounds = onHeaderBounds)
                Text(
                    text = item.questionSnapshot,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { rotationY = 180f }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PreparationCardHeader("Answer", Modifier.testTag("preparation_card_back"), onBounds = onHeaderBounds)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preparation_answer"),
                ) {
                    Text(
                        text = item.answerSnapshot,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private const val FLIP_DURATION_MILLIS = 550

@Composable
fun PreparationSummaryScreen(
    state: PreparationSummaryUiState,
    onRetry: () -> Unit,
    onRetryReadiness: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit = {},
    onRetryCoverage: () -> Unit = {},
) {
    if (!state.isLoading && state.isMissingContent) {
        MissingContentScreen(title = "Preparation summary", onBack = onBack)
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = "Preparation summary",
                subtitle = state.session?.summarySubtitle(),
                variant = StudyTopBarVariant.BACK_SUBTITLE,
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(20.dp)
                .testTag("preparation_summary_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.isLoading -> LoadingState(modifier = Modifier.testTag("preparation_summary_loading"))
                state.error != null -> ErrorState(
                    message = state.error,
                    onRetry = onRetry,
                    modifier = Modifier.testTag("preparation_summary_error"),
                )
                state.isNotCompleted -> ErrorState(
                    message = "This preparation session is not complete yet.",
                    onRetry = onRetry,
                    modifier = Modifier.testTag("preparation_summary_incomplete"),
                )
                state.session == null || state.summary == null -> MissingContentScreen(
                    title = "Preparation summary",
                    onBack = onBack,
                )
                else -> SummaryContent(
                    summary = state.summary,
                    readiness = state.readiness,
                    isReadinessLoading = state.isReadinessLoading,
                    readinessError = state.readinessError,
                    onRetryReadiness = onRetryReadiness,
                    onBack = onBack,
                    onContinue = onContinue,
                    onRetryCoverage = onRetryCoverage,
                    state = state,
                )
            }
        }
    }
}

@Composable
private fun SummaryContent(
    summary: PreparationSummaryProjection,
    readiness: com.studysmart.app.feature.readiness.ReadinessEstimate?,
    isReadinessLoading: Boolean,
    readinessError: String?,
    onRetryReadiness: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onRetryCoverage: () -> Unit,
    state: PreparationSummaryUiState,
) {
    Text("Nice work!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(
        "Your review history has been saved.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ResultMetric(
            value = summary.uniqueQuestionCount.toString(),
            label = "Unique questions",
            modifier = Modifier.weight(1f),
        )
        ResultMetric(
            value = summary.presentationCount.toString(),
            label = "Presentations",
            modifier = Modifier.weight(1f),
        )
        ResultMetric(
            value = summary.needsImprovementCount.toString(),
            label = "Needs improvement",
            modifier = Modifier.weight(1f),
        )
    }
    Text("Rating history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryMetric("Again", summary.ratingCounts.getValue(Rating.AGAIN), Modifier.weight(1f))
        SummaryMetric("Hard", summary.ratingCounts.getValue(Rating.HARD), Modifier.weight(1f))
        SummaryMetric("Good", summary.ratingCounts.getValue(Rating.GOOD), Modifier.weight(1f))
        SummaryMetric("Easy", summary.ratingCounts.getValue(Rating.EASY), Modifier.weight(1f))
    }
    ReadinessEstimateCard(
        estimate = readiness,
        isLoading = isReadinessLoading,
        error = readinessError,
        onRetry = onRetryReadiness,
        testTag = "preparation_readiness_slot",
    )
    Text(
        "Recently reviewed",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag("preparation_recently_reviewed"),
    )
    summary.recentlyReviewed
        .forEach { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("preparation_summary_item_${item.questionId}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.questionSnapshot,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    item.lastRating?.let { rating ->
                        StatusPill(status = rating.toStatusPill())
                    }
                }
            }
        }
    Spacer(Modifier.height(4.dp))
    if (state.session?.chapterId != null) {
        state.coverageError?.let {
            ErrorState(message = it, onRetry = onRetryCoverage, modifier = Modifier.testTag("preparation_coverage_error"))
        }
        if (state.isChapterUnavailable || state.chapterCoverage?.eligibleCount == 0) {
            Text("No questions available in this chapter.", style = MaterialTheme.typography.bodySmall)
        }
        PrimaryButton(
            text = if (state.chapterCoverage?.isCovered == true) "Practice again" else "Continue",
            onClick = onContinue,
            enabled = !state.isCoverageLoading && !state.isChapterUnavailable && state.chapterCoverage?.eligibleCount != 0,
            modifier = Modifier.fillMaxWidth().testTag("preparation_summary_continue"),
        )
    }
    SecondaryButton(
        text = "Back",
        leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
        onClick = onBack,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preparation_summary_back"),
    )
}

@Composable
private fun SummaryMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    ResultMetric(value = value.toString(), label = label, modifier = modifier)
}

private fun PreparationItemModel.topicLabel(session: PreparationSessionModel): String =
    session.context.topicTitle ?: "Question"

private fun PreparationSessionModel.headerTitle(): String =
    if (scopeType == com.studysmart.app.core.model.PreparationScopeType.DUE_REVIEW) {
        "Due review"
    } else {
        context.chapterTitle ?: "Preparation"
    }

private fun PreparationSessionModel.headerSubtitle(): String? =
    if (scopeType == com.studysmart.app.core.model.PreparationScopeType.DUE_REVIEW) {
        listOf(courseCodeOrFallback(), context.academicPeriodName)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
    } else {
        listOfNotNull(context.subjectCode, context.academicPeriodName)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
    }

private fun PreparationSessionModel.summarySubtitle(): String? =
    if (scopeType == com.studysmart.app.core.model.PreparationScopeType.DUE_REVIEW) {
        listOf(courseCodeOrFallback(), context.academicPeriodName)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
    } else {
        listOfNotNull(context.chapterTitle, context.subjectCode)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
    }

private fun PreparationSessionModel.courseCodeOrFallback(): String =
    context.courseCode.ifBlank { context.courseName }

private fun Rating.toStatusPill(): StatusPillVariant = when (this) {
    Rating.AGAIN -> StatusPillVariant.AGAIN
    Rating.HARD -> StatusPillVariant.HARD
    Rating.GOOD -> StatusPillVariant.GOOD
    Rating.EASY -> StatusPillVariant.EASY
}
