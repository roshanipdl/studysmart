package com.studysmart.app.feature.preparation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.studysmart.app.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.ui.RatingButton
import com.studysmart.app.core.ui.RatingButtonVariant

@Composable
private fun preparationRatingLabels(): Map<Rating, String> = mapOf(
    Rating.AGAIN to stringResource(R.string.preparation_rating_again),
    Rating.HARD to stringResource(R.string.preparation_rating_hard),
    Rating.GOOD to stringResource(R.string.preparation_rating_good),
    Rating.EASY to stringResource(R.string.preparation_rating_easy),
)

@Composable
internal fun preparationRatingButtonDiameter(showDirectionHints: Boolean = true): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelLarge
    val labels = preparationRatingLabels()
    fun row(text: String) = measurer.measure(AnnotatedString(text),
        style = style).size.let {
        with(density) { it.width.toDp().value to it.height.toDp().value }
    }
    return RatingSwipeDirection.entries.maxOf { direction ->
        val rows = buildList {
            if (showDirectionHints) add(row(direction.arrow))
            add(row(labels.getValue(direction.rating)))
        }
        preparationCircleDiameter(rows)
    }.dp
}

/** Reading controls occupy the vertical axis; never inflate the horizontal row. */
@Composable
internal fun preparationReadingButtonDiameter(): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelLarge
    fun row(text: String) = measurer.measure(AnnotatedString(text),
        style = style).size.let {
        with(density) { it.width.toDp().value to it.height.toDp().value }
    }
    return maxOf(preparationCircleDiameter(listOf(row("Read ↓"), row("further"))),
        preparationCircleDiameter(listOf(row("Read ↑"), row("earlier")))).dp
}

/** +4dp beyond the 16dp gaps is rounding slack: px layout can then never coerce the last circle's width. */
@Composable
internal fun preparationRatingPadWidth(showDirectionHints: Boolean = true, flipExtra: Dp = 8.dp): Dp =
     preparationRatingButtonDiameter(showDirectionHints) * 2 + preparationFlipDiameter(showDirectionHints, flipExtra) + 20.dp

