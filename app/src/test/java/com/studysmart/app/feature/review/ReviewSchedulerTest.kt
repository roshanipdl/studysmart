package com.studysmart.app.feature.review

import com.studysmart.app.core.model.Rating
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSchedulerTest {
    private val reviewedAt = Instant.parse("2026-01-01T12:34:56Z")
    private val scheduler = DeterministicReviewScheduler(
        Clock.fixed(reviewedAt, ZoneOffset.UTC),
    )

    @Test
    fun againSchedulesTenMinutes() {
        assertDecision(Rating.AGAIN, 600L, reviewedAt.plusSeconds(600))
    }

    @Test
    fun hardSchedulesOneDay() {
        assertDecision(Rating.HARD, 86_400L, reviewedAt.plusSeconds(86_400))
    }

    @Test
    fun goodSchedulesThreeDays() {
        assertDecision(Rating.GOOD, 259_200L, reviewedAt.plusSeconds(259_200))
    }

    @Test
    fun easySchedulesSevenDays() {
        assertDecision(Rating.EASY, 604_800L, reviewedAt.plusSeconds(604_800))
    }

    @Test
    fun schedulerUsesUtcInstantAndDoesNotDependOnLocalZone() {
        val decision = scheduler.nextReview(Rating.GOOD)

        assertEquals(reviewedAt, decision.reviewedAt)
        assertTrue(decision.nextReviewAt.isAfter(decision.reviewedAt))
    }

    @Test
    fun schedulerReadsTheClockOncePerDecision() {
        val clock = CountingClock(reviewedAt)

        DeterministicReviewScheduler(clock).nextReview(Rating.AGAIN)

        assertEquals(1, clock.readCount)
    }

    private fun assertDecision(rating: Rating, intervalSeconds: Long, nextReviewAt: Instant) {
        val decision = scheduler.nextReview(rating)

        assertEquals(rating, decision.rating)
        assertEquals(reviewedAt, decision.reviewedAt)
        assertEquals(intervalSeconds, decision.intervalSeconds)
        assertEquals(nextReviewAt, decision.nextReviewAt)
    }

    private class CountingClock(
        private val currentInstant: Instant,
    ) : Clock() {
        var readCount: Int = 0

        override fun instant(): Instant {
            readCount += 1
            return currentInstant
        }

        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this
    }
}
