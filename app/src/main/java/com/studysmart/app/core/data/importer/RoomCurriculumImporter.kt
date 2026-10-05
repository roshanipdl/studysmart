package com.studysmart.app.core.data.importer

import androidx.room.withTransaction
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.database.SearchIndexSynchronizer
import java.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomCurriculumImporter(
    private val database: StudySmartDatabase,
    private val source: CurriculumBundleSource,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) : CurriculumImporter {
    private val mutex = Mutex()

    override suspend fun importIfNeeded(): CurriculumImportResult = mutex.withLock {
        withContext(dispatchers.io) {
            val payload = CurriculumJsonParser().read(source)
            database.withTransaction {
                CurriculumImportEngine(database.curriculumImportDao()).apply(payload, clock.instant()).also { result ->
                    if (result == CurriculumImportResult.IMPORTED) {
                        SearchIndexSynchronizer.synchronize(database.openHelper.writableDatabase)
                    }
                }
            }
        }
    }
}
