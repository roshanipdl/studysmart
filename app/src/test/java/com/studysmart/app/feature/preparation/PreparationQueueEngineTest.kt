package com.studysmart.app.feature.preparation

import com.studysmart.app.core.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparationQueueEngineTest {
    private val engine = PreparationQueueEngine()

    @Test
    fun initialStatePreservesPlanOrderAndUniqueCount() {
        val state = initialState("one", "two", "three")

        assertEquals(listOf("one", "two", "three"), state.pendingQuestionIds)
        assertEquals(3, state.uniqueQuestionCount)
        assertEquals(0, state.totalPresentationCount)
        assertFalse(state.isComplete)
    }

    @Test
    fun againRequeuesAfterThreePendingPresentations() {
        val transition = engine.rateCurrent(initialState("one", "two", "three", "four", "five"), Rating.AGAIN)

        assertEquals(listOf("two", "three", "four", "one", "five"), transition.state.pendingQuestionIds)
        assertTrue(transition.wasRequeued)
        assertFalse(transition.sessionCompleted)
        assertEquals(1, transition.state.itemsByQuestionId.getValue("one").presentationCount)
        assertEquals(5, transition.state.uniqueQuestionCount)
    }

    @Test
    fun againAppendsWhenFewerThanThreePendingRemain() {
        val transition = engine.rateCurrent(initialState("one", "two", "three"), Rating.AGAIN)

        assertEquals(listOf("two", "three", "one"), transition.state.pendingQuestionIds)
    }

    @Test
    fun againCanRequeueRepeatedly() {
        var state = initialState("one", "two", "three", "four")

        state = engine.rateCurrent(state, Rating.AGAIN).state
        state = engine.rateCurrent(state, Rating.GOOD).state
        state = engine.rateCurrent(state, Rating.GOOD).state
        state = engine.rateCurrent(state, Rating.GOOD).state
        val transition = engine.rateCurrent(state, Rating.AGAIN)

        assertEquals("one", transition.previousQuestionId)
        assertTrue(transition.wasRequeued)
        assertEquals(2, transition.state.itemsByQuestionId.getValue("one").presentationCount)
    }

    @Test
    fun firstHardRequeuesAfterSixPendingPresentations() {
        val transition = engine.rateCurrent(
            initialState("one", "two", "three", "four", "five", "six", "seven", "eight"),
            Rating.HARD,
        )

        assertEquals(listOf("two", "three", "four", "five", "six", "seven", "one", "eight"), transition.state.pendingQuestionIds)
        assertTrue(transition.wasRequeued)
        assertTrue(transition.state.itemsByQuestionId.getValue("one").hardRequeueUsed)
    }

    @Test
    fun firstHardAppendsWhenFewerThanSixPendingRemain() {
        val transition = engine.rateCurrent(initialState("one", "two", "three"), Rating.HARD)

        assertEquals(listOf("two", "three", "one"), transition.state.pendingQuestionIds)
    }

    @Test
    fun secondHardDoesNotRequeue() {
        var state = initialState("one")

        state = engine.rateCurrent(state, Rating.HARD).state
        val transition = engine.rateCurrent(state, Rating.HARD)

        assertTrue(transition.sessionCompleted)
        assertFalse(transition.wasRequeued)
        assertTrue(transition.state.itemsByQuestionId.getValue("one").isCompleted)
        assertEquals(2, transition.state.totalPresentationCount)
    }

    @Test
    fun hardAfterAgainStillReceivesItsFirstHardRequeue() {
        var state = initialState("one", "two", "three")

        state = engine.rateCurrent(state, Rating.AGAIN).state
        state = engine.rateCurrent(state, Rating.GOOD).state
        state = engine.rateCurrent(state, Rating.GOOD).state
        val transition = engine.rateCurrent(state, Rating.HARD)

        assertEquals("one", transition.previousQuestionId)
        assertTrue(transition.wasRequeued)
        assertTrue(transition.state.itemsByQuestionId.getValue("one").hardRequeueUsed)
    }

    @Test
    fun goodAndEasyCompleteWithoutRequeue() {
        val good = engine.rateCurrent(initialState("good"), Rating.GOOD)
        val easy = engine.rateCurrent(initialState("easy"), Rating.EASY)

        assertTrue(good.sessionCompleted)
        assertFalse(good.wasRequeued)
        assertTrue(easy.sessionCompleted)
        assertFalse(easy.wasRequeued)
    }

    @Test
    fun everyRatingIncrementsPresentationAndTotalCounts() {
        var state = initialState("one", "two")

        state = engine.rateCurrent(state, Rating.AGAIN).state
        state = engine.rateCurrent(state, Rating.GOOD).state
        state = engine.rateCurrent(state, Rating.GOOD).state

        assertEquals(3, state.totalPresentationCount)
        assertEquals(2, state.itemsByQuestionId.getValue("one").presentationCount)
        assertEquals(1, state.itemsByQuestionId.getValue("two").presentationCount)
        assertEquals(Rating.GOOD, state.itemsByQuestionId.getValue("one").lastRating)
        assertEquals(2, state.uniqueQuestionCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun ratingAnEmptyQueueIsRejected() {
        engine.rateCurrent(initialState("one").let { engine.rateCurrent(it, Rating.GOOD).state }, Rating.GOOD)
    }

    @Test(expected = IllegalArgumentException::class)
    fun initialStateRejectsMoreThanThirtyQuestions() {
        engine.createInitialState(
            PreparationPlan(
                selectionSeed = 1L,
                orderedQuestionIds = (1..31).map { "question-$it" },
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun initialStateRejectsDuplicateQuestions() {
        engine.createInitialState(
            PreparationPlan(
                selectionSeed = 1L,
                orderedQuestionIds = listOf("one", "one"),
            ),
        )
    }

    private fun initialState(vararg questionIds: String): PreparationQueueState =
        engine.createInitialState(
            PreparationPlan(selectionSeed = 1L, orderedQuestionIds = questionIds.toList()),
        )
}
