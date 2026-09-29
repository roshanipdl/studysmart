package com.studysmart.app.feature.preparation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.ui.EmptyState
import com.studysmart.app.core.ui.ErrorState
import com.studysmart.app.core.ui.LoadingState
import com.studysmart.app.core.ui.LocalStudySmartSemanticColors
import com.studysmart.app.core.ui.MissingContentScreen
import com.studysmart.app.core.ui.PrimaryButton
import com.studysmart.app.core.ui.RatingButton
import com.studysmart.app.core.ui.RatingButtonVariant
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
    val item = session?.currentItem
    var ratingTransition by remember { mutableStateOf<RatingTransition?>(null) }
    val currentCardKey = item?.cardKey()
    LaunchedEffect(currentCardKey, state.mutationError) {
        val transition = ratingTransition
        if (transition != null &&
            transition.fromKey != currentCardKey &&
            state.mutationError == null
        ) {
            delay(CARD_SWIPE_DURATION_MILLIS.toLong())
            ratingTransition = null
        }
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
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                val completedCount = session.items.count { it.isCompleted }
                val progress = if (session.uniqueQuestionCount == 0) {
                    0f
                } else {
                    completedCount.toFloat() / session.uniqueQuestionCount.toFloat()
                }
                Text(
                    text = "Card ${session.reviewCount + 1} · ${session.uniqueQuestionCount} unique",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("preparation_card_position"),
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .testTag("preparation_progress"),
                )
                val activeRatingTransition = ratingTransition?.takeIf {
                    it.fromKey != currentCardKey && state.mutationError == null
                }
                val transitionBackground = activeRatingTransition?.rating?.let { rating ->
                    val semanticColors = LocalStudySmartSemanticColors.current
                    when (rating) {
                        Rating.AGAIN -> MaterialTheme.colorScheme.errorContainer
                        Rating.HARD -> semanticColors.warningContainer
                        Rating.GOOD -> semanticColors.successContainer
                        Rating.EASY -> semanticColors.infoContainer
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(transitionBackground ?: MaterialTheme.colorScheme.background)
                        .then(
                            activeRatingTransition?.let {
                                Modifier.testTag(
                                    "preparation_rating_transition_${it.rating.name.lowercase()}",
                                )
                            } ?: Modifier
                        ),
                ) {
                    AnimatedContent(
                        targetState = item,
                        modifier = Modifier.fillMaxWidth(),
                        transitionSpec = {
                            (slideInHorizontally(
                                animationSpec = tween(CARD_SWIPE_DURATION_MILLIS),
                                initialOffsetX = { it },
                            ) + fadeIn(animationSpec = tween(CARD_SWIPE_DURATION_MILLIS))) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(CARD_SWIPE_DURATION_MILLIS),
                                    targetOffsetX = { -it },
                                ) + fadeOut(animationSpec = tween(CARD_SWIPE_DURATION_MILLIS)))
                        },
                        contentKey = { card -> card.cardKey() },
                        label = "preparation_card_transition",
                    ) { animatedItem ->
                        PreparationFlashcard(
                            item = animatedItem,
                            topicLabel = animatedItem.topicLabel(session),
                            onReveal = onReveal,
                            enabled = !state.isSaving && state.mutationError == null && activeRatingTransition == null,
                            modifier = Modifier.testTag("preparation_card"),
                        )
                    }
                }
                Text(
                    text = if (item.isRevealed) {
                        "Rate the card after viewing the answer."
                    } else {
                        "Think first, then reveal the answer."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.isRevealed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preparation_ratings"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RatingButton(
                            rating = RatingButtonVariant.AGAIN,
                            onClick = {
                                ratingTransition = RatingTransition(Rating.AGAIN, currentCardKey)
                                onRate(Rating.AGAIN)
                            },
                            enabled = !state.isSaving && state.mutationError == null && activeRatingTransition == null,
                            modifier = Modifier.weight(1f).testTag("preparation_rating_again"),
                        )
                        RatingButton(
                            rating = RatingButtonVariant.HARD,
                            onClick = {
                                ratingTransition = RatingTransition(Rating.HARD, currentCardKey)
                                onRate(Rating.HARD)
                            },
                            enabled = !state.isSaving && state.mutationError == null && activeRatingTransition == null,
                            modifier = Modifier.weight(1f).testTag("preparation_rating_hard"),
                        )
                        RatingButton(
                            rating = RatingButtonVariant.GOOD,
                            onClick = {
                                ratingTransition = RatingTransition(Rating.GOOD, currentCardKey)
                                onRate(Rating.GOOD)
                            },
                            enabled = !state.isSaving && state.mutationError == null && activeRatingTransition == null,
                            modifier = Modifier.weight(1f).testTag("preparation_rating_good"),
                        )
                        RatingButton(
                            rating = RatingButtonVariant.EASY,
                            onClick = {
                                ratingTransition = RatingTransition(Rating.EASY, currentCardKey)
                                onRate(Rating.EASY)
                            },
                            enabled = !state.isSaving && activeRatingTransition == null,
                            modifier = Modifier.weight(1f).testTag("preparation_rating_easy"),
                        )
                    }
                }
                state.mutationError?.let { message ->
                    ErrorState(
                        message = message,
                        onRetry = onRetry,
                        modifier = Modifier.testTag("preparation_save_error"),
                    )
                }
                if (state.isSaving) {
                    LoadingState(
                        message = "Saving",
                        modifier = Modifier.testTag("preparation_saving"),
                    )
                }
            }
        }
    }

    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelDiscard,
            title = { Text("Discard this session?") },
            text = { Text("Committed review history will remain, but this active session will be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = onDiscard,
                    enabled = !state.isSaving,
                    modifier = Modifier.testTag("preparation_discard"),
                ) { Text("Discard session") }
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

private data class RatingTransition(
    val rating: Rating,
    val fromKey: PreparationCardKey?,
)

private fun PreparationItemModel.cardKey(): PreparationCardKey = PreparationCardKey(
    questionId = questionId,
    presentationCount = presentationCount,
)

@Composable
private fun PreparationFlashcard(
    item: PreparationItemModel,
    topicLabel: String,
    onReveal: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rotation by animateFloatAsState(
        targetValue = if (item.isRevealed) 180f else 0f,
        animationSpec = tween(FLIP_DURATION_MILLIS),
        label = "preparation_card_flip",
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density.density
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (rotation <= 90f) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = topicLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = item.questionSnapshot,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                PrimaryButton(
                    text = "Show answer",
                    onClick = onReveal,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preparation_reveal"),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { rotationY = 180f }
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Answer",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("preparation_card_back"),
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preparation_answer"),
                ) {
                    Text(
                        text = item.answerSnapshot,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private const val FLIP_DURATION_MILLIS = 550
private const val CARD_SWIPE_DURATION_MILLIS = 420

@Composable
fun PreparationSummaryScreen(
    state: PreparationSummaryUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
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
                state.session == null -> MissingContentScreen(
                    title = "Preparation summary",
                    onBack = onBack,
                )
                else -> SummaryContent(session = state.session, onBack = onBack)
            }
        }
    }
}

@Composable
private fun SummaryContent(
    session: PreparationSessionModel,
    onBack: () -> Unit,
) {
    val counts = session.reviewEvents.groupingBy { it.rating }.eachCount()
    val needsImprovement = session.items.count {
        it.lastRating == Rating.AGAIN || it.lastRating == Rating.HARD
    }
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
            value = session.uniqueQuestionCount.toString(),
            label = "Unique questions",
            modifier = Modifier.weight(1f),
        )
        ResultMetric(
            value = session.reviewCount.toString(),
            label = "Presentations",
            modifier = Modifier.weight(1f),
        )
        ResultMetric(
            value = needsImprovement.toString(),
            label = "Needs improvement",
            modifier = Modifier.weight(1f),
        )
    }
    Text("Rating history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryMetric("Again", counts[Rating.AGAIN] ?: 0, Modifier.weight(1f))
        SummaryMetric("Hard", counts[Rating.HARD] ?: 0, Modifier.weight(1f))
        SummaryMetric("Good", counts[Rating.GOOD] ?: 0, Modifier.weight(1f))
        SummaryMetric("Easy", counts[Rating.EASY] ?: 0, Modifier.weight(1f))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preparation_readiness_slot"),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Exam readiness estimate",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Available after evaluated test results are added.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
    Text(
        "Recently reviewed",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag("preparation_recently_reviewed"),
    )
    session.items
        .sortedByDescending { it.lastPresentedAt }
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
    PrimaryButton(
        text = "Back",
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