@Composable
internal fun preparationFlipDiameter(showDirectionHints: Boolean, flipExtra: Dp): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val labels = preparationRatingLabels()
    fun row(text: String) = measurer.measure(AnnotatedString(text),
        style = style).size.let {
        with(density) { it.width.toDp().value to it.height.toDp().value }
    }
    val iconStates = listOf(stringResource(R.string.preparation_flip), stringResource(R.string.preparation_drag)).maxOf {
        preparationCircleDiameter(listOf(24f to 24f, row(it)), gap = 4f, motion = 8f)
    }
    val selectedStates = RatingSwipeDirection.entries.maxOf { direction ->
        preparationCircleDiameter(listOf(row(direction.arrow),
            row(labels.getValue(direction.rating))), gap = 4f, motion = 8f)
    }
    return maxOf(preparationRatingButtonDiameter(showDirectionHints) + flipExtra,
        maxOf(iconStates, selectedStates).dp)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PreparationRatingPad(
    enabled: Boolean,
    onRate: (Rating) -> Unit,
    modifier: Modifier = Modifier,
    flipEnabled: Boolean = enabled,
    onFlip: () -> Unit = {},
    showAnswer: Boolean = false,
    showDirectionHints: Boolean = true,
    flipExtra: Dp = 8.dp,
    previewDirection: RatingSwipeDirection? = null,
    onFlipBounds: (Rect) -> Unit = {},
    scrollMode: Boolean = false,
    scrollForwardEnabled: Boolean = false,
    scrollBackwardEnabled: Boolean = false,
    onScroll: (Boolean) -> Unit = {},
    scrollDirection: RatingSwipeDirection? = null,
    ratingsUnlocked: Boolean = enabled,
    joystickVisual: PreparationJoystickVisual = PreparationJoystickVisual(),
) {
    // These positions describe physical swipe directions, not reading order.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalRippleConfiguration provides null) {
        val diameter = preparationRatingButtonDiameter(showDirectionHints)
        val readingDiameter = preparationReadingButtonDiameter()
         val flipDiameter = preparationFlipDiameter(showDirectionHints, flipExtra)
        val labels = preparationRatingLabels()
        val selected = previewDirection?.takeIf { ratingsUnlocked }
        val knobReturn = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        LaunchedEffect(joystickVisual.active, joystickVisual.offset, ratingsUnlocked) {
            if (!ratingsUnlocked) {
                knobReturn.snapTo(Offset.Zero)
            } else if (joystickVisual.active) {
                knobReturn.snapTo(joystickVisual.offset)
            } else {
                // Compose's animation clock honors MotionDurationScale, including immediate zero scale.
                knobReturn.animateTo(Offset.Zero, spring(dampingRatio = 1f, stiffness = 400f),
                    initialVelocity = Offset.Zero)
            }
        }
        val knobOffset = when {
            !ratingsUnlocked -> Offset.Zero
            joystickVisual.active -> joystickVisual.offset
            else -> knobReturn.value
        }
        val outline = MaterialTheme.colorScheme.outline
         val selectedColor = previewDirection?.takeIf { ratingsUnlocked }?.rating?.let { preparationRatingOutlineColor(it) }
        val flipInteractions = remember { MutableInteractionSource() }
        val flipPressed by flipInteractions.collectIsPressedAsState()
        val flipAction = stringResource(if (showAnswer) R.string.preparation_show_question else R.string.preparation_show_answer)
        Box(modifier) {
        Column(
            Modifier.testTag("preparation_ratings").width(preparationRatingPadWidth(showDirectionHints, flipExtra)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                 if (scrollMode) ScrollPadButton(true, scrollForwardEnabled, onScroll, Modifier.size(readingDiameter), scrollDirection == RatingSwipeDirection.UP)
                 else PadButton(Rating.GOOD, enabled, onRate, Modifier.size(diameter), showDirectionHints, previewDirection, ratingsUnlocked)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                 PadButton(Rating.AGAIN, enabled, onRate, Modifier.size(diameter), showDirectionHints, previewDirection, ratingsUnlocked)
                OutlinedButton(
                    onClick = onFlip,
                    enabled = flipEnabled,
                    interactionSource = flipInteractions,
                    modifier = Modifier.size(flipDiameter).testTag("preparation_flip_button")
                        .onGloballyPositioned { coordinates ->
                            onFlipBounds(Rect(coordinates.localToRoot(Offset.Zero),
                                Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())))
                        }.drawWithContent {
                            drawContent()
                            val inset = 1.5.dp.toPx()
                            RatingSwipeDirection.entries.forEach { direction ->
                                  val selected = ratingsUnlocked && direction == previewDirection || direction == scrollDirection
                                val centerAngle = when (direction) {
                                    RatingSwipeDirection.RIGHT -> 0f
                                    RatingSwipeDirection.DOWN -> 90f
                                    RatingSwipeDirection.LEFT -> 180f
                                    RatingSwipeDirection.UP -> 270f
                                }
                                drawArc(if (selected) selectedColor ?: outline else outline,
                                    centerAngle - 43f, 86f, false,
                                    topLeft = Offset(inset, inset),
                                    size = Size((size.width - inset * 2).coerceAtLeast(0f),
                                        (size.height - inset * 2).coerceAtLeast(0f)),
                                    style = Stroke((if (selected || flipPressed && flipEnabled) 3.dp else 1.dp).toPx()))
                            }
                        }.semantics {
                          onClick(label = flipAction, action = null)
                          stateDescription = if (scrollMode) when (scrollDirection) {
                              RatingSwipeDirection.UP -> "Scrolling forward; return to center or release to stop"
                              RatingSwipeDirection.DOWN -> "Scrolling back; return to center or release to stop"
                              else -> "Tap to flip; hold vertically to scroll; horizontal ratings disabled"
                            } else previewDirection?.takeIf { ratingsUnlocked }?.let { "${it.rating.name.lowercase()} selected; drag beyond the threshold and release to rate" }
                              ?: "Tap to flip; after reading the answer drag toward a rating and release; return to center to cancel"
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(8.dp),
                    border = null,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White, contentColor = Color.Black,
                        disabledContainerColor = Color.White,
                        disabledContentColor = Color.Black.copy(alpha = 0.65f),
                    ),
                 ) {
                      Column(
                          modifier = Modifier.graphicsLayer {
                              translationX = knobOffset.x
                              translationY = knobOffset.y
                          }.testTag("preparation_joystick_knob"),
                          horizontalAlignment = Alignment.CenterHorizontally,
                          verticalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                          val foreground = selectedColor ?: if (ratingsUnlocked || flipEnabled) Color.Black else Color.Black.copy(alpha = 0.65f)
                          when {
                              selected != null -> Text(selected.arrow, modifier = Modifier.clearAndSetSemantics {},
                                  style = MaterialTheme.typography.labelLarge, color = foreground)
                              ratingsUnlocked -> Icon(Icons.Filled.Gamepad, contentDescription = null,
                                  modifier = Modifier.size(24.dp), tint = foreground)
                              else -> Icon(painterResource(R.drawable.ic_preparation_flip), contentDescription = null,
                                  modifier = Modifier.size(24.dp), tint = foreground)
                          }
                          Text(selected?.let { labels.getValue(it.rating) }
                              ?: stringResource(if (ratingsUnlocked) R.string.preparation_drag else R.string.preparation_flip),
                              style = MaterialTheme.typography.labelLarge, color = foreground)
                     }
                 }
                 PadButton(Rating.EASY, enabled, onRate, Modifier.size(diameter), showDirectionHints, previewDirection, ratingsUnlocked)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                 if (scrollMode) ScrollPadButton(false, scrollBackwardEnabled, onScroll, Modifier.size(readingDiameter), scrollDirection == RatingSwipeDirection.DOWN)
                 else PadButton(Rating.HARD, enabled, onRate, Modifier.size(diameter), showDirectionHints, previewDirection, ratingsUnlocked)
            }
        }
        }
    }
}

