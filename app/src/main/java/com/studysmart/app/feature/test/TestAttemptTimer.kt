package com.studysmart.app.feature.test

import com.studysmart.app.core.database.TestAttemptEntity
import java.time.Instant

data class TestTimerReading(
    val isValid: Boolean,
    val isExpired: Boolean,
    val elapsedMillis: Long?,
    val remainingSeconds: Long?,
)

/** Uses elapsed realtime as the active-process authority and UTC deadline as a no-extra-time guard. */
object TestAttemptTimer {
    fun read(
        attempt: TestAttemptEntity,
        now: Instant,
        elapsedRealtimeMillis: Long,
        currentBootId: String? = attempt.startedBootId,
    ): TestTimerReading {
        val startedAt = attempt.startedAt
        val monotonicStart = attempt.startedAtElapsedRealtimeMillis
        val bootChanged = attempt.isTimed &&
            (attempt.startedBootId.isNullOrBlank() || currentBootId.isNullOrBlank() ||
                attempt.startedBootId != currentBootId)
        if (startedAt == null || monotonicStart == null || monotonicStart < 0L ||
            elapsedRealtimeMillis < monotonicStart || bootChanged
        ) {
            return TestTimerReading(
                isValid = false,
                isExpired = attempt.isTimed,
                elapsedMillis = null,
                remainingSeconds = if (attempt.isTimed) 0L else null,
            )
        }
        val monotonicElapsed = elapsedRealtimeMillis - monotonicStart
        val wallElapsed = maxOf(0L, now.toEpochMilli() - startedAt.toEpochMilli())
        val effectiveElapsed = maxOf(monotonicElapsed, wallElapsed)
        if (!attempt.isTimed) {
            if (attempt.timeLimitSeconds != null || attempt.deadlineAt != null) {
                return TestTimerReading(false, false, effectiveElapsed, null)
            }
            return TestTimerReading(true, false, effectiveElapsed, null)
        }

        val limitSeconds = attempt.timeLimitSeconds
        val deadline = attempt.deadlineAt
        if (limitSeconds == null || limitSeconds !in 1L..10_800L || deadline == null) {
            return TestTimerReading(false, true, null, 0L)
        }
        val limitMillis = limitSeconds * MILLIS_PER_SECOND
        val expired = now >= deadline || effectiveElapsed >= limitMillis
        val remainingMillis = if (expired) 0L else limitMillis - effectiveElapsed
        val remainingSeconds = if (remainingMillis == 0L) 0L else {
            (remainingMillis + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND
        }
        return TestTimerReading(true, expired, effectiveElapsed, remainingSeconds)
    }

    private const val MILLIS_PER_SECOND = 1_000L
}
