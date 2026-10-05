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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
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
import com.studysmart.app.testing.unclippedOriginInRoot
import com.studysmart.app.testing.unclippedCenterInRoot
import com.studysmart.app.testing.unclippedControlBoundsInRoot
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
    fun savingFeedbackKeepsControlsFixedThroughRevealAndRating() = assertSavingGeometry(1f)

    @Test
    fun savingFeedbackDoesNotChangeLargeFontFallbackGeometry() = assertSavingGeometry(2f)

    private fun assertSavingGeometry(fontScale: Float) {
        val initial = initialSession().let { session -> session.copy(items = session.items.map {
            it.copy(questionSnapshot = "Short?", answerSnapshot = "Yes.")
        }) }
        val state = mutableStateOf(PreparationSessionUiState(isLoading = false, session = initial))
        composeRule.setContent {
            StudySmartTheme {
              CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    PreparationSessionScreen(state = state.value, onReveal = {}, onRate = {}, onRetry = {},
                        onClose = {}, onDiscard = {}, onCancelDiscard = {}, onLoadRetry = {}, onBackFallback = {},
                        onOpenSummary = {})
                }
              }
            }
        }
        val tags = listOf("preparation_rating_pad", "preparation_progress", "preparation_supporting_text") +
            Rating.entries.map { "preparation_rating_${it.name.lowercase()}" }
        fun snapshot() = tags.associateWith { composeRule.onNodeWithTag(it).unclippedControlBoundsInRoot() }
        val before = snapshot()
        composeRule.runOnIdle { state.value = state.value.copy(isSaving = true) }
        composeRule.onNodeWithTag("preparation_saving", useUnmergedTree = true).assertExists()
        assertEquals("First reveal save must not reflow the lower controls", before, snapshot())
        composeRule.runOnIdle {
            state.value = state.value.copy(isSaving = false,
                session = initial.copy(items = initial.items.map { it.copy(isRevealed = true) }))
        }
        composeRule.waitForIdle()
        val revealed = snapshot()
        if (fontScale == 1f) {
            assertEquals("Supporting-copy changes must not reflow the lower controls", before, revealed)
        }
        // Fallback may expand for a taller face, but routine saving must never change its geometry.
        assertEquals(before.getValue("preparation_supporting_text").height,
            revealed.getValue("preparation_supporting_text").height, 1f)
        composeRule.runOnIdle { state.value = state.value.copy(isSaving = true) }
        assertEquals("Rating save must not reflow the lower controls", revealed, snapshot())
        composeRule.runOnIdle { state.value = state.value.copy(isSaving = false) }
        assertEquals(revealed, snapshot())
    }

    @Test
    fun padBackgroundsStayStableWhilePressedReleasedAndDisabled() {
        val enabled = mutableStateOf(true)
        val dark = mutableStateOf(false)
        var taps = 0
        composeRule.setContent {
            StudySmartTheme(darkTheme = dark.value) {
                PreparationRatingPad(enabled = enabled.value, flipEnabled = enabled.value,
                    onRate = { taps++ }, onFlip = { taps++ })
            }
        }
        val tags = Rating.entries.map { "preparation_rating_${it.name.lowercase()}" } + "preparation_flip_button"
        fun background(tag: String): androidx.compose.ui.graphics.Color {
            val image = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap()
            return image[image.width / 2, image.height / 5]
        }
        listOf(false, true).forEach { darkTheme ->
            composeRule.runOnIdle { dark.value = darkTheme; enabled.value = true }
            val colors = tags.associateWith { background(it) }
            tags.forEach { tag ->
                val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                composeRule.onNodeWithTag(tag).performTouchInput { down(center) }
                composeRule.waitForIdle()
                assertEquals("Pressed background: $tag", colors.getValue(tag), background(tag))
                assertEquals(bounds, composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
                composeRule.onNodeWithTag(tag).performTouchInput { up() }
                composeRule.waitForIdle()
                assertEquals("Released background: $tag", colors.getValue(tag), background(tag))
            }
            composeRule.runOnIdle { enabled.value = false }
            tags.forEach { tag ->
                composeRule.onNodeWithTag(tag).assertIsNotEnabled()
                assertEquals("Disabled background: $tag", colors.getValue(tag), background(tag))
            }
        }
        assertEquals(10, taps)
    }

    @Test
    fun compactFacesKeepLowerControlsFixedAndColorsStable() {
        val initial = initialSession()
        val shortSession = initial.copy(items = initial.items.map {
            it.copy(questionSnapshot = "Short?", answerSnapshot = "Yes.")
        })
        val state = mutableStateOf(PreparationSessionUiState(isLoading = false, session = shortSession))
        composeRule.setContent {
            StudySmartTheme {
                PreparationSessionScreen(state = state.value,
                    onReveal = { state.value = state.value.copy(session = state.value.session!!.copy(
                        items = state.value.session!!.items.map { it.copy(isRevealed = true) })) },
                    onRate = {}, onRetry = {}, onClose = {}, onDiscard = {}, onCancelDiscard = {},
                    onLoadRetry = {}, onBackFallback = {}, onOpenSummary = {})
            }
        }
        fun bounds(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        fun background(tag: String): androidx.compose.ui.graphics.Color {
            val image = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap()
            return image[image.width / 2, image.height / 5]
        }
        val pad = bounds("preparation_rating_pad")
        val progress = bounds("preparation_progress")
        val supporting = bounds("preparation_supporting_text")
        val compact = bounds("preparation_card")
        val progressDensity = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        assertEquals(6f * progressDensity, progress.height, 1f)
        assertTrue(progress.bottom < compact.top)
        assertTrue(compact.bottom < supporting.top)
        assertTrue(supporting.bottom < pad.top)
        val tags = Rating.entries.map { "preparation_rating_${it.name.lowercase()}" } + "preparation_flip_button"
        val colors = tags.associateWith { background(it) }
        composeRule.onNodeWithTag("preparation_flip_button").performClick()
        composeRule.awaitNodeWithTag("preparation_answer", useUnmergedTree = true)
        composeRule.waitForIdle()
        tags.forEach { assertEquals(colors.getValue(it), background(it)) }
        composeRule.onNodeWithTag("preparation_flip_button").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            state.value = state.value.copy(session = state.value.session!!.copy(items = state.value.session!!.items.map {
                it.copy(questionSnapshot = List(100) { "Long question content" }.joinToString("\n"))
            }))
        }
        val long = bounds("preparation_card")
        assertTrue(long.height > compact.height)
        assertEquals(compact.width, long.width, 1f)
        assertEquals(pad, bounds("preparation_rating_pad"))
        assertEquals(progress, bounds("preparation_progress"))
        assertEquals(supporting, bounds("preparation_supporting_text"))
        assertTrue(long.bottom < pad.top)
    }

    @Test
    fun progressCardHeaderInstructionsAndOffsetPadFollowNewHierarchy() {
        val initial = initialSession()
        val template = initial.items.first()
        val repeat = template.copy(
            id = "repeat-item",
            questionId = "repeat-question",
            originalOrder = 1,
            queueSequence = 1,
            presentationCount = 1,
            isRevealed = false,
            isCompleted = false,
            lastRating = Rating.AGAIN,
        )
        val session = initial.copy(
            uniqueQuestionCount = 3,
            reviewCount = 1,
            items = listOf(
                template.copy(id = "unique-1", questionId = "unique-1-question", originalOrder = 0,
                    queueSequence = 0, isRevealed = true, isCompleted = false),
                repeat,
                template.copy(id = "unique-2", questionId = "unique-2-question", originalOrder = 2,
                    queueSequence = 2, isRevealed = false, isCompleted = false),
            ),
            reviewEvents = listOf(PreparationReviewEventModel(
                id = "repeat-review", questionId = "repeat-question", rating = Rating.AGAIN,
                reviewedAt = java.time.Instant.EPOCH,
            )),
        )
        composeRule.setContent {
            StudySmartTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    PreparationSessionScreen(
                        state = PreparationSessionUiState(isLoading = false, session = session),
                        onReveal = {}, onRate = {}, onRetry = {}, onClose = {}, onDiscard = {},
                        onCancelDiscard = {}, onLoadRetry = {}, onBackFallback = {}, onOpenSummary = {},
                    )
                }
            }
        }

        val viewport = composeRule.onNodeWithTag("preparation_study_viewport").fetchSemanticsNode().boundsInRoot
        val progress = composeRule.onNodeWithTag("preparation_progress").fetchSemanticsNode().boundsInRoot
        val card = composeRule.onNodeWithTag("preparation_card").fetchSemanticsNode().boundsInRoot
        val instruction = composeRule.onNodeWithTag("preparation_supporting_text").fetchSemanticsNode().boundsInRoot
        val pad = composeRule.onNodeWithTag("preparation_rating_pad").unclippedControlBoundsInRoot()
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        assertEquals(6f * density, progress.height, 1f)
        assertTrue(progress.top < card.top)
        assertTrue(card.bottom < instruction.top)
        assertTrue(instruction.bottom < pad.top)
        assertEquals(pad.center.x, instruction.center.x, 1f)
        assertEquals(pad.width, instruction.width, 1f)
        val flip = composeRule.onNodeWithTag("preparation_flip_button").unclippedControlBoundsInRoot()
        val good = composeRule.onNodeWithTag("preparation_rating_good").unclippedControlBoundsInRoot()
        val again = composeRule.onNodeWithTag("preparation_rating_again").unclippedControlBoundsInRoot()
        assertTrue(flip.width > good.width)
        assertTrue(good.bottom < flip.top)
        assertTrue(again.right < flip.left)
        assertEquals(12f * density, pad.center.x - viewport.center.x, 1f)
        assertTrue(viewport.right - pad.right >= 12f * density - 1f)

        composeRule.onNodeWithTag("preparation_card_back", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("preparation_remaining_count", useUnmergedTree = true).assertTextEquals("2 unique · 1 repeat left")
        composeRule.onNodeWithTag("preparation_flip_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preparation_card_front", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("preparation_remaining_count", useUnmergedTree = true).assertTextEquals("2 unique · 1 repeat left")
        composeRule.onNodeWithTag("preparation_count_again").assertDoesNotExist()
        composeRule.onNodeWithTag("preparation_card_position").assertDoesNotExist()
    }

    @Test
    fun eligiblePadPreviewsMoveAndTintOnlyCardThenCancel() {
        val initial = initialSession()
        var ratings = 0
        composeRule.setContent {
            StudySmartTheme {
                PreparationSessionScreen(state = PreparationSessionUiState(isLoading = false,
                    session = initial.copy(items = initial.items.map { it.copy(isRevealed = true) })),
                    onReveal = {}, onRate = { ratings++ }, onRetry = {}, onClose = {}, onDiscard = {},
                    onCancelDiscard = {}, onLoadRetry = {}, onBackFallback = {}, onOpenSummary = {})
            }
        }
        composeRule.waitForIdle()
        fun bounds(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val rest = composeRule.onNodeWithTag("preparation_card").unclippedOriginInRoot()
        fun cardBackground(): androidx.compose.ui.graphics.Color {
            val image = composeRule.onNodeWithTag("preparation_card").captureToImage().toPixelMap()
            return image[image.width / 2, 2]
        }
        val restingColor = cardBackground()
        val pad = bounds("preparation_rating_pad")
        val progress = bounds("preparation_progress")
        val vectors = listOf(Offset(-60f, 0f), Offset(60f, 0f), Offset(0f, -60f), Offset(0f, 60f))
        vectors.forEach { vector ->
            composeRule.onNodeWithTag("preparation_rating_pad").performTouchInput { down(center); moveBy(vector) }
            composeRule.waitForIdle()
            val moved = composeRule.onNodeWithTag("preparation_card").unclippedOriginInRoot()
            val preview = bounds("preparation_swipe_preview")
            val viewport = bounds("preparation_study_viewport")
            assertEquals(viewport.center.x, preview.center.x, 1f)
            assertEquals(viewport.center.y, preview.center.y, 1f)
            assertTrue(restingColor != cardBackground())
            if (vector.x != 0f) assertTrue((moved.x - rest.x) * vector.x > 0f)
            else assertTrue((moved.y - rest.y) * vector.y > 0f)
            assertEquals(pad, bounds("preparation_rating_pad"))
            assertEquals(progress, bounds("preparation_progress"))
            composeRule.onNodeWithTag("preparation_rating_pad").performTouchInput { cancel() }
            composeRule.waitForIdle()
            val restored = composeRule.onNodeWithTag("preparation_card").unclippedOriginInRoot()
            assertEquals(rest.x, restored.x, 1f)
            assertEquals(rest.y, restored.y, 1f)
            assertEquals(restingColor, cardBackground())
        }
        assertEquals(0, ratings)
    }

    @Test
    fun previewStaysAtViewportCenterDuringFallbackScroll() {
        val initial = initialSession()
        composeRule.setContent {
            StudySmartTheme {
                // Leave less than the 120dp normal reading budget after measured controls.
                Box(Modifier.width(360.dp).height(360.dp)) {
                    PreparationSessionScreen(state = PreparationSessionUiState(isLoading = false,
                        session = initial.copy(items = initial.items.map { it.copy(isRevealed = true) })),
                        onReveal = {}, onRate = {}, onRetry = {}, onClose = {}, onDiscard = {},
                        onCancelDiscard = {}, onLoadRetry = {}, onBackFallback = {}, onOpenSummary = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preparation_rating_pad").performScrollTo()
        composeRule.onNodeWithTag("preparation_rating_pad").performTouchInput { down(center); moveBy(Offset(60f, 0f)) }
        fun assertCentered() {
            val viewport = composeRule.onNodeWithTag("preparation_study_viewport").fetchSemanticsNode().boundsInRoot
            val preview = composeRule.onNodeWithTag("preparation_swipe_preview").fetchSemanticsNode().boundsInRoot
            assertEquals(viewport.center.x, preview.center.x, 1f)
            assertEquals(viewport.center.y, preview.center.y, 1f)
        }
        assertCentered()
        composeRule.onNodeWithTag("preparation_study_scroll", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -30f) }
        assertCentered()
        composeRule.onNodeWithTag("preparation_rating_pad").performTouchInput { cancel() }
        composeRule.onNodeWithTag("preparation_swipe_preview").assertDoesNotExist()
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
        composeRule.onNodeWithTag("preparation_rating_pad").performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
        }
        composeRule.onNodeWithTag("preparation_swipe_preview").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.onNodeWithTag("preparation_cancel_discard").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.onNodeWithTag("preparation_swipe_preview").assertDoesNotExist()
        assertEquals(resting, composeRule.onNodeWithTag("preparation_card").fetchSemanticsNode().boundsInRoot.left, 1f)
        composeRule.onNodeWithTag("preparation_rating_pad").performTouchInput { cancel() }
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
        val flip = composeRule.onNodeWithTag("preparation_flip_button").fetchSemanticsNode().boundsInRoot
        assertTrue(again.right < easy.left)
        assertTrue(again.right < flip.left && flip.right < easy.left)
        assertTrue(good.bottom < again.top)
        assertTrue(hard.top > easy.bottom)
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        listOf(again, easy, good, hard, flip).forEach {
            assertTrue(it.width >= 48f * density - 1f)
            assertTrue(it.height >= 48f * density - 1f)
            assertEquals(it.width, it.height, 1f)
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
        // A revealed session opens on its answer face; the fallback scroller keeps it reachable.
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).performScrollTo().assertExists()
    }

    @Test
    fun narrowLargeFontLayoutKeepsHorizontalProgressAndCircularControlsReachable() {
        val session = initialSession()
        composeRule.setContent {
            StudySmartTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    Box(Modifier.width(360.dp).height(600.dp)) {
                        PreparationSessionScreen(
                            state = PreparationSessionUiState(isLoading = false, session = session),
                            onReveal = {}, onRate = {}, onRetry = {}, onClose = {}, onDiscard = {},
                            onCancelDiscard = {}, onLoadRetry = {}, onBackFallback = {}, onOpenSummary = {},
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithTag("preparation_flip_button").performScrollTo().assertIsDisplayed().assertIsEnabled()
        val progress = composeRule.onNodeWithTag("preparation_progress").unclippedControlBoundsInRoot()
        val card = composeRule.onNodeWithTag("preparation_card").unclippedControlBoundsInRoot()
        val viewport = composeRule.onNodeWithTag("preparation_study_viewport").fetchSemanticsNode().boundsInRoot
        val instruction = composeRule.onNodeWithTag("preparation_supporting_text").unclippedControlBoundsInRoot()
        val pad = composeRule.onNodeWithTag("preparation_rating_pad").unclippedControlBoundsInRoot()
        assertTrue(progress.bottom < card.top)
        assertTrue(instruction.bottom < pad.top)
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val availableHorizontalClearance = (viewport.width - pad.width).coerceAtLeast(0f)
        val expectedClearance = minOf(12f * density, availableHorizontalClearance)
        assertTrue(viewport.right - pad.right >= expectedClearance - 1f)
        listOf("again", "good", "easy", "hard").forEach { rating ->
            val button = composeRule.onNodeWithTag("preparation_rating_$rating")
            button.performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            val bounds = button.unclippedControlBoundsInRoot()
            assertEquals(bounds.width, bounds.height, 1f)
        }
    }

    @Test
    fun uniqueReviewedProgressCanBeFullWhileRepeatIsStillActive() {
        val initial = initialSession()
        val question = initial.currentItem!!
        val session = initial.copy(
            reviewCount = 1,
            items = initial.items.map { item ->
                if (item.questionId == question.questionId) item.copy(presentationCount = 1) else item
            },
            reviewEvents = listOf(
                PreparationReviewEventModel("committed-again", question.questionId, Rating.AGAIN, java.time.Instant.EPOCH),
            ),
        )
        composeRule.setContent {
            StudySmartTheme {
                PreparationSessionScreen(
                    state = PreparationSessionUiState(isLoading = false, session = session),
                    onReveal = {}, onRate = {}, onRetry = {}, onClose = {}, onDiscard = {},
                    onCancelDiscard = {}, onLoadRetry = {}, onBackFallback = {}, onOpenSummary = {},
                )
            }
        }
        composeRule.onNodeWithTag("preparation_remaining_count", useUnmergedTree = true).assertTextEquals("1 repeat left")
        composeRule.onNodeWithTag("preparation_count_again").assertDoesNotExist()
        composeRule.onNodeWithTag("preparation_card_position").assertDoesNotExist()
        val semantics = composeRule.onNodeWithTag("preparation_progress").fetchSemanticsNode().config
        assertEquals(1f, semantics[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current, 0f)
        assertTrue(semantics[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
            .any { it.contains("1 repeat left") })
        composeRule.onNodeWithTag("preparation_card_front", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_summary_screen").assertDoesNotExist()
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
        composeRule.onNodeWithTag("preparation_flip_button").assertIsEnabled()
        composeRule.onNodeWithTag("preparation_remaining_count", useUnmergedTree = true).assertIsDisplayed()
        val initialBottom = composeRule.onNodeWithTag("preparation_card").fetchSemanticsNode().boundsInRoot.bottom
        val initialPad = composeRule.onNodeWithTag("preparation_rating_pad").fetchSemanticsNode().boundsInRoot
        val initialProgress = composeRule.onNodeWithTag("preparation_progress").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("preparation_flip_button").performClick()
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("preparation_remaining_count", useUnmergedTree = true).assertIsDisplayed()
        val answerBottom = composeRule.onNodeWithTag("preparation_card").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(initialBottom > 0f && answerBottom > 0f)
        assertEquals(initialPad, composeRule.onNodeWithTag("preparation_rating_pad").fetchSemanticsNode().boundsInRoot)
        assertEquals(initialProgress, composeRule.onNodeWithTag("preparation_progress").fetchSemanticsNode().boundsInRoot)
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
        val card = composeRule.onNodeWithTag("preparation_card").fetchSemanticsNode().boundsInRoot
        assertTrue(card.bottom < good.top)
        assertTrue(card.left <= again.left && card.right >= easy.right)
        val progress = composeRule.onNodeWithTag("preparation_progress").fetchSemanticsNode().boundsInRoot
        assertTrue(progress.top < card.top)
        val progressDensity = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        assertEquals(6f * progressDensity, progress.height, 1f)
        composeRule.onNodeWithTag("preparation_rating_distribution").assertDoesNotExist()
        composeRule.onNodeWithTag("preparation_count_again").assertDoesNotExist()
        composeRule.onNodeWithTag("preparation_card_position").assertDoesNotExist()

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
        composeRule.onNodeWithTag("preparation_flip_button").assertIsNotEnabled()
        composeRule.runOnIdle { state.value = state.value.copy(isSaving = false) }
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.onNodeWithTag("preparation_card").assertIsNotEnabled()
        composeRule.onNodeWithTag("preparation_flip_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("preparation_answer", useUnmergedTree = true).assertDoesNotExist()
        // The full-height layout fits without the fallback scroller; Retry is directly visible.
        composeRule.onNodeWithText("Retry").performClick()
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
        composeRule.onNodeWithTag("preparation_answer_body", useUnmergedTree = true).performScrollTo()
        composeRule.onNodeWithTag("preparation_content_scroll", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
        assertLastTextLineDisplayed(answer)
        composeRule.onNodeWithTag("preparation_card").performClick()
        composeRule.onNodeWithTag("preparation_question_body", useUnmergedTree = true).performScrollTo()
        composeRule.onNodeWithTag("preparation_content_scroll", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
        composeRule.onNodeWithTag("preparation_card_front", useUnmergedTree = true).assertExists()
        assertLastTextLineDisplayed(question)
        // Rating visibility is direct when the layout fits; scroll first when it does not.
        runCatching { composeRule.onNodeWithTag("preparation_rating_good").performScrollTo() }
        composeRule.onNodeWithTag("preparation_rating_good").assertIsDisplayed()
    }

    @Test fun answerEndUnlockSurvivesRestorationButResetsForRepeatPresentation() {
        val initial = initialSession().let { s -> s.copy(items = s.items.map {
            it.copy(answerSnapshot = "Long answer\n".repeat(80), isRevealed = true)
        }) }
        val state = mutableStateOf(PreparationSessionUiState(isLoading = false, session = initial))
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            StudySmartTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    PreparationSessionScreen(state.value, onReveal = {}, onRate = {}, onRetry = {},
                        onClose = {}, onDiscard = {}, onCancelDiscard = {}, onLoadRetry = {},
                        onBackFallback = {}, onOpenSummary = {})
                }
            }
        }
        composeRule.onNodeWithTag("preparation_scroll_forward").assertExists()
        composeRule.onNodeWithTag("preparation_content_scroll", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preparation_rating_good").assertIsEnabled()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preparation_rating_good").assertIsEnabled()
        composeRule.runOnIdle {
            state.value = state.value.copy(session = initial.copy(items = initial.items.map {
                it.copy(presentationCount = it.presentationCount + 1, isRevealed = true)
            }))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preparation_scroll_forward").assertExists()
        composeRule.onNodeWithTag("preparation_rating_easy").assertIsNotEnabled()
    }

    @Test fun longQuestionBottomNeverUnlocksAndShortAnswerNeedsFirstReveal() {
        val initial = initialSession().let { s -> s.copy(items = s.items.map {
            it.copy(questionSnapshot = "Long question\n".repeat(80), answerSnapshot = "Short answer", isRevealed = false)
        }) }
        val state = mutableStateOf(PreparationSessionUiState(isLoading = false, session = initial))
        composeRule.setContent {
            StudySmartTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    PreparationSessionScreen(state.value, onReveal = {
                        state.value = state.value.copy(session = initial.copy(items = initial.items.map { it.copy(isRevealed = true) }))
                    }, onRate = {}, onRetry = {}, onClose = {}, onDiscard = {}, onCancelDiscard = {},
                        onLoadRetry = {}, onBackFallback = {}, onOpenSummary = {})
                }
            }
        }
        composeRule.onNodeWithTag("preparation_content_scroll", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preparation_rating_easy").assertIsNotEnabled()
        composeRule.onNodeWithTag("preparation_flip_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preparation_rating_good").assertIsEnabled()
        composeRule.onNodeWithTag("preparation_content_scrollbar").assertDoesNotExist()
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
        // The full-height layout fits without the fallback scroller; Retry is directly visible.
        composeRule.onNodeWithText("Retry").assertIsEnabled().performClick()
        Rating.entries.forEach { rating ->
            composeRule.onNodeWithTag("preparation_rating_${rating.name.lowercase()}").assertIsEnabled()
        }
        assertEquals(1, retries)
        assertEquals(0, ratings)
    }
}
