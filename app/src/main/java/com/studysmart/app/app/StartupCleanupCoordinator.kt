package com.studysmart.app.app

import androidx.room.withTransaction
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.database.StudySmartDatabase
import kotlinx.coroutines.withContext

/** Removes transient work before active routes can be exposed to the UI. */
interface StartupCleanupCoordinator {
    suspend fun cleanup()
}

class RoomStartupCleanupCoordinator(
    private val database: StudySmartDatabase,
    private val dispatchers: DispatcherProvider,
) : StartupCleanupCoordinator {
    override suspend fun cleanup() {
        withContext(dispatchers.io) {
            database.withTransaction {
                database.preparationDao().deleteActiveSessions()
                database.testAttemptDao().deleteActiveAttempts()
            }
        }
    }
}
