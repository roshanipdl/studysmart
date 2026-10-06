package com.studysmart.app.feature.preparation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.ui.LocalStudySmartSemanticColors
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.MutatePriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private class PreparationScrollDrive(var velocity: Float = 0f)

@Composable
internal fun preparationRatingColor(rating: Rating): Color {
    val colors = LocalStudySmartSemanticColors.current
    return when (rating) {
        Rating.AGAIN -> MaterialTheme.colorScheme.errorContainer
        Rating.HARD -> colors.warningContainer
        Rating.GOOD -> colors.successContainer
        Rating.EASY -> colors.infoContainer
    }
}

/** Owns committed transitions; only the pad owns rating pointer input. */
@Composable
internal fun PreparationSwipeCard(
    enabled: Boolean,
    inputEnabled: Boolean,
    feedback: PreparationRatingFeedback?,
    viewportHeight: Int,
    entranceDirection: RatingSwipeDirection?,
    resetPreview: Boolean = false,
    onRate: (Rating) -> Unit,
    onExitFinished: (String) -> Unit,
    onBusyChanged: (Boolean) -> Unit,
    scrollingAllowed: Boolean = false,
    readingScrollState: ScrollState? = null,
    onScrollGestureActive: (Boolean) -> Unit = {},
    onJoystickVisualChanged: (PreparationJoystickVisual) -> Unit = {},
    ratingPad: @Composable (Modifier, Boolean, RatingSwipeDirection?, RatingSwipeDirection?, (Rect) -> Unit) -> Unit = { _, _, _, _, _ -> },
    layout: @Composable (Modifier, @Composable (Boolean) -> Unit, @Composable () -> Unit, @Composable () -> Unit) -> Unit = { modifier, card, _, preview ->
        Box(modifier) { card(false); Box(Modifier.align(Alignment.Center)) { preview() } }
    },
    content: @Composable (Modifier, Color, (Boolean) -> Unit, Boolean, Boolean) -> Unit,
) {
    val density = LocalDensity.current.density
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var padRootOffset by remember { mutableStateOf(Offset.Zero) }
    var flipBounds by remember { mutableStateOf<Rect?>(null) }
    val latestPadRootOffset by rememberUpdatedState(padRootOffset)
    val latestFlipBounds by rememberUpdatedState(flipBounds)
    var flipRunning by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(entranceDirection != null) }
    var direction by remember { mutableStateOf<RatingSwipeDirection?>(null) }
    var scrollDirection by remember { mutableStateOf<RatingSwipeDirection?>(null) }
    val latestScrollingAllowed by rememberUpdatedState(scrollingAllowed)
    val latestRatingEnabled by rememberUpdatedState(enabled)
    val latestScrollGesture by rememberUpdatedState(onScrollGestureActive)
    val latestJoystickVisual by rememberUpdatedState(onJoystickVisualChanged)
    DisposableEffect(Unit) {
        onDispose { latestJoystickVisual(PreparationJoystickVisual()) }
    }
    var dragging by remember { mutableStateOf(false) }
    var previewOffset by remember { mutableStateOf(Offset.Zero) }
    var previewRating by remember { mutableStateOf<Rating?>(null) }
    var previewIntensity by remember { mutableFloatStateOf(0f) }
    var retainedPreview by remember { mutableStateOf(false) }
    val previewX by animateFloatAsState(previewOffset.x, tween(100), label = "rating_preview_x")
    val previewY by animateFloatAsState(previewOffset.y, tween(100), label = "rating_preview_y")
    val previewColor = previewRating?.let { preparationRatingColor(it) } ?: MaterialTheme.colorScheme.surface
    val tint by animateColorAsState(lerp(MaterialTheme.colorScheme.surface, previewColor, previewIntensity),
        tween(100), label = "rating_preview_tint")
    LaunchedEffect(resetPreview) {
        if (resetPreview) {
            latestJoystickVisual(PreparationJoystickVisual())
            direction = null
            scrollDirection = null
            dragging = false
            retainedPreview = false
             previewOffset = Offset.Zero
             previewRating = null
            previewIntensity = 0f
        }
    }
    val initial = when (entranceDirection) {
        RatingSwipeDirection.LEFT -> Offset(64f * density, 0f)
        RatingSwipeDirection.RIGHT -> Offset(-64f * density, 0f)
        RatingSwipeDirection.UP -> Offset(0f, 64f * density)
        RatingSwipeDirection.DOWN -> Offset(0f, -64f * density)
        null -> Offset.Zero
    }
    val animatedOffset = remember { Animatable(initial, Offset.VectorConverter) }
    val latestRate by rememberUpdatedState(onRate)
    val latestExit by rememberUpdatedState(onExitFinished)
    val latestBusy by rememberUpdatedState(onBusyChanged)
    LaunchedEffect(flipRunning, settling, dragging) { latestBusy(flipRunning || settling || dragging) }
    LaunchedEffect(Unit) {
        if (entranceDirection != null) animatedOffset.animateTo(Offset.Zero, tween(220))
        settling = false
    }
    LaunchedEffect(feedback?.reviewRecordId, feedback?.committed) {
        val committed = feedback?.takeIf { it.committed } ?: return@LaunchedEffect
        settling = true
        val horizontal = cardSize.width.toFloat() + 48f * density
        val vertical = maxOf(cardSize.height, viewportHeight).toFloat() + 48f * density
        val target = when (committed.rating.swipeDirection()) {
            RatingSwipeDirection.LEFT -> Offset(-horizontal, 0f)
            RatingSwipeDirection.RIGHT -> Offset(horizontal, 0f)
            RatingSwipeDirection.UP -> Offset(0f, -vertical)
            RatingSwipeDirection.DOWN -> Offset(0f, vertical)
        }
        animatedOffset.animateTo(target, tween(320))
        latestExit(committed.reviewRecordId)
    }
    // A reset invalidates ownership, not just its preview. Changing this pointer-input
    // key cancels the captured gesture and scroll drive before later input can resume.
    val pointerEnabled = inputEnabled && !resetPreview && !flipRunning && !settling && feedback == null
    val cardReady = !resetPreview && !flipRunning && !settling && !dragging && feedback == null
    val padModifier = Modifier.onSizeChanged { padSize = it }
        .onGloballyPositioned { padRootOffset = it.localToRoot(Offset.Zero) }
        .pointerInput(pointerEnabled, padSize, density, readingScrollState) {
            if (!pointerEnabled) return@pointerInput
            val gestureScope = CoroutineScope(currentCoroutineContext())
            var cancelScrolling: () -> Unit = {}
            var generation = 0L
            try {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // A child's minimum-touch-target expansion must not enlarge the rating zone.
                     if (down.position.x < 0f || down.position.y < 0f ||
                         down.position.x >= size.width || down.position.y >= size.height) return@awaitEachGesture
                     // Read current geometry at down; scrolling must not restart an owned gesture.
                     val rootDown = down.position + latestPadRootOffset
                     val origin = if (latestFlipBounds?.let { bounds ->
                         (rootDown - bounds.center).getDistance() <= minOf(bounds.width, bounds.height) / 2f
                      } == true) PreparationGestureOrigin.FLIP_JOYSTICK else PreparationGestureOrigin.PAD_SWIPE
                     val ratingAtDown = latestRatingEnabled
                     val movingKnob = ratingAtDown && origin == PreparationGestureOrigin.FLIP_JOYSTICK
                     latestJoystickVisual(PreparationJoystickVisual(active = movingKnob))
                     val scrollAtDown = !ratingAtDown && latestScrollingAllowed &&
                         origin == PreparationGestureOrigin.FLIP_JOYSTICK && readingScrollState != null
                     val drive = PreparationScrollDrive()
                     var scrollJob: Job? = null
                     val gestureGeneration = ++generation
                     cancelScrolling = { drive.velocity = 0f; scrollJob?.cancel() }
                    var accumulated = Offset.Zero
                    var axis: RatingSwipeDirection? = null
                    var moved = false
                    var released = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.changes.count { it.pressed } > 1) {
                            event.changes.forEach { it.consume() }
                            break
                        }
                        if (change.isConsumed) break
                        // Up may carry the final position: classify it through the same path as moves.
                        accumulated += change.position - change.previousPosition
                        if (movingKnob && latestRatingEnabled && change.pressed) {
                            // Reserved center geometry supplies at most 8dp radial travel.
                            val available = minOf(8f * density,
                                (latestFlipBounds?.let { minOf(it.width, it.height) / 2f } ?: 0f))
                            latestJoystickVisual(PreparationJoystickVisual(true,
                                preparationJoystickOffset(accumulated.x, accumulated.y,
                                    viewConfiguration.touchSlop, density, available)))
                        }
                        if (accumulated.getDistance() > viewConfiguration.touchSlop) moved = true
                        if (origin == PreparationGestureOrigin.FLIP_JOYSTICK) {
                            axis = ratingJoystickDirection(accumulated.x, accumulated.y, viewConfiguration.touchSlop)
                        } else if (axis == null) {
                            axis = ratingDragDirection(accumulated.x, accumulated.y, viewConfiguration.touchSlop, true)
                        }
                        if (moved) {
                            dragging = true
                            change.consume()
                            if (scrollAtDown) {
                                latestScrollGesture(true)
                                drive.velocity = if (change.pressed) preparationJoystickScrollVelocity(
                                    accumulated.x, accumulated.y, viewConfiguration.touchSlop, density) else 0f
                                if (scrollJob != null && !scrollJob.isActive) drive.velocity = 0f
                                if (drive.velocity > 0f && !readingScrollState.canScrollForward ||
                                    drive.velocity < 0f && !readingScrollState.canScrollBackward) drive.velocity = 0f
                                scrollDirection = when {
                                    drive.velocity > 0f -> RatingSwipeDirection.UP
                                    drive.velocity < 0f -> RatingSwipeDirection.DOWN
                                    else -> null
                                }
                                direction = null
                                previewRating = null
                                previewOffset = Offset.Zero
                                previewIntensity = 0f
                                if (scrollJob == null && change.pressed) {
                                    scrollJob = gestureScope.launch {
                                        try {
                                            readingScrollState.scroll(MutatePriority.Default) {
                                                var previous = withFrameNanos { it }
                                                while (isActive) {
                                                    val now = withFrameNanos { it }
                                                    val seconds = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
                                                    previous = now
                                                    scrollBy(drive.velocity * seconds)
                                                }
                                            }
                                        } finally {
                                            drive.velocity = 0f
                                            if (generation == gestureGeneration) scrollDirection = null
                                        }
                                    }
                                }
                            } else if (ratingAtDown && latestRatingEnabled && axis != null) {
                                direction = if (axis == RatingSwipeDirection.LEFT || axis == RatingSwipeDirection.RIGHT) {
                                    if (accumulated.x < 0f) RatingSwipeDirection.LEFT else RatingSwipeDirection.RIGHT
                                 } else if (accumulated.y < 0f) RatingSwipeDirection.UP else RatingSwipeDirection.DOWN
                                 val horizontal = axis == RatingSwipeDirection.LEFT || axis == RatingSwipeDirection.RIGHT
                                 val distance = if (horizontal) accumulated.x else accumulated.y
                                 val extent = if (horizontal) padSize.width else padSize.height
                                 val threshold = ratingDragThreshold(extent.coerceAtLeast(1).toFloat(), density)
                                 val displacement = ratingPreviewDistance(distance, threshold, density)
                                 previewOffset = if (horizontal) Offset(displacement, 0f) else Offset(0f, displacement)
                                 previewRating = direction?.rating
                                  previewIntensity = ratingDragIntensity(distance, threshold)
                            } else {
                                direction = null
                                previewRating = null
                                previewOffset = Offset.Zero
                                previewIntensity = 0f
                            }
                        } else if (change.pressed) {
                            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                            if (finalEvent.changes.any { it.id == down.id && it.isConsumed }) break
                        }
                        if (!change.pressed) {
                            released = true
                            break
                        }
                    } while (true)
                    val selected = direction
                    val horizontal = selected == RatingSwipeDirection.LEFT || selected == RatingSwipeDirection.RIGHT
                    val distance = if (horizontal) accumulated.x else accumulated.y
                    val extent = if (horizontal) padSize.width else padSize.height
                    val commit = ratingAtDown && latestRatingEnabled && released && axis != null && selected != null && ratingDragCommits(
                        distance, ratingDragThreshold(extent.coerceAtLeast(1).toFloat(), density),
                    )
                      direction = null
                      latestJoystickVisual(PreparationJoystickVisual())
                      cancelScrolling()
                      scrollDirection = null
                      latestScrollGesture(false)
                     dragging = false
                     retainedPreview = commit
                      if (!commit) {
                          previewRating = null
                         previewOffset = Offset.Zero
                         previewIntensity = 0f
                     }
                     if (commit) latestRate(selected.rating)
                }
            } finally {
                  latestJoystickVisual(PreparationJoystickVisual())
                  direction = null
                  cancelScrolling()
                  scrollDirection = null
                  latestScrollGesture(false)
                 dragging = false
                  if (!retainedPreview) {
                      previewRating = null
                     previewOffset = Offset.Zero
                     previewIntensity = 0f
                 }
            }
        }
    layout(Modifier, { scrollEnabled ->
        content(
            Modifier.onSizeChanged { cardSize = it }.graphicsLayer {
                translationX = animatedOffset.value.x + previewX
                translationY = animatedOffset.value.y + previewY
            },
            tint,
            { flipRunning = it },
            cardReady,
            scrollEnabled,
        )
    }, { ratingPad(padModifier, cardReady, direction, scrollDirection) { flipBounds = it } }, {
            scrollDirection?.let { scrolling ->
                Surface(Modifier.testTag("preparation_scroll_preview"), color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small) {
                    Text(if (scrolling == RatingSwipeDirection.UP) "↓ Read further" else "↑ Read earlier",
                        Modifier.padding(8.dp))
                }
            }
            direction?.let { preview ->
                Surface(
                    modifier = Modifier.testTag("preparation_swipe_preview"),
                    color = preparationRatingColor(preview.rating),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("${preview.arrow} ${preview.rating.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        Modifier.padding(8.dp))
                }
            }
    })
}
