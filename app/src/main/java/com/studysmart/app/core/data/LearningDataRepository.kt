package com.studysmart.app.core.data

import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.database.LearningDataDao
import kotlinx.coroutines.withContext

fun interface LearningDataRepository {
    suspend fun clearLocalLearningData()
}

class RoomLearningDataRepository(
    private val dao: LearningDataDao,
    private val dispatchers: DispatcherProvider,
) : LearningDataRepository {
    override suspend fun clearLocalLearningData() = withContext(dispatchers.io) {
        // Progress/readiness are derived queries; there is no persisted aggregate cache.
        dao.clearForUser("local-user")
    }
}
