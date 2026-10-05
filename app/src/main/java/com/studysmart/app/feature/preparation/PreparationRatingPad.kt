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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import kotlin.math.hypot
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
internal fun preparationRatingButtonDiameter(showDirectionHints: Boolean = true): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val padding = with(density) { 16.dp.toPx() }
    val contentDiameter = RatingButtonVariant.entries.maxOf { rating ->
        val label = measurer.measure(AnnotatedString(rating.label), style = MaterialTheme.typography.labelLarge).size
        val arrow = measurer.measure(AnnotatedString("↑"), style = MaterialTheme.typography.labelLarge).size
        hypot((maxOf(label.width, if (showDirectionHints) arrow.width else 0) + padding).toDouble(),
            (label.height + (if (showDirectionHints) arrow.height else 0) + padding).toDouble())
    }
    val flip = measurer.measure(AnnotatedString("Flip"), style = MaterialTheme.typography.labelLarge).size
    val scroll = measurer.measure(AnnotatedString("Further"), style = MaterialTheme.typography.labelLarge).size
    return maxOf(48.dp, with(density) {
        maxOf(contentDiameter, hypot((flip.width + padding).toDouble(), (flip.height + padding).toDouble()),
            hypot((scroll.width + padding).toDouble(), (scroll.height * 2 + padding).toDouble()))
            .toFloat().toDp()
    })
}

/** +4dp beyond the 16dp gaps is rounding slack: px layout can then never coerce the last circle's width. */
@Composable
internal fun preparationRatingPadWidth(showDirectionHints: Boolean = true, flipExtra: Dp = 8.dp): Dp =
    preparationRatingButtonDiameter(showDirectionHints) * 3 + flipExtra + 20.dp

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
) {
    // These positions describe physical swipe directions, not reading order.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalRippleConfiguration provides null) {
        val diameter = preparationRatingButtonDiameter(showDirectionHints)
        val flipDiameter = diameter + flipExtra
        val outline = MaterialTheme.colorScheme.outline
        val selectedColor = previewDirection?.rating?.let { preparationRatingOutlineColor(it) }
        val flipInteractions = remember { MutableInteractionSource() }
        val flipPressed by flipInteractions.collectIsPressedAsState()
        Box(modifier) {
        Column(
            Modifier.testTag("preparation_ratings").width(preparationRatingPadWidth(showDirectionHints, flipExtra)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (scrollMode) ScrollPadButton(true, scrollForwardEnabled, onScroll, Modifier.size(diameter), scrollDirection == RatingSwipeDirection.UP)
                else PadButton(Rating.GOOD, enabled, onRate, Modifier.size(diameter), showDirectionHints, previewDirection)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PadButton(Rating.AGAIN, enabled, onRate, Modifier.size(diameter), showDirectionHints, previewDirection)
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
                                val selected = direction == previewDirection || direction == scrollDirection
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
                         onClick(label = if (showAnswer) "Show question" else "Show answer", action = null)
                          stateDescription = if (scrollMode) when (scrollDirection) {
                              RatingSwipeDirection.UP -> "Scrolling forward; return to center or release to stop"
                              RatingSwipeDirection.DOWN -> "Scrolling back; return to center or release to stop"
                              else -> "Tap to flip; hold vertically to scroll; horizontal ratings disabled"
                          } else previewDirection?.let { "${it.rating.name.lowercase()} selected; drag beyond the threshold and release to rate" }
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
                ) { Text("Flip", style = MaterialTheme.typography.labelLarge) }
                PadButton(Rating.EASY, enabled, onRate, Modifier.size(diameter), showDirectionHints, previewDirection)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (scrollMode) ScrollPadButton(false, scrollBackwardEnabled, onScroll, Modifier.size(diameter), scrollDirection == RatingSwipeDirection.DOWN)
                else PadButton(Rating.HARD, enabled, onRate, Modifier.size(diameter), showDirectionHints, previewDirection)
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
    showDirectionHint: Boolean, previewDirection: RatingSwipeDirection?) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val selected = previewDirection?.rating == rating
    val selectionColor = preparationRatingOutlineColor(rating)
    RatingButton(
        rating = RatingButtonVariant.valueOf(rating.name),
        onClick = { onRate(rating) },
        enabled = enabled,
        directionHint = if (showDirectionHint) rating.swipeDirection().arrow else null,
        shape = CircleShape,
        preserveDisabledColor = true,
        interactionSource = interactions,
        modifier = modifier.border(if (selected) 3.dp else 1.dp,
            if (selected) selectionColor else if (pressed && enabled) MaterialTheme.colorScheme.outline else Color.Transparent, CircleShape)
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