@Composable
private fun ScrollPadButton(forward: Boolean, enabled: Boolean, onScroll: (Boolean) -> Unit, modifier: Modifier, selected: Boolean) {
    OutlinedButton(onClick = { onScroll(forward) }, enabled = enabled, shape = CircleShape,
        contentPadding = PaddingValues(8.dp), modifier = modifier.border(if (selected) 3.dp else 1.dp,
            MaterialTheme.colorScheme.outline, CircleShape)
            .testTag(if (forward) "preparation_scroll_forward" else "preparation_scroll_backward")
            .semantics { onClick(label = if (forward) "Move text up; read further" else "Move text down; read earlier", action = null) }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (forward) "Read ↓" else "Read ↑", style = MaterialTheme.typography.labelLarge)
            Text(if (forward) "further" else "earlier", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PadButton(rating: Rating, enabled: Boolean, onRate: (Rating) -> Unit, modifier: Modifier,
    showDirectionHint: Boolean, previewDirection: RatingSwipeDirection?, previewEnabled: Boolean = enabled) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
      val selected = previewEnabled && previewDirection?.rating == rating
    val selectionColor = preparationRatingOutlineColor(rating)
    RatingButton(
        rating = RatingButtonVariant.valueOf(rating.name),
        onClick = { onRate(rating) },
        enabled = enabled,
        directionHint = if (showDirectionHint) rating.swipeDirection().arrow else null,
        shape = CircleShape,
         preserveDisabledColor = false,
         disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
         disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        interactionSource = interactions,
        modifier = modifier.border(if (selected) 3.dp else 1.dp,
             if (selected) selectionColor else if (!enabled || pressed) MaterialTheme.colorScheme.outline else Color.Transparent, CircleShape)
            .testTag("preparation_rating_${rating.name.lowercase()}")
            .semantics { stateDescription = if (selected) "Selected: ${rating.name.lowercase()}" else rating.swipeDirection().instruction },
    )
}

@Composable
private fun preparationRatingOutlineColor(rating: Rating): Color {
    val colors = com.studysmart.app.core.ui.LocalStudySmartSemanticColors.current
    return when (rating) {
        Rating.AGAIN -> MaterialTheme.colorScheme.error
        Rating.HARD -> colors.warning
        Rating.GOOD -> colors.success
        Rating.EASY -> colors.info
    }
}
