package com.studysmart.app.feature.preparation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.ui.RatingButton
import com.studysmart.app.core.ui.RatingButtonVariant

@Composable
internal fun preparationRatingPadWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidth = RatingButtonVariant.entries.maxOf {
        measurer.measure(AnnotatedString(it.label), style = MaterialTheme.typography.labelLarge).size.width
    }
    val buttonWidth = maxOf(48.dp, with(density) { labelWidth.toDp() } + 16.dp)
    return maxOf(112.dp, buttonWidth * 2 + 4.dp)
}

@Composable
internal fun PreparationRatingPad(
    enabled: Boolean,
    onRate: (Rating) -> Unit,
    modifier: Modifier = Modifier,
) {
    // These positions describe physical swipe directions, not reading order.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier) {
        Column(
            Modifier.testTag("preparation_ratings").width(preparationRatingPadWidth()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PadButton(Rating.GOOD, enabled, onRate, Modifier.fillMaxWidth(0.5f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PadButton(Rating.AGAIN, enabled, onRate, Modifier.weight(1f))
                PadButton(Rating.EASY, enabled, onRate, Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PadButton(Rating.HARD, enabled, onRate, Modifier.fillMaxWidth(0.5f))
            }
        }
        }
    }
}

@Composable
private fun PadButton(rating: Rating, enabled: Boolean, onRate: (Rating) -> Unit, modifier: Modifier) {
    RatingButton(
        rating = RatingButtonVariant.valueOf(rating.name),
        onClick = { onRate(rating) },
        enabled = enabled,
        directionHint = rating.swipeDirection().arrow,
        modifier = modifier.testTag("preparation_rating_${rating.name.lowercase()}")
            .semantics { stateDescription = rating.swipeDirection().instruction },
    )
}
