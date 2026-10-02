package com.studysmart.app.feature.preparation

import org.junit.Assert.*
import org.junit.Test

class PreparationChapterCoverageTest {
    @Test fun coverageRequiresEveryEligibleQuestionAndANonemptyChapter() {
        assertFalse(projectChapterCoverage(emptyList()).isCovered)
        assertFalse(projectChapterCoverage(listOf(true, false)).isCovered)
        assertTrue(projectChapterCoverage(listOf(true, true)).isCovered)
        assertEquals(1, projectChapterCoverage(listOf(true, false)).reviewedCount)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsCountsAboveTheEligiblePopulation() {
        PreparationChapterCoverage(1, 2)
    }
}
