package com.studysmart.app.feature.test

import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.TestQuestionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestQuestionSelectionEngineTest {
    private val engine = TestQuestionSelectionEngine()

    @Test
    fun allKeepsStableOrderAndDoesNotPersistASeed() {
        val candidates = candidates(4)
        val plan = engine.createPlan(
            candidates,
            TestDefaults(questionCount = 2, questionSource = TestQuestionSource.ALL),
            selectionSeed = null,
        )

        assertEquals(listOf("q1", "q2"), plan?.orderedQuestionIds)
        assertNull(plan?.selectionSeed)
    }

    @Test
    fun sampledSourcesFilterBeforeApplyingTheCount() {
        val candidates = candidates(4).mapIndexed { index, candidate ->
            candidate.copy(latestRating = if (index == 2) Rating.HARD else null)
        }
        val plan = engine.createPlan(
            candidates,
            TestDefaults(questionCount = 3, questionSource = TestQuestionSource.WEAK),
            selectionSeed = 42L,
        )

        assertEquals(listOf("q3"), plan?.orderedQuestionIds)
        assertEquals(42L, plan?.selectionSeed)
    }

    @Test
    fun unseenAndImportantDoNotTreatUnreviewedAsWeak() {
        val candidates = listOf(
            TestCandidate("unseen", null, false, null, null, false),
            TestCandidate("important", null, true, null, null, true),
            TestCandidate("weak-test", null, true, 49.9, null, false),
        )

        assertEquals(
            listOf("unseen"),
            engine.createPlan(
                candidates,
                TestDefaults(questionSource = TestQuestionSource.UNSEEN),
                1L,
            )?.orderedQuestionIds,
        )
        assertEquals(
            listOf("important"),
            engine.createPlan(
                candidates,
                TestDefaults(questionSource = TestQuestionSource.IMPORTANT),
                1L,
            )?.orderedQuestionIds,
        )
        assertEquals(
            listOf("weak-test"),
            engine.createPlan(
                candidates,
                TestDefaults(questionSource = TestQuestionSource.WEAK),
                1L,
            )?.orderedQuestionIds,
        )
    }

    private fun candidates(count: Int): List<TestCandidate> = (1..count).map { number ->
        TestCandidate(
            questionId = "q$number",
            latestRating = null,
            hasReview = true,
            latestTestPercentage = null,
            latestTestRequiresReview = null,
            isImportant = false,
        )
    }
}
