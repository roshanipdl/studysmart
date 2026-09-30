package com.studysmart.app.core.common

import android.os.SystemClock
import java.io.File

/** Elapsed realtime includes device sleep and is not affected by wall-clock changes. */
fun interface MonotonicClock {
    fun elapsedRealtimeMillis(): Long

    /** Stable for one OS boot; null when the platform cannot expose a boot marker. */
    fun bootId(): String? = null
}

object AndroidMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun bootId(): String? = currentBootId

    private val currentBootId: String? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching {
            File(BOOT_ID_PATH).readText().trim().takeIf(String::isNotEmpty)
        }.getOrNull()
    }

    private const val BOOT_ID_PATH = "/proc/sys/kernel/random/boot_id"
}
