package com.studysmart.app.feature.preparation

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparationSelectionEngineTest {
    private val selectedAt = Instant.parse("2026-01-01T12:00:00Z")
    private val engine = PreparationSelectionEngine()

    @Test fun configurableLimitsPreserveSelectionOrderingAndUniqueCap() {
        val candidates = (1..40).map { candidate("q-$it", it, nextReviewAt = selectedAt.minusSeconds(it.toLong())) }
        for (policy in PreparationSelectionPolicy.entries) {
            val full = engine.createPlan(candidates, policy, selectedAt, 42L)!!
            for (limit in listOf(1, 10, 30)) {
                val plan = engine.createPlan(candidates + candidates.first(), policy, selectedAt, 42L, limit)!!
                assertEquals(full.orderedQuestionIds.take(limit), plan.orderedQuestionIds)
                assertEquals(limit, plan.uniqueQuestionCount)
            }
            assertEquals(2, engine.createPlan(candidates.take(2), policy, selectedAt, 42L, 10)!!.uniqueQuestionCount)
            assertNull(engine.createPlan(emptyList(), policy, selectedAt, 42L, 10))
        }
        for (limit in listOf(0, 31)) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                engine.createPlan(candidates, PreparationSelectionPolicy.ADAPTIVE, selectedAt, 42L, limit)
            }
        }
    }

    @Test
    fun adaptiveSelectionUsesWeakDueUnseenStrongPriority() {
        val candidates = listOf(
            candidate("strong", 1),
            candidate("unseen", 2, isUnseen = true),
            candidate("due", 3, nextReviewAt = selectedAt.minusSeconds(1)),
            candidate("weak", 4, isWeak = true),
        )

        val plan = engine.createPlan(
            candidates = candidates,
            policy = PreparationSelectionPolicy.ADAPTIVE,
            selectedAt = selectedAt,
            selectionSeed = 11L,
        )

        assertEquals(listOf("weak", "due", "unseen", "strong"), plan?.orderedQuestionIds)
    }

    @Test
    fun weakCandidateWinsWhenItIsAlsoDue() {
        val plan = engine.createPlan(
            candidates = listOf(
                candidate("due", 1, nextReviewAt = selectedAt.minusSeconds(1)),
                candidate("weak-due", 2, isWeak = true, nextReviewAt = selectedAt.minusSeconds(1)),
            ),
            policy = PreparationSelectionPolicy.ADAPTIVE,
            selectedAt = selectedAt,
            selectionSeed = 1L,
        )

        assertEquals(listOf("weak-due", "due"), plan?.orderedQuestionIds)
    }

    @Test
    fun sameSeedProducesSameOrderRegardlessOfInputOrder() {
        val candidates = listOf(
            candidate("one", 1),
            candidate("two", 2),
            candidate("three", 3),
            candidate("four", 4),
        )

        val first = engine.createPlan(
            candidates = candidates,
            policy = PreparationSelectionPolicy.ADAPTIVE,
            selectedAt = selectedAt,
            selectionSeed = 42L,
        )
        val second = engine.createPlan(
            candidates = candidates.reversed(),
            policy = PreparationSelectionPolicy.ADAPTIVE,
            selectedAt = selectedAt,
            selectionSeed = 42L,
        )

        assertEquals(first, second)
    }

    @Test
    fun fixedSeedHasStableTieBreakOrder() {
        val plan = engine.createPlan(
            candidates = listOf(
                candidate("one", 1),
                candidate("two", 2),
                candidate("three", 3),
                candidate("four", 4),
            ),
            policy = PreparationSelectionPolicy.ADAPTIVE,
            selectedAt = selectedAt,
            selectionSeed = 42L,
        )

        assertEquals(listOf("two", "one", "four", "three"), plan?.orderedQuestionIds)
    }

    @Test
    fun dueReviewSelectionOrdersMostOverdueFirstWithoutSeededShuffle() {
        val plan = engine.createPlan(
            candidates = listOf(
                candidate("future", 1, nextReviewAt = selectedAt.plusSeconds(1)),
                candidate("recent", 2, nextReviewAt = selectedAt.minusSeconds(10)),
                candidate("oldest", 3, nextReviewAt = selectedAt.minusSeconds(100)),
                candidate("unseen", 4),
            ),
            policy = PreparationSelectionPolicy.DUE_REVIEW,
            selectedAt = selectedAt,
            selectionSeed = 99L,
        )

        assertEquals(listOf("oldest", "recent"), plan?.orderedQuestionIds)
    }

    @Test
    fun dueReviewTiesUseCurriculumOrderThenQuestionId() {
        val due = selectedAt.minusSeconds(10)
        val plan = engine.createPlan(
            candidates = listOf(
                candidate("z", 2, nextReviewAt = due),
                candidate("a", 1, nextReviewAt = due),
            ),
            policy = PreparationSelectionPolicy.DUE_REVIEW,
            selectedAt = selectedAt,
            selectionSeed = 99L,
        )

        assertEquals(listOf("a", "z"), plan?.orderedQuestionIds)
    }

    @Test
    fun duplicateQuestionIdsDoNotConsumeTheUniqueLimit() {
        val plan = engine.createPlan(
            candidates = listOf(
                candidate("one", 1),
                candidate("one", 1),
                candidate("two", 2),
            ),
            policy = PreparationSelectionPolicy.ADAPTIVE,
            selectedAt = selectedAt,
            selectionSeed = 1L,
        )

        assertEquals(2, plan?.uniqueQuestionCount)
        assertEquals(setOf("one", "two"), plan?.orderedQuestionIds?.toSet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun conflictingDuplicateQuestionIdsAreRejected() {
        engine.createPlan(
            candidates = listOf(candidate("same", 1), candidate("same", 2)),
            policy = PreparationSelectionPolicy.ADAPTIVE,
            selectedAt = selectedAt,
            selectionSeed = 1L,
        )
    }

    @Test
    fun selectionIsLimitedToThirtyUniqueQuestions() {
        val plan = engine.createPlan(
            candidates = (1..31).map { number -> candidate("question-$number", number) },
            policy = PreparationSelectionPolicy.ADAPTIVE,
            selectedAt = selectedAt,
            selectionSeed = 1L,
        )

        assertEquals(MAX_UNIQUE_QUESTIONS, plan?.uniqueQuestionCount)
        assertEquals(MAX_UNIQUE_QUESTIONS, plan?.orderedQuestionIds?.toSet()?.size)
    }

    @Test
    fun emptyAndNonDuePoolsReturnNoPlan() {
        assertNull(
            engine.createPlan(
                candidates = emptyList(),
                policy = PreparationSelectionPolicy.ADAPTIVE,
                selectedAt = selectedAt,
                selectionSeed = 1L,
            ),
        )
        assertNull(
            engine.createPlan(
                candidates = listOf(candidate("future", 1, nextReviewAt = selectedAt.plusSeconds(1))),
                policy = PreparationSelectionPolicy.DUE_REVIEW,
                selectedAt = selectedAt,
                selectionSeed = 1L,
            ),
        )
    }

    @Test
    fun topicOwnershipIsCarriedWithoutChangingSelectionRules() {
        val plan = engine.createPlan(
            candidates = listOf(
                candidate("direct", 1),
                candidate("topic", 2, topicId = "topic-1"),
            ),
            policy = PreparationSelectionPolicy.ADAPTIVE,
            selectedAt = selectedAt,
            selectionSeed = 1L,
        )

        assertTrue(plan!!.orderedQuestionIds.containsAll(listOf("direct", "topic")))
    }

    private fun candidate(
        questionId: String,
        curriculumOrder: Int,
        topicId: String? = null,
        isWeak: Boolean = false,
        isUnseen: Boolean = false,
        nextReviewAt: Instant? = null,
    ): PreparationCandidate = PreparationCandidate(
        questionId = questionId,
        topicId = topicId,
        curriculumOrder = curriculumOrder.toLong(),
        isWeak = isWeak,
        isUnseen = isUnseen,
        nextReviewAt = nextReviewAt,
    )
}
