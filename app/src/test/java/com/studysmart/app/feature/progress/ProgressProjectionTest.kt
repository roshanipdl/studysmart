package com.studysmart.app.feature.progress

import com.studysmart.app.core.common.localStudyDay
import com.studysmart.app.core.database.ReadinessQuestionRow
import com.studysmart.app.core.model.LearningState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ProgressProjectionTest {
    @Test fun testOnlyActivityNeverCountsAsCoverageOrSuccessfulLearning() {
        val question = ReadinessQuestionRow("q", null, null, null, false, Instant.EPOCH, true)
        val result = projectProgress(listOf(question), 0, true)
        assertTrue(result.hasActivity)
        assertEquals(0, result.studiedCount)
        assertEquals(0, result.learningCount)
        assertEquals(0, result.dailyRatedCount)
    }

    @Test fun duplicateQuestionsCountOnceAndLatestTestWarningUsesSharedState() {
        val rated = ReadinessQuestionRow("q", LearningState.LEARNING, Instant.EPOCH, Instant.EPOCH, true, null, null)
        assertEquals(ProgressMetrics(1, 1, 1, 1, true), projectProgress(listOf(rated, rated), 1, false))
        assertEquals(0, projectProgress(listOf(rated.copy(latestTestSubmittedAt = Instant.EPOCH.plusSeconds(1), latestTestRequiresReview = true)), 1, true).learningCount)
    }

    @Test fun emptyAndUnstudiedPoolsHaveNoActivity() {
        assertFalse(projectProgress(emptyList(), 0, false).hasActivity)
        assertFalse(projectProgress(listOf(ReadinessQuestionRow("q", null, null, null, false, null, null)), 0, false).hasActivity)
    }

    @Test fun localDayUsesLocalMidnightNotUtcAndHasExclusiveEnd() {
        val day = localStudyDay(Instant.parse("2026-10-04T19:00:00Z"), ZoneId.of("Asia/Kathmandu"))
        assertEquals(Instant.parse("2026-10-04T18:15:00Z"), day.start)
        assertEquals(Instant.parse("2026-10-05T18:15:00Z"), day.end)
        assertEquals(day.end, localStudyDay(day.end, ZoneId.of("Asia/Kathmandu")).start)
    }

    @Test fun daylightSavingDaysAreTwentyThreeAndTwentyFiveHours() {
        val zone = ZoneId.of("America/New_York")
        val spring = localStudyDay(Instant.parse("2026-03-08T16:00:00Z"), zone)
        val autumn = localStudyDay(Instant.parse("2026-11-01T16:00:00Z"), zone)
        assertEquals(23L, Duration.between(spring.start, spring.end).toHours())
        assertEquals(25L, Duration.between(autumn.start, autumn.end).toHours())
    }

    @Test fun timeZoneChangesRecomputeWindowWithoutChangingStoredTime() {
        val now = Instant.parse("2026-10-04T02:00:00Z")
        val utc = localStudyDay(now, ZoneId.of("UTC"))
        val west = localStudyDay(now, ZoneId.of("America/Los_Angeles"))
        assertNotEquals(utc, west)
        assertTrue(now >= utc.start && now < utc.end)
        assertTrue(now >= west.start && now < west.end)
    }
}
