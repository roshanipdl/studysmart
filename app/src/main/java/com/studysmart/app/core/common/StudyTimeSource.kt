package com.studysmart.app.core.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

data class StudyTime(val now: Instant, val zone: ZoneId) {
    val day: StudyDay get() = localStudyDay(now, zone)
}

data class StudyDay(val start: Instant, val end: Instant)

fun localStudyDay(now: Instant, zone: ZoneId): StudyDay {
    val date = now.atZone(zone).toLocalDate()
    return StudyDay(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())
}

interface StudyTimeSource {
    fun observeTime(): Flow<StudyTime>
    fun refresh()
}

/** Refreshes without changing device settings; receivers exist only while collected. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceStudyTimeSource(
    context: Context,
    private val clock: Clock,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : StudyTimeSource {
    private val context = context.applicationContext
    private val refreshes = MutableStateFlow(0L)
    override fun refresh() { refreshes.update { it + 1 } }
    override fun observeTime(): Flow<StudyTime> {
        val changes = callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) { trySend(Unit) }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            trySend(Unit)
            awaitClose { context.unregisterReceiver(receiver) }
        }
        return combine(changes, refreshes) { _, _ -> Unit }.flatMapLatest {
            flow {
                while (true) {
                    val time = StudyTime(clock.instant(), zoneProvider())
                    emit(time)
                    delay(Duration.between(time.now, time.day.end).toMillis().coerceIn(1L, 60_000L))
                }
            }
        }
    }
}
