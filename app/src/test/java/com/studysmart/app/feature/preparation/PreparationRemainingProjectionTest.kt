package com.studysmart.app.feature.preparation

import com.studysmart.app.core.model.Rating
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PreparationRemainingProjectionTest {
    @Test
    fun initialPendingQuestionsAreUniqueAndNotRepeats() {
        val session = sessionWithQueue(presentations = listOf(0, 0, 0))

        assertEquals(PreparationRemainingProjection(3, 0), projectPreparationRemaining(session))
        assertEquals("3 unique left", projectPreparationRemaining(session).label)
    }

    @Test
    fun pendingItemsWithPriorPresentationsAreRepeats() {
        val session = sessionWithQueue(presentations = listOf(0, 1, 2, 0, 1, 0))

        assertEquals(PreparationRemainingProjection(3, 3), projectPreparationRemaining(session))
        assertEquals("3 unique · 3 repeats left", projectPreparationRemaining(session).label)
    }

    @Test
    fun singularRepeatUsesSingularWording() {
        val projection = projectPreparationRemaining(sessionWithQueue(presentations = listOf(0, 1)))

        assertEquals("1 unique · 1 repeat left", projection.label)
        assertEquals("1 repeat left", projectPreparationRemaining(
            sessionWithQueue(presentations = listOf(1)),
        ).label)
    }

    @Test
    fun completedQuestionsAreExcludedAndEmptyQueueIsSafe() {
        val active = sessionWithQueue(presentations = listOf(0, 1, 2))
        val session = active.copy(items = active.items.map { it.copy(isCompleted = true) })
        val projection = projectPreparationRemaining(session)

        assertEquals(PreparationRemainingProjection(0, 0), projection)
        assertEquals("No questions left", projection.label)
    }

    @Test
    fun queueTransitionCountsAgainAndFirstHardAsRepeatsButNotGoodOrEasy() {
        val engine = PreparationQueueEngine()
        val initial = engine.createInitialState(
            PreparationPlan(selectionSeed = 10L, orderedQuestionIds = (1..4).map { "q$it" }),
        )
        val afterAgain = engine.rateCurrent(initial, Rating.AGAIN).state
        assertEquals(PreparationRemainingProjection(3, 1), remaining(afterAgain))

        val afterFirstHard = engine.rateCurrent(afterAgain, Rating.HARD).state
        assertEquals(PreparationRemainingProjection(2, 2), remaining(afterFirstHard))

        val afterGood = engine.rateCurrent(afterFirstHard, Rating.GOOD).state
        assertEquals(PreparationRemainingProjection(1, 2), remaining(afterGood))

        val afterEasy = engine.rateCurrent(afterGood, Rating.EASY).state
        assertEquals(PreparationRemainingProjection(0, 2), remaining(afterEasy))
        assertEquals("2 repeats left", remaining(afterEasy).label)
    }

    @Test
    fun subsequentHardDoesNotAddAnotherRepeat() {
        val engine = PreparationQueueEngine()
        var queue = queueWithOneQuestion()
        queue = engine.rateCurrent(queue, Rating.HARD).state
        assertEquals(PreparationRemainingProjection(0, 1), remaining(queue))

        queue = engine.rateCurrent(queue, Rating.HARD).state
        assertEquals(PreparationRemainingProjection(0, 0), remaining(queue))
    }

    @Test
    fun repeatedAgainRemainsOneQueuedRepeatRatherThanCountingHistoryEvents() {
        val engine = PreparationQueueEngine()
        var queue = engine.createInitialState(
            PreparationPlan(selectionSeed = 4L, orderedQuestionIds = listOf("q1", "q2", "q3", "q4")),
        )
        queue = engine.rateCurrent(queue, Rating.AGAIN).state
        repeat(3) { queue = engine.rateCurrent(queue, Rating.GOOD).state }
        queue = engine.rateCurrent(queue, Rating.AGAIN).state
        assertEquals(PreparationRemainingProjection(0, 1), remaining(queue))
        assertEquals(2, queue.itemsByQuestionId.getValue("q1").presentationCount)
        assertEquals("1 repeat left", remaining(queue).label)
    }

    private fun remaining(queue: PreparationQueueState): PreparationRemainingProjection =
        projectPreparationRemaining(sessionFromQueue(queue))

    private fun queueWithOneQuestion(): PreparationQueueState = PreparationQueueEngine().createInitialState(
        PreparationPlan(selectionSeed = 1L, orderedQuestionIds = listOf("q1")),
    )

    private fun sessionFromQueue(queue: PreparationQueueState): PreparationSessionModel {
        val base = sampleSession()
        val pendingPositions = queue.pendingQuestionIds.withIndex().associate { it.value to it.index }
        return base.copy(
            uniqueQuestionCount = queue.uniqueQuestionCount,
            reviewCount = queue.totalPresentationCount,
            items = queue.itemsByQuestionId.values.mapIndexed { index, item ->
                base.items.single().copy(
                    id = "item-$index",
                    questionId = item.questionId,
                    originalOrder = item.originalOrder,
                    queueSequence = pendingPositions[item.questionId] ?: Int.MAX_VALUE,
                    presentationCount = item.presentationCount,
                    hardRequeueUsed = item.hardRequeueUsed,
                    isCompleted = item.isCompleted,
                    lastRating = item.lastRating,
                )
            },
        )
    }

    private fun sessionWithQueue(presentations: List<Int>): PreparationSessionModel {
        val base = sampleSession()
        val items = presentations.mapIndexed { index, count ->
            base.items.single().copy(
                id = "item-$index",
                questionId = "question-$index",
                originalOrder = index,
                queueSequence = index,
                presentationCount = count,
                isCompleted = false,
            )
        }
        return base.copy(
            uniqueQuestionCount = presentations.size,
            reviewCount = presentations.sum(),
            items = items,
            reviewEvents = presentations.flatMapIndexed { index, count ->
                (0 until count).map { event ->
                    PreparationReviewEventModel(
                        id = "review-$index-$event",
                        questionId = "question-$index",
                        rating = Rating.AGAIN,
                        reviewedAt = Instant.EPOCH,
                    )
                }
            },
        )
    }
}
