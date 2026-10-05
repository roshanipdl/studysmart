package com.studysmart.app.app

import com.studysmart.app.core.data.importer.CurriculumImportResult
import com.studysmart.app.core.data.importer.CurriculumImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StartupInitializationCoordinatorTest {
    @Test fun importCompletesBeforeCleanup() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = DefaultStartupInitializationCoordinator(
            CurriculumImporter { events += "import"; CurriculumImportResult.IMPORTED },
            object : StartupCleanupCoordinator { override suspend fun cleanup() { events += "cleanup" } },
        )
        coordinator.initialize()
        assertEquals(listOf("import", "cleanup"), events)
    }

    @Test fun failedImportNeverRunsCleanup() = runBlocking {
        var cleanupRan = false
        val coordinator = DefaultStartupInitializationCoordinator(
            CurriculumImporter { error("broken bundle") },
            object : StartupCleanupCoordinator { override suspend fun cleanup() { cleanupRan = true } },
        )
        try { coordinator.initialize(); fail("Expected failure") } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Unable to import"))
        }
        assertFalse(cleanupRan)
    }

    @Test fun cancellationIsNotWrapped() = runBlocking {
        val cancelled = CancellationException("cancelled")
        val coordinator = DefaultStartupInitializationCoordinator(
            CurriculumImporter { throw cancelled },
            object : StartupCleanupCoordinator { override suspend fun cleanup() { fail("Cleanup must not run") } },
        )
        try { coordinator.initialize(); fail("Expected cancellation") } catch (e: CancellationException) { assertSame(cancelled, e) }
    }
}
