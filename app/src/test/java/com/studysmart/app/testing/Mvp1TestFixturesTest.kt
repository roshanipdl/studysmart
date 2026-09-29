package com.studysmart.app.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class Mvp1TestFixturesTest {
    @Test
    fun fixtureMatrixCoversBothAcademicStructuresAndMoreThanThirtyQuestions() {
        val matrix = Mvp1TestFixtures.matrix()

        assertEquals(8, matrix.semesterCourse.periodIds.size)
        assertEquals(FixturePeriodType.SEMESTER, matrix.semesterCourse.periodType)
        assertEquals(3, matrix.yearlyCourse.periodIds.size)
        assertEquals(FixturePeriodType.YEAR, matrix.yearlyCourse.periodType)
        assertTrue(matrix.questions.size > 30)
        assertTrue(matrix.questions.any { it.topicId == null })
        assertTrue(matrix.questions.any { it.topicId != null })
    }

    @Test
    fun fixtureMatrixCoversLearningStatesAndStableIds() {
        val matrix = Mvp1TestFixtures.matrix(Instant.parse("2026-01-01T00:00:00Z"))

        assertEquals("local-user", matrix.localUserId)
        assertEquals(setOf("again", "hard", "good", "easy"), matrix.ratings.toSet())
        assertEquals(setOf("question-1", "question-21"), matrix.bookmarkedQuestionIds)
        assertTrue(matrix.schedules.any { it.nextReviewAt.isBefore(Instant.parse("2026-01-01T00:00:00Z")) })
        assertTrue(matrix.attempts.any { it.status == FixtureAttemptStatus.ACTIVE })
        assertTrue(matrix.attempts.any { it.status == FixtureAttemptStatus.SUBMITTED })
    }
}
