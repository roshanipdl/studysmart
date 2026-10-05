package com.studysmart.app.app

import com.studysmart.app.core.data.importer.CurriculumImporter
import kotlinx.coroutines.CancellationException

fun interface StartupInitializationCoordinator {
    suspend fun initialize()
}

class DefaultStartupInitializationCoordinator(
    private val importer: CurriculumImporter,
    private val cleanup: StartupCleanupCoordinator,
) : StartupInitializationCoordinator {
    override suspend fun initialize() {
        try {
            importer.importIfNeeded()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw IllegalStateException("Unable to import bundled curriculum. Existing data was not replaced.", error)
        }
        cleanup.cleanup()
    }
}
