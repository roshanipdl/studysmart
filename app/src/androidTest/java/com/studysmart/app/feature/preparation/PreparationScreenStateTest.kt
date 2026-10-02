package com.studysmart.app.feature.preparation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PreparationScreenStateTest {
    @get:Rule val composeRule = createComposeRule()
    private var database: StudySmartDatabase? = null

    @After fun closeDatabase() { database?.close() }

    private fun initialSession(): PreparationSessionModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = createInMemoryStudySmartDatabase(context).also { database = it }
        val repository = coreAlphaTestContainer(context, db).preparationRepository
        return runBlocking {
            val created = repository.createSession(PreparationSessionRequest(
                PreparationScopeType.TOPIC, "topic-bca-s1-dbms-01",
                PreparationOriginType.TOPIC, "topic-bca-s1-dbms-01", "flip-screen-state",
            )) as PreparationCreationResult.Created
            repository.observeSession(created.sessionId).first()!!
        }
    }

    @Test
    fun openingDiscardDuringDragClearsPreviewWithoutRating() {
        val initial = initialSession()
        val state = mutableStateOf(PreparationSessionUiState(isLoading = false,
            session = initial.copy(items = initial.items.map { it.copy(isRevealed = true) })))
        var ratings = 0
        composeRule.setContent {
            StudySmartTheme {
                PreparationSessionScreen(
                    state = state.value, onReveal = {}, onRate = { ratings++ }, onRetry = {},
                    onClose = { state.value = state.value.copy(showDiscardConfirmation = true) },
                    onDiscard = {}, onCancelDiscard = { state.value = state.value.copy(showDiscardConfirmation = false) },
                    onLoadRetry = {}, onBackFallback = {}, onOpenSummary = {},
                )
            }
        }
        val resting = composeRule.onNodeWithTag("preparation_card").fetchSemanticsNode().boundsInRoot.left
        composeRule.onNodeWithTag("preparation_card").performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
        }
        composeRule.onNodeWithTag("preparation_swipe_preview").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.onNodeWithTag("preparation_cancel_discard").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.onNodeWithTag("preparation_swipe_preview").assertDoesNotExist()
        assertEquals(resting, composeRule.onNodeWithTag("preparation_card").fetchSemanticsNode().boundsInRoot.left, 1f)
        composeRule.onNodeWithTag("preparation_card").performTouchInput { cancel() }
        assertEquals(0, ratings)
    }

    @Test
    fun padKeepsPhysicalDirectionsAndAccessibleTargetsInRtl() {
        composeRule.setContent {
            StudySmartTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PreparationRatingPad(enabled = true, onRate = {})
                }
            }
        }
        val again = composeRule.onNodeWithTag("preparation_rating_again").fetchSemanticsNode().boundsInRoot
        val easy = composeRule.onNodeWithTag("preparation_rating_easy").fetchSemanticsNode().boundsInRoot
        val good = composeRule.onNodeWithTag("preparation_rating_good").fetchSemanticsNode().boundsInRoot
        val hard = composeRule.onNodeWithTag("preparation_rating_hard").fetchSemanticsNode().boundsInRoot
        assertTrue(again.right < easy.left)
        assertTrue(good.bottom < again.top)
        assertTrue(hard.top > easy.bottom)
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        listOf(again, easy, good, hard).forEach {
            assertTrue(it.width >= 48f * density - 1f)
            assertTrue(it.height >= 48f * density - 1f)
        }
    }

    @Test
    fun shortViewportFallsBackToScrollingAndKeepsRetryReachable() {
        val session = initialSession().let { it.copy(items = it.items.map { item -> item.copy(isRevealed = true) }) }
        composeRule.setContent {
            StudySmartTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                Box(Modifier.width(360.dp).height(280.dp)) {
                    PreparationSessionScreen(
                        state = PreparationSessionUiState(isLoading = false, session = session, mutationError = "Save failed"),
                        onReveal = {}, onRate = {}, onRetry = {}, onClose = {}, onDiscard = {},
                        onCancelDiscard = {}, onLoadRetry = {}, onBackFallback = {}, onOpenSummary = {},
                    )
                }
                }
            }
        }
        composeRule.onNodeWithText("Retry").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_rating_hard").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag("preparation_card_front", useUnmergedTree = true).performScrollTo().assertExists()
    }

    @Test
    fun wholeCardFlipsBothWaysAndKeepsRatingsWithoutAnotherReveal() {
        val state = mutableStateOf(PreparationSessionUiState(isLoading = false, session = initialSession()))
        var reveals = 0
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            StudySmartTheme {
                PreparationSessionScreen(
                    state = state.value,
                    onReveal = {
                        reveals++
                        val session = state.value.session!!
                        state.value = state.value.copy(session = session.copy(
                            items = session.items.map { it.copy(isRevealed = true) },
                        ))
                    },
                    onRate = {}, onRetry = {}, onClose = {}, onDiscard = {},
                    onCancelDiscard = {}, onLoadRetry = {}, onBackFallback = {}, onOpenSummary = {},
                )
            }
        }
        composeRule.onNodeWithText("Show answer").assertDoesNotExist()
        composeRule.onNodeWithTag("preparation_rating_good").assertIsNotEnabled()
        composeRule.onNodeWithTag("preparation_flip_hint", useUnmergedTree = true).assertIsDisplayed()
        val initialBottom = composeRule.onNodeWithTag("preparation_card").fetchSemanticsNode().boundsInRoot.bottom
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_flip_hint", useUnmergedTree = true).assertIsDisplayed()
        val answerBottom = composeRule.onNodeWithTag("preparation_card").fetchSemanticsNode().boundsInRoot.bottom
        assertEquals(initialBottom, answerBottom, 1f)
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.onNodeWithTag("preparation_card_front", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_ratings").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("preparation_card_front", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(1, reveals)

        val good = composeRule.onNodeWithTag("preparation_rating_good").fetchSemanticsNode().boundsInRoot
        val easy = composeRule.onNodeWithTag("preparation_rating_easy").fetchSemanticsNode().boundsInRoot
        val again = composeRule.onNodeWithTag("preparation_rating_again").fetchSemanticsNode().boundsInRoot
        val hard = composeRule.onNodeWithTag("preparation_rating_hard").fetchSemanticsNode().boundsInRoot
        assertTrue(good.bottom < again.top)
        assertEquals(again.top, easy.top, 1f)
        assertTrue(again.right < easy.left)
        assertTrue(easy.bottom < hard.top)
        assertEquals(good.center.x, hard.center.x, 1f)
        assertTrue(composeRule.onNodeWithTag("preparation_card").fetchSemanticsNode().boundsInRoot.right < again.left)
        composeRule.onNodeWithTag("preparation_count_again").assertTextEquals("0")

        composeRule.runOnIdle {
            val session = state.value.session!!
            state.value = state.value.copy(session = session.copy(items = session.items.map {
                it.copy(presentationCount = it.presentationCount + 1, isRevealed = false)
            }))
        }
        composeRule.onNodeWithTag("preparation_card_front", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_rating_good").assertIsNotEnabled()
    }

    @Test
    fun failedRevealAndSavingBlockCardUntilRetrySucceeds() {
        val state = mutableStateOf(PreparationSessionUiState(isLoading = false, session = initialSession()))
        var reveals = 0
        composeRule.setContent {
            StudySmartTheme {
                PreparationSessionScreen(
                    state = state.value,
                    onReveal = {
                        reveals++
                        state.value = state.value.copy(mutationError = "Unable to reveal the answer.")
                    },
                    onRetry = {
                        val session = state.value.session!!
                        state.value = state.value.copy(mutationError = null, session = session.copy(
                            items = session.items.map { it.copy(isRevealed = true) },
                        ))
                    },
                    onRate = {}, onClose = {}, onDiscard = {}, onCancelDiscard = {},
                    onLoadRetry = {}, onBackFallback = {}, onOpenSummary = {},
                )
            }
        }
        composeRule.runOnIdle { state.value = state.value.copy(isSaving = true) }
        composeRule.onNodeWithTag("preparation_card").assertIsNotEnabled()
        composeRule.runOnIdle { state.value = state.value.copy(isSaving = false) }
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.onNodeWithTag("preparation_card").assertIsNotEnabled()
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Retry").performScrollTo().performClick()
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(1, reveals)
    }

    @Test
    fun longQuestionAndAnswerEndsRemainVisibleWithLargeFontsAndReachableRatings() {
        val initial = initialSession()
        val question = "Study question\n".repeat(80) + "End of question"
        val answer = "Study answer\n".repeat(80) + "End of answer"
        val session = initial.copy(items = initial.items.map {
            it.copy(questionSnapshot = question, answerSnapshot = answer, isRevealed = true)
        })
        composeRule.setContent {
            StudySmartTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    PreparationSessionScreen(
                        state = PreparationSessionUiState(isLoading = false, session = session),
                        onReveal = {}, onRate = {}, onRetry = {}, onClose = {}, onDiscard = {},
                        onCancelDiscard = {}, onLoadRetry = {}, onBackFallback = {}, onOpenSummary = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("preparation_rating_good").assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_study_scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
        assertLastTextLineDisplayed(answer)
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.onNodeWithTag("preparation_study_scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
        composeRule.onNodeWithTag("preparation_card_front", useUnmergedTree = true).assertExists()
        assertLastTextLineDisplayed(question)
        composeRule.onNodeWithTag("preparation_rating_good").assertIsDisplayed()
    }

    private fun assertLastTextLineDisplayed(text: String) {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true)
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        val semantics = node.fetchSemanticsNode()
        val lineTop = semantics.positionInRoot.y + layout.getLineTop(layout.lineCount - 1)
        val lineBottom = semantics.positionInRoot.y + layout.getLineBottom(layout.lineCount - 1)
        val viewport = composeRule.onNodeWithTag("preparation_study_viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("The final study-text line must not be clipped above the viewport", lineTop >= viewport.top)
        assertTrue("The final study-text line must not be clipped below the viewport", lineBottom <= viewport.bottom)
    }

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
        composeRule.awaitNodeWithTag("preparation_answer", useUnmergedTree = true)
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
