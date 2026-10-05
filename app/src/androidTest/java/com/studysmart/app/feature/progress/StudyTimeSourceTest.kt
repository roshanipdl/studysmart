package com.studysmart.app.feature.progress

import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.common.DeviceStudyTimeSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Uses an injected clock/zone only; never changes device time or global settings. */
class StudyTimeSourceTest {
    @Test fun receiverRegistrationAndCancellationWorkWithInjectedCurrentZone() = runBlocking {
        var zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-10-04T02:00:00Z")
        val source = DeviceStudyTimeSource(ApplicationProvider.getApplicationContext(), Clock.fixed(now, ZoneId.of("UTC"))) { zone }
        val utc = source.observeTime().first()
        zone = ZoneId.of("America/Los_Angeles")
        source.refresh()
        val west = source.observeTime().first()
        assertEquals(utc.now, west.now)
        assertNotEquals(utc.day, west.day)
    }
}
