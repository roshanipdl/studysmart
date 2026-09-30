package com.studysmart.app.feature.preparation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.ui.StudySmartTheme
import com.studysmart.app.testing.awaitNodeWithTag
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PreparationScreenStateTest {
    @get:Rule val composeRule = createComposeRule()
    private var database: StudySmartDatabase? = null

    @After fun closeDatabase() { database?.close() }

    @Test
    fun discardFailureIsVisibleInsideTheDialogAndRetryAndCancelRemainAvailable() {
        val state = mutableStateOf(PreparationSessionUiState(
            showDiscardConfirmation = true, discardError = "Unable to discard this session.",
        ))
        var retries = 0
        var cancels = 0
        composeRule.setContent {
            StudySmartTheme {
                PreparationSessionScreen(
                    state = state.value, onReveal = {}, onRate = {}, onRetry = {},
                    onClose = {}, onDiscard = { retries++ },
                    onCancelDiscard = { cancels++; state.value = state.value.copy(showDiscardConfirmation = false) },
                    onLoadRetry = {}, onBackFallback = {}, onOpenSummary = {},
                )
            }
        }
        composeRule.onNodeWithTag("preparation_discard_error").assertIsDisplayed()
        composeRule.onNodeWithText("Retry discard").assertIsEnabled().performClick()
        assertEquals(1, retries)
        composeRule.onNodeWithTag("preparation_cancel_discard").assertIsEnabled().performClick()
        assertEquals(1, cancels)
    }

    @Test
    fun summaryLoadingFailureAndMissingStatesOfferOnlyValidRecoveryActions() {
        val state = mutableStateOf(PreparationSummaryUiState())
        var retries = 0
        var backs = 0
        composeRule.setContent {
            StudySmartTheme {
                PreparationSummaryScreen(
                    state = state.value,
                    onRetry = {
                        retries++
                        state.value = PreparationSummaryUiState(isLoading = false, isMissingContent = true)
                    },
                    onRetryReadiness = {}, onBack = { backs++ },
                )
            }
        }
        composeRule.onNodeWithTag("preparation_summary_loading").assertIsDisplayed()
        composeRule.runOnIdle {
            state.value = PreparationSummaryUiState(isLoading = false, error = "Unable to load summary.")
        }
        composeRule.onNodeWithTag("preparation_summary_error").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.onNodeWithText("This content is no longer available.").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Retry")
            .fetchSemanticsNodes(atLeastOneRootRequired = false).size)
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, retries)
        assertEquals(1, backs)
    }

    @Test
    fun failedSaveDisablesAllRatingsUntilRetryClearsTheError() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = createInMemoryStudySmartDatabase(context).also { database = it }
        val repository = coreAlphaTestContainer(context, db).preparationRepository
        val session = runBlocking {
            val created = repository.createSession(PreparationSessionRequest(
                PreparationScopeType.CHAPTER, "chapter-bca-s1-dbms-01",
                PreparationOriginType.CHAPTER, "chapter-bca-s1-dbms-01", "screen-state",
            )) as PreparationCreationResult.Created
            val initial = repository.observeSession(created.sessionId).first()!!
            repository.revealCurrent(created.sessionId, initial.currentItem!!.questionId)
            repository.observeSession(created.sessionId).first { it?.currentItem?.isRevealed == true }!!
        }
        val state = mutableStateOf(PreparationSessionUiState(
            isLoading = false, session = session, mutationError = "Unable to save this rating.",
        ))
        var ratings = 0
        var retries = 0
        composeRule.setContent {
            StudySmartTheme {
                PreparationSessionScreen(
                    state = state.value, onReveal = {}, onRate = { ratings++ },
                    onRetry = { retries++; state.value = state.value.copy(mutationError = null) },
                    onClose = {}, onDiscard = {}, onCancelDiscard = {}, onLoadRetry = {},
                    onBackFallback = {}, onOpenSummary = {},
                )
            }
        }
        composeRule.awaitNodeWithTag("preparation_answer")
        Rating.entries.forEach { rating ->
            composeRule.onNodeWithTag("preparation_rating_${rating.name.lowercase()}").assertIsNotEnabled()
        }
        composeRule.onNodeWithText("Retry").performScrollTo().assertIsEnabled().performClick()
        Rating.entries.forEach { rating ->
            composeRule.onNodeWithTag("preparation_rating_${rating.name.lowercase()}").assertIsEnabled()
        }
        assertEquals(1, retries)
        assertEquals(0, ratings)
    }
}
