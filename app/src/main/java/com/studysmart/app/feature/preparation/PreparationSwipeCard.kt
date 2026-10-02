package com.studysmart.app.feature.preparation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.ui.LocalStudySmartSemanticColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.abs

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

@Composable
internal fun PreparationSwipeCard(
    enabled: Boolean,
    feedback: PreparationRatingFeedback?,
    viewportHeight: Int,
    entranceDirection: RatingSwipeDirection?,
    onRate: (Rating) -> Unit,
    onExitFinished: (String) -> Unit,
    onBusyChanged: (Boolean) -> Unit,
    ratingPad: @Composable (Modifier, Boolean) -> Unit = { _, _ -> },
    layout: @Composable (Modifier, @Composable () -> Unit, @Composable () -> Unit) -> Unit = { modifier, card, _ ->
        Box(modifier) { card() }
    },
    content: @Composable (Modifier, Color, (PreparationGestureBounds) -> Unit, (Boolean) -> Unit, Boolean) -> Unit,
) {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var headerBounds by remember { mutableStateOf<PreparationGestureBounds?>(null) }
    var flipRunning by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(entranceDirection != null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var direction by remember { mutableStateOf<RatingSwipeDirection?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var hostCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var padCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var springJob by remember { mutableStateOf<Job?>(null) }
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
    LaunchedEffect(flipRunning, settling) { latestBusy(flipRunning || settling) }
    LaunchedEffect(Unit) {
        if (entranceDirection != null) animatedOffset.animateTo(Offset.Zero, tween(220))
        settling = false
    }
    LaunchedEffect(feedback?.reviewRecordId, feedback?.committed) {
        val committed = feedback?.takeIf { it.committed } ?: return@LaunchedEffect
        springJob?.cancel()
        val exitDirection = committed.rating.swipeDirection()
        direction = exitDirection
        settling = true
        val horizontal = size.width.toFloat() + 48f * density
        val vertical = maxOf(size.height, viewportHeight).toFloat() + 48f * density
        val target = when (exitDirection) {
            RatingSwipeDirection.LEFT -> Offset(-horizontal, 0f)
            RatingSwipeDirection.RIGHT -> Offset(horizontal, 0f)
            RatingSwipeDirection.UP -> Offset(0f, -vertical)
            RatingSwipeDirection.DOWN -> Offset(0f, vertical)
        }
        animatedOffset.animateTo(target, tween(320))
        latestExit(committed.reviewRecordId)
    }
    val gestureEnabled = enabled && !flipRunning && !settling && feedback == null
    val previewRating = feedback?.rating ?: direction?.rating
    val axisDistance = if (direction == RatingSwipeDirection.LEFT || direction == RatingSwipeDirection.RIGHT) drag.x else drag.y
    val extent = if (direction == RatingSwipeDirection.LEFT || direction == RatingSwipeDirection.RIGHT) size.width else viewportHeight
    val intensity = if (feedback != null) 0.7f else ratingDragIntensity(axisDistance, ratingDragThreshold(extent.coerceAtLeast(1).toFloat(), density))
    val tint = if (previewRating == null) MaterialTheme.colorScheme.surface
        else lerp(MaterialTheme.colorScheme.surface, preparationRatingColor(previewRating), intensity * 0.7f)
    val latestHeaderBounds by rememberUpdatedState(headerBounds)
    val cardReady = !flipRunning && !settling && !dragging && feedback == null
    val hostModifier = Modifier.onGloballyPositioned { hostCoordinates = it }
                .pointerInput(gestureEnabled, viewportHeight) {
                    if (!gestureEnabled) return@pointerInput
                    try {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val host = hostCoordinates?.takeIf { it.isAttached } ?: return@awaitEachGesture
                        val card = cardCoordinates?.takeIf { it.isAttached }
                        val pad = padCoordinates?.takeIf { it.isAttached }
                        fun LayoutCoordinates.visibleBounds(): PreparationGestureBounds {
                            val bounds = boundsInRoot()
                            return PreparationGestureBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
                        }
                        val start = host.localToRoot(down.position)
                        val region = preparationGestureRegion(
                            start.x, start.y, card?.visibleBounds(),
                            latestHeaderBounds,
                            pad?.visibleBounds(),
                        )
                        if (region == PreparationGestureRegion.OUTSIDE) return@awaitEachGesture
                        val allowVertical = region.allowsVertical
                        var accumulated = Offset.Zero
                        var axis: RatingSwipeDirection? = null
                        var released = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.count { it.pressed } > 1 || change.isConsumed) break
                            if (!change.pressed) {
                                if (axis != null) change.consume()
                                released = true
                                break
                            }
                            accumulated += change.position - change.previousPosition
                            if (axis == null) {
                                axis = ratingDragDirection(accumulated.x, accumulated.y, viewConfiguration.touchSlop, allowVertical)
                                if (!allowVertical && abs(accumulated.y) > viewConfiguration.touchSlop &&
                                    abs(accumulated.y) > abs(accumulated.x) * 1.25f) break
                            }
                            if (axis != null) {
                                dragging = true
                                change.consume()
                                val horizontal = axis == RatingSwipeDirection.LEFT || axis == RatingSwipeDirection.RIGHT
                                drag = if (horizontal) Offset(accumulated.x, 0f) else Offset(0f, accumulated.y)
                                direction = if (horizontal) {
                                    if (drag.x < 0f) RatingSwipeDirection.LEFT else RatingSwipeDirection.RIGHT
                                } else if (drag.y < 0f) RatingSwipeDirection.UP else RatingSwipeDirection.DOWN
                            } else {
                                // Child scrolling runs in Main. Once it owns movement, do not
                                // reinterpret a later horizontal turn as a rating.
                                val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                                if (finalEvent.changes.any { it.id == down.id && it.isConsumed }) break
                            }
                        } while (true)
                        val selected = direction
                        val distance = if (selected == RatingSwipeDirection.LEFT || selected == RatingSwipeDirection.RIGHT) drag.x else drag.y
                        val selectedExtent = if (selected == RatingSwipeDirection.LEFT || selected == RatingSwipeDirection.RIGHT) size.width else viewportHeight
                        if (released && axis != null && selected != null && ratingDragCommits(
                                distance, ratingDragThreshold(selectedExtent.coerceAtLeast(1).toFloat(), density),
                            )) {
                            drag = Offset.Zero
                            direction = null
                            latestRate(selected.rating)
                        } else if (axis != null) {
                            val start = drag
                            drag = Offset.Zero
                            direction = null
                            settling = true
                            springJob = scope.launch {
                                try {
                                    animatedOffset.snapTo(start)
                                    animatedOffset.animateTo(Offset.Zero)
                                } finally { settling = false }
                            }
                        }
                        dragging = false
                    }
                    } finally {
                        drag = Offset.Zero
                        direction = null
                        dragging = false
                    }
                }
    DisposableEffect(Unit) { onDispose { springJob?.cancel() } }
    Box {
        layout(hostModifier, {
            content(
                Modifier.onSizeChanged { size = it }.onGloballyPositioned { cardCoordinates = it }
                .graphicsLayer {
                    translationX = animatedOffset.value.x + drag.x
                    translationY = animatedOffset.value.y + drag.y
                    rotationZ = ((animatedOffset.value.x + drag.x) / (size.width.coerceAtLeast(1))) * 5f
                },
            tint,
            { headerBounds = it },
            { flipRunning = it },
            cardReady,
            )
        }, {
            ratingPad(Modifier.onGloballyPositioned { padCoordinates = it }, gestureEnabled && cardReady)
        })
        if (direction != null && feedback == null) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).testTag("preparation_swipe_preview"),
                color = preparationRatingColor(direction!!.rating),
                shape = MaterialTheme.shapes.small,
            ) {
                Text("${direction!!.arrow} ${direction!!.rating.name.lowercase().replaceFirstChar { it.uppercase() }}", Modifier.padding(8.dp))
            }
        }
    }
}
