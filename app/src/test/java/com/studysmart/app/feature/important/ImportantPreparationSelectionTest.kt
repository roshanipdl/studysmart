package com.studysmart.app.feature.important

import com.studysmart.app.feature.preparation.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ImportantPreparationSelectionTest {
    @Test fun savedOrderingUsesBookmarkTimeThenIdNotAdaptivePriorityOrCurriculum() {
        val now = Instant.EPOCH
        val candidates = listOf(
            PreparationCandidate("z", null, 0, true, false, now, now),
            PreparationCandidate("a", null, 5, false, true, null, now),
            PreparationCandidate("older", null, 10, false, true, null, now.minusSeconds(1)),
            PreparationCandidate("unsaved", null, 0, false, true, null),
        )
        val plan = PreparationSelectionEngine().createPlan(candidates, PreparationSelectionPolicy.IMPORTANT, now, 99, 30)!!
        assertEquals(listOf("older", "a", "z"), plan.orderedQuestionIds)
    }

    @Test fun savedPoolDeduplicatesAndRespectsRequestedCountAndThirtyLimit() {
        val candidates = (0..39).map { PreparationCandidate("q-$it", null, it.toLong(), false, true, null, Instant.EPOCH.plusSeconds(it.toLong())) }
        val engine = PreparationSelectionEngine()
        assertEquals(30, engine.createPlan(candidates + candidates, PreparationSelectionPolicy.IMPORTANT, Instant.EPOCH, 1)!!.uniqueQuestionCount)
        assertEquals(listOf("q-0", "q-1"), engine.createPlan(candidates, PreparationSelectionPolicy.IMPORTANT, Instant.EPOCH, 1, 2)!!.orderedQuestionIds)
    }
}
