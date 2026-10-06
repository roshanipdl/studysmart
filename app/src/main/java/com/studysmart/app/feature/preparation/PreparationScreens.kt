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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.studysmart.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs
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
    onBookmark: () -> Unit = {},
    onRetryBookmark: () -> Unit = {},
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
    var showQuestion by rememberSaveable(session?.id, item?.questionId, item?.presentationCount) {
        mutableStateOf(false)
    }
    val showAnswer = item?.isRevealed == true && !showQuestion
    val flipCard: () -> Unit = {
        if (item?.isRevealed == true) showQuestion = !showQuestion else onReveal()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                title = session?.headerTitle() ?: "Preparation",
                subtitle = session?.headerSubtitle(),
                variant = StudyTopBarVariant.BACK_SUBTITLE,
                onBack = onClose,
                navigationContentDescription = "Close",
                trailingContent = {
                    if (item != null && state.bookmarks != null) {
                        com.studysmart.app.core.ui.BookmarkControl(state.bookmarks, item.questionId, onBookmark,
                            enabled = !state.isSaving && state.ratingFeedback == null && !state.showDiscardConfirmation,
                            iconOnly = true, modifier = Modifier.testTag("preparation_bookmark"))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = 12.dp)
                .testTag("preparation_session_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.bookmarks?.let {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    com.studysmart.app.core.ui.BookmarkStatus(it, onRetryBookmark)
                }
            }
            if (state.isLoading || session == null || item == null) {
                LoadingState(
                    message = "Loading card",
                    modifier = Modifier.padding(horizontal = 20.dp).testTag("preparation_session_loading"),
                )
            } else {
                val interactionEnabled = !state.isSaving && state.mutationError == null &&
                    state.ratingFeedback == null && !state.showDiscardConfirmation && !state.completionReady
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("preparation_study_viewport"),
                ) {
                    val viewportHeight = constraints.maxHeight
                    val questionScroll = androidx.compose.runtime.key(session.id, currentCardKey, "question") {
                        rememberScrollState()
                    }
                    val answerScroll = androidx.compose.runtime.key(session.id, currentCardKey, "answer") { rememberScrollState() }
                    val scrollState = if (showAnswer) answerScroll else questionScroll
                    var answerEndReached by rememberSaveable(session.id, currentCardKey) { mutableStateOf(false) }
                    var scrollUnlockNotified by rememberSaveable(session.id, currentCardKey) { mutableStateOf(false) }
                    var showUnlockNotice by remember(session.id, currentCardKey) { mutableStateOf(false) }
                    var questionMeasurement by remember(session.id, currentCardKey) { mutableStateOf(PreparationBodyMeasurement()) }
                    var answerMeasurement by remember(session.id, currentCardKey) { mutableStateOf(PreparationBodyMeasurement()) }
                    var scrollGestureActive by remember(session.id, currentCardKey) { mutableStateOf(false) }
                    var pageScrolling by remember(session.id, currentCardKey) { mutableStateOf(false) }
                    var pageJob by remember(session.id, currentCardKey) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                    val scrollingAction = scrollGestureActive || pageScrolling
                    val bodyMeasurement = if (showAnswer) answerMeasurement else questionMeasurement
                    val controlMode = preparationControlMode(answerEndReached, bodyMeasurement, scrollingAction)
                    val ratingEligible = interactionEnabled && item.isRevealed && answerEndReached && !scrollingAction
                    val rateIfEligible: (Rating) -> Unit = { if (ratingEligible) onRate(it) }
                    val density = androidx.compose.ui.platform.LocalDensity.current.density
                    LaunchedEffect(answerScroll, showAnswer, item.isRevealed, density) {
                        snapshotFlow {
                            preparationAnswerEndReached(answerEndReached, item.isRevealed,
                                showAnswer, answerMeasurement.ready, answerMeasurement, answerScroll.value,
                                density.toInt().coerceAtLeast(1))
                        }.collect { reached ->
                            if (reached && !answerEndReached) {
                                if (answerMeasurement.overflows && !scrollUnlockNotified) {
                                    scrollUnlockNotified = true
                                    showUnlockNotice = true
                                }
                                answerEndReached = true
                            }
                        }
                    }
                    LaunchedEffect(session.id, currentCardKey, showUnlockNotice) {
                        if (showUnlockNotice) {
                            delay(5_000)
                            showUnlockNotice = false
                        }
                    }
                    val scrollScope = androidx.compose.runtime.key(session.id, currentCardKey) { rememberCoroutineScope() }
                    LaunchedEffect(interactionEnabled, showAnswer) {
                        if (!interactionEnabled || pageScrolling) pageJob?.cancel()
                    }
                    val scrollPage: (Boolean) -> Unit = { forward ->
                        if (interactionEnabled && controlMode == PreparationControlMode.CONTENT_SCROLL && !scrollingAction) {
                            pageScrolling = true
                            pageJob = scrollScope.launch {
                                try {
                                    val distance = (bodyMeasurement.viewportHeight * 0.6f).toInt()
                                    val target = (scrollState.value + if (forward) distance else -distance)
                                        .coerceIn(0, scrollState.maxValue)
                                    scrollState.animateScrollTo(target, tween(250))
                                } finally { pageScrolling = false }
                            }
                        }
                    }
                    val outerScrollState = androidx.compose.runtime.key(session.id, currentCardKey) { rememberScrollState() }
                    val showDirectionHints = preparationRatingPadWidth(flipExtra = 0.dp) <= maxWidth
                    val flipExtra = (maxWidth - preparationRatingPadWidth(showDirectionHints, 0.dp)).coerceIn(0.dp, 8.dp)
                    val padWidth = preparationRatingPadWidth(showDirectionHints, flipExtra)
                    val progressProjection = projectPreparationProgress(session)
                    val remaining = projectPreparationRemaining(session)
                    androidx.compose.runtime.key(session.id, currentCardKey) {
                        var joystickVisual by remember { mutableStateOf(PreparationJoystickVisual()) }
                        PreparationSwipeCard(
                            enabled = ratingEligible,
                            inputEnabled = interactionEnabled,
                            feedback = state.ratingFeedback,
                            viewportHeight = viewportHeight,
                             entranceDirection = entranceDirection,
                             resetPreview = state.mutationError != null || state.showDiscardConfirmation,
                            onRate = rateIfEligible,
                            onExitFinished = { id ->
                                entranceDirection = state.ratingFeedback?.rating?.swipeDirection()
                                onRatingAnimationFinished(id)
                            },
                            onBusyChanged = {},
                            scrollingAllowed = interactionEnabled && controlMode == PreparationControlMode.CONTENT_SCROLL,
                            readingScrollState = scrollState,
                            onScrollGestureActive = { scrollGestureActive = it },
                            onJoystickVisualChanged = { joystickVisual = it },
                            ratingPad = { modifier, ready, ratingGestureActive, previewDirection, scrollDirection, onFlipBounds ->
                                PreparationRatingPad(
                                    enabled = ratingEligible && ready,
                                    ratingsUnlocked = ratingEligible,
                                    ratingGestureActive = ratingGestureActive,
                                    joystickVisual = joystickVisual,
                                    onRate = rateIfEligible,
                                    modifier = modifier.testTag("preparation_rating_pad"),
                                    flipEnabled = interactionEnabled && ready,
                                    onFlip = flipCard,
                                    showAnswer = showAnswer,
                                    showDirectionHints = showDirectionHints,
                                    flipExtra = flipExtra,
                                    previewDirection = previewDirection,
                                    onFlipBounds = onFlipBounds,
                                    scrollMode = controlMode == PreparationControlMode.CONTENT_SCROLL,
                                    scrollForwardEnabled = interactionEnabled && ready && !scrollingAction && scrollState.canScrollForward,
                                    scrollBackwardEnabled = interactionEnabled && ready && !scrollingAction && scrollState.canScrollBackward,
                                    onScroll = scrollPage,
                                    scrollDirection = scrollDirection,
                                )
                            },
                             layout = { modifier, card, pad, preview ->
                               Box(modifier.fillMaxSize()) {
                                 PreparationStudyLayout(
                                     viewportHeight = viewportHeight,
                                      padWidth = padWidth,
                                     outerScroll = outerScrollState,
                                     modifier = modifier.fillMaxWidth(),
                                     card = card,
                                     pad = pad,
                                      progress = { PreparationProgress(progressProjection, remaining) },
                                       instructions = { PreparationSupportingStatus(state.isSaving,
                                           showAnswer, controlMode, answerEndReached, showUnlockNotice) },
                                      recovery = {
                                          state.mutationError?.let { message ->
                                              ErrorState(
                                                  message = message,
                                                  onRetry = onRetry,
                                                  modifier = Modifier.testTag("preparation_save_error"),
                                              )
                                          }
                                     },
                                 )
                                Box(Modifier.align(Alignment.Center)) { preview() }
                              }
                            },
                        ) { swipeModifier, tint, onFlipRunning, cardReady, scrollEnabled ->
                            PreparationFlashcard(
                                item = item,
                                showAnswer = showAnswer,
                                topicLabel = session.context.topicTitle,
                                remainingLabel = remaining.label,
                                onFlip = flipCard,
                                enabled = interactionEnabled && cardReady,
                                onFaceChanged = {
                                    scrollState.scrollTo(0)
                                    outerScrollState.scrollTo(0)
                                },
                                scrollState = scrollState,
                                 scrollEnabled = scrollEnabled,
                                 onBodyMeasured = { answer, measurement ->
                                     if (answer) answerMeasurement = measurement else questionMeasurement = measurement
                                 },
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

/** Both copy variants and the inline status reserve space even while idle. */
@Composable
internal fun PreparationSupportingStatus(isSaving: Boolean,
    showAnswer: Boolean, mode: PreparationControlMode, unlocked: Boolean, showUnlockNotice: Boolean) {
    val messages = listOf(
        stringResource(R.string.preparation_instruction_question),
        stringResource(R.string.preparation_instruction_unlocked),
        stringResource(R.string.preparation_instruction_long_question),
        stringResource(R.string.preparation_instruction_locked_answer),
    )
    val messageIndex = when {
        unlocked && mode == PreparationControlMode.RATING -> 1
        showAnswer -> 3
        mode == PreparationControlMode.CONTENT_SCROLL -> 2
        else -> 0
    }
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    val statusStyle = MaterialTheme.typography.bodySmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    SubcomposeLayout(Modifier.fillMaxWidth().testTag("preparation_supporting_status")) { constraints ->
        val statusHeight = listOf("Saving", "Ratings unlocked").maxOf {
            measurer.measure(AnnotatedString(it), style = statusStyle,
                constraints = Constraints.fixedWidth(constraints.maxWidth)).size.height
        }
        val gap = 4.dp.roundToPx()
        val textWidth = constraints.maxWidth
        val copyHeight = messages.maxOf { message ->
            measurer.measure(AnnotatedString(message), style = style,
                constraints = Constraints.fixedWidth(textWidth)).size.height
        }
        val height = copyHeight + gap + statusHeight
        val copy = subcompose("copy") {
            Text(messages[messageIndex],
                Modifier.testTag("preparation_supporting_text"), style = style, color = color)
        }.single().measure(Constraints.fixed(textWidth, copyHeight))
        val status = subcompose("status") {
            Box(Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = if (showUnlockNotice) "Ratings unlocked" else ""
            }, contentAlignment = Alignment.Center) {
                if (isSaving) Text("Saving", Modifier.testTag("preparation_saving").semantics {
                    liveRegion = LiveRegionMode.Polite
                }, style = statusStyle, color = color)
                else if (showUnlockNotice) Text("Ratings unlocked", Modifier.testTag("preparation_ratings_unlocked"), style = statusStyle, color = color)
            }
        }.single().measure(Constraints.fixed(textWidth, statusHeight))
        layout(constraints.maxWidth, height) {
            copy.placeRelative(0, 0)
            status.placeRelative(0, copyHeight + gap)
        }
    }
}

/** Keep progress above the reserved card slot and the instruction immediately above the pad. */
@Composable
private fun PreparationStudyLayout(
    viewportHeight: Int,
    padWidth: androidx.compose.ui.unit.Dp,
    outerScroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    card: @Composable (Boolean) -> Unit,
    pad: @Composable () -> Unit,
    progress: @Composable () -> Unit,
    instructions: @Composable () -> Unit,
    recovery: @Composable () -> Unit,
) {
    var overflow by remember { mutableStateOf(false) }
    val minimumReadingSlot = 200.dp * maxOf(1f, androidx.compose.ui.platform.LocalDensity.current.fontScale)
    SubcomposeLayout(modifier.verticalScroll(outerScroll, enabled = overflow)
        .then(if (overflow) Modifier.testTag("preparation_study_scroll") else Modifier)) { constraints ->
        val gap = 12.dp.roundToPx()
        val width = constraints.maxWidth
        val contentMargin = 20.dp.roundToPx()
        val contentWidth = (width - contentMargin * 2).coerceAtLeast(0)
        val progressPlaceable = subcompose("progress", progress).single()
            .measure(Constraints.fixedWidth(contentWidth))
        // Measure controls against the full screen, never the 20dp-inset card width.
        // Keep the measured width intact: an oversized pad is a product gate, not compression.
        val padPixels = padWidth.roundToPx()
        val instructionsPlaceable = subcompose("instructions", instructions).single()
            .measure(Constraints.fixedWidth(padPixels))
        val padPlaceable = subcompose("pad", pad).single().measure(Constraints.fixedWidth(padPixels))
        val recoveryPlaceable = subcompose("recovery") {
            Box(Modifier.fillMaxWidth()) { recovery() }
        }.single().measure(Constraints.fixedWidth(contentWidth))
        val hasRecovery = recoveryPlaceable.height > 0
        val fixedHeight = progressPlaceable.height + gap + instructionsPlaceable.height +
            gap + padPlaceable.height + (if (hasRecovery) gap + recoveryPlaceable.height else 0)
        val available = (viewportHeight - fixedHeight - gap).coerceAtLeast(0)
        val fallback = available < minimumReadingSlot.roundToPx()
        overflow = fallback
        val cardHeight = if (fallback) maxOf((viewportHeight * 0.58f).toInt(), minimumReadingSlot.roundToPx())
            else minOf((viewportHeight * 0.58f).toInt(), available)
        val cardPlaceable = subcompose("card") {
            Box(Modifier.testTag("preparation_card_viewport")) {
                card(true)
            }
        }.single().measure(Constraints(minWidth = contentWidth, maxWidth = contentWidth, maxHeight = cardHeight))
        val reservedHeight = cardHeight
        val height = maxOf(
            viewportHeight,
            progressPlaceable.height + gap + reservedHeight + gap + instructionsPlaceable.height +
                gap + padPlaceable.height + (if (hasRecovery) gap + recoveryPlaceable.height else 0),
        )
        val cardTop = progressPlaceable.height + gap
        val lowerGroupHeight = instructionsPlaceable.height + gap + padPlaceable.height +
            (if (hasRecovery) gap + recoveryPlaceable.height else 0)
        val instructionsTop = maxOf(cardTop + reservedHeight + gap, height - lowerGroupHeight)
        val padTop = instructionsTop + instructionsPlaceable.height + gap
        val recoveryTop = padTop + padPlaceable.height + gap
        val controls = preparationControlGroupGeometry(width.toFloat(), padPixels.toFloat(),
            contentMargin.toFloat(), 12.dp.toPx())
        val padLeft = controls.left.toInt()
        layout(constraints.maxWidth, height) {
            progressPlaceable.place(contentMargin, 0)
            cardPlaceable.place(contentMargin, cardTop)
            instructionsPlaceable.place(padLeft, instructionsTop)
            padPlaceable.place(padLeft, padTop)
            if (hasRecovery) recoveryPlaceable.place(contentMargin, recoveryTop)
        }
    }
}

@Composable
private fun PreparationProgress(
    progress: PreparationProgressProjection,
    remaining: PreparationRemainingProjection,
) {
    val colors = Rating.entries.map { preparationRatingColor(it) }
    val fractions = Rating.entries.map { rating ->
        animateFloatAsState(
            progress.ratingSegmentFractions.getValue(rating),
            label = "unique_progress_${rating.name}",
        ).value
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val completion = progress.completion.coerceIn(0f, 1f)
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            .testTag("preparation_progress").semantics {
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(completion, 0f..1f)
                contentDescription = "${progress.uniqueReviewed} of ${progress.uniqueTotal} unique questions reviewed; ${remaining.label}"
            },
    ) {
        drawRect(track)
        preparationHorizontalSegments(size.width, fractions).forEachIndexed { index, segment ->
            if (segment.right > segment.left) {
                drawRect(
                    colors[index],
                    topLeft = Offset(segment.left, 0f),
                    size = androidx.compose.ui.geometry.Size(segment.right - segment.left, size.height),
                )
            }
        }
    }
}

@Composable
private fun PreparationCardHeader(
    label: String,
    remainingLabel: String,
    topicContext: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                remainingLabel,
                modifier = Modifier.weight(1f).testTag("preparation_remaining_count")
                    .semantics { contentDescription = remainingLabel },
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        topicContext?.takeIf(String::isNotBlank)?.let {
            Text(
                it,
                Modifier.testTag("preparation_topic_context"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreparationFlashcard(
    item: PreparationItemModel,
    showAnswer: Boolean,
    topicLabel: String?,
    remainingLabel: String,
    onFlip: () -> Unit,
    enabled: Boolean,
    onFaceChanged: suspend () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    scrollEnabled: Boolean,
    onFlipRunning: (Boolean) -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onBodyMeasured: (Boolean, PreparationBodyMeasurement) -> Unit = { _, _ -> },
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
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
            .pointerInput(enabled, showAnswer) {
                // The revealed answer owns nested horizontal code scrolling and selection.
                if (!enabled || showAnswer) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var movement = Offset.Zero
                    var horizontalDrag = false
                    var dragged = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.changes.count { it.pressed } > 1) {
                            event.changes.forEach { it.consume() }
                            break
                        }
                        movement += change.position - change.previousPosition
                        if (movement.getDistance() > viewConfiguration.touchSlop) dragged = true
                        if (abs(movement.x) > viewConfiguration.touchSlop && abs(movement.x) > abs(movement.y)) {
                            horizontalDrag = true
                        }
                        // Horizontal movement has no card action; consume it only to
                        // cancel click-through. Vertical movement remains owned by scrolling.
                        if (horizontalDrag || (!change.pressed && dragged)) change.consume()
                        if (!change.pressed) break
                    } while (true)
                }
            }
            .semantics { stateDescription = if (rotation <= 90f) "Question" else "Answer" }
            .clickable(
                enabled = enabled && rotation == targetRotation,
                role = Role.Button,
                onClickLabel = if (showAnswer) "Show question" else "Show answer",
            ) {
                onFlip()
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
                modifier = Modifier.fillMaxWidth()
                    .testTag("preparation_card_front")
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                 PreparationCardHeader("Question", remainingLabel, topicLabel)
                 PreparationScrollableContent(scrollState, scrollEnabled,
                     rotation == targetRotation && !showAnswer, false,
                     { onBodyMeasured(false, it) }, Modifier.weight(1f, fill = false)) {
                   Text(
                    text = item.questionSnapshot,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                 )
                 }
          }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { rotationY = 180f }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                 PreparationCardHeader("Answer", remainingLabel, null, Modifier.testTag("preparation_card_back"))
                 Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                         .fillMaxWidth()
                         .weight(1f, fill = false)
                        .testTag("preparation_answer"),
                ) {
                    PreparationScrollableContent(scrollState, scrollEnabled,
                        rotation == targetRotation && showAnswer, true,
                        { onBodyMeasured(true, it) }) {
                       com.studysmart.app.core.ui.FormattedAnswerContent(
                         content = item.answerSnapshot,
                         format = item.answerFormatSnapshot,
                         modifier = Modifier.padding(8.dp),
                    )
                    }
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
    if (state.session?.chapterId != null && state.session.scopeType != com.studysmart.app.core.model.PreparationScopeType.IMPORTANT) {
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
