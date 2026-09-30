package com.studysmart.app.feature.test

import com.studysmart.app.core.database.TestAttemptEntity
import com.studysmart.app.core.model.TestAttemptOriginType
import com.studysmart.app.core.model.TestAttemptScopeType
import com.studysmart.app.core.model.TestAttemptStatus
import com.studysmart.app.core.model.TestQuestionSource
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestAttemptTimerTest {
    private val startedAt = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun monotonicTimeContinuesWhenUtcClockMovesBackward() {
        val attempt = attempt(isTimed = true)
        val reading = TestAttemptTimer.read(
            attempt,
            startedAt.minusSeconds(30),
            elapsedRealtimeMillis = 40_000L,
        )

        assertTrue(reading.isValid)
        assertFalse(reading.isExpired)
        assertEquals(30_000L, reading.elapsedMillis)
        assertEquals(30L, reading.remainingSeconds)
    }

    @Test
    fun reachingPersistedDeadlineExpiresWithoutWaitingForMonotonicLimit() {
        val reading = TestAttemptTimer.read(
            attempt(isTimed = true),
            startedAt.plusSeconds(60),
            elapsedRealtimeMillis = 11_000L,
        )

        assertTrue(reading.isValid)
        assertTrue(reading.isExpired)
        assertEquals(0L, reading.remainingSeconds)
    }

    @Test
    fun untimedAttemptHasNoCountdown() {
        val reading = TestAttemptTimer.read(
            attempt(isTimed = false),
            startedAt.plusSeconds(15),
            elapsedRealtimeMillis = 25_000L,
        )

        assertTrue(reading.isValid)
        assertFalse(reading.isExpired)
        assertEquals(15_000L, reading.elapsedMillis)
        assertEquals(null, reading.remainingSeconds)
    }

    @Test
    fun invalidMonotonicAnchorFailsClosedForTimedAttempt() {
        val reading = TestAttemptTimer.read(
            attempt(isTimed = true),
            startedAt,
            elapsedRealtimeMillis = 9_999L,
        )

        assertFalse(reading.isValid)
        assertTrue(reading.isExpired)
        assertEquals(0L, reading.remainingSeconds)
    }

    @Test
    fun rebootFailsClosedEvenWhenNewBootUptimePassesSavedAnchor() {
        val reading = TestAttemptTimer.read(
            attempt(isTimed = true),
            startedAt.minusSeconds(10),
            elapsedRealtimeMillis = 30_000L,
            currentBootId = "new-boot",
        )

        assertFalse(reading.isValid)
        assertTrue(reading.isExpired)
        assertEquals(0L, reading.remainingSeconds)
    }

    private fun attempt(isTimed: Boolean): TestAttemptEntity = TestAttemptEntity(
        id = "attempt-1",
        userId = "local-user",
        courseId = "course-1",
        academicPeriodId = "period-1",
        subjectId = "subject-1",
        chapterId = "chapter-1",
        topicId = null,
        scopeType = TestAttemptScopeType.CHAPTER,
        scopeReferenceId = "chapter-1",
        originType = TestAttemptOriginType.CHAPTER,
        originReferenceId = "chapter-1",
        creationRequestId = "request-1",
        requestedQuestionCount = 1,
        actualQuestionCount = 1,
        questionSource = TestQuestionSource.RANDOM,
        selectionSeed = 1L,
        isTimed = isTimed,
        timeLimitSeconds = if (isTimed) 60L else null,
        startedAt = startedAt,
        startedAtElapsedRealtimeMillis = 10_000L,
        startedBootId = "test-boot",
        deadlineAt = if (isTimed) startedAt.plusSeconds(60) else null,
        submittedAt = null,
        completedAt = null,
        timeTakenSeconds = null,
        score = null,
        maxScore = null,
        percentage = null,
        status = TestAttemptStatus.IN_PROGRESS,
        completionReason = null,
        evaluatorVersion = null,
    )
}
