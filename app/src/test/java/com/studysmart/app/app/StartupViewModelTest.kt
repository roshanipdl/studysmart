package com.studysmart.app.app

import com.studysmart.app.testing.TestDispatcherProvider
import com.studysmart.app.testing.TestStartupCleanupCoordinator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupViewModelTest {
    @Test
    fun cleanupCompletesBeforeStartupBecomesReady() = runTest {
        val coordinator = TestStartupCleanupCoordinator()
        val viewModel = StartupViewModel(
            cleanupCoordinator = coordinator,
            dispatcher = TestDispatcherProvider().main,
        )

        assertTrue(coordinator.cleanupStarted)
        assertTrue(coordinator.cleanupCompleted)
        assertTrue(viewModel.uiState.value.isReady)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun cleanupFailureIsRetryableState() = runTest {
        val viewModel = StartupViewModel(
            cleanupCoordinator = TestStartupCleanupCoordinator(IllegalStateException("database")),
            dispatcher = TestDispatcherProvider().main,
        )

        assertFalse(viewModel.uiState.value.isReady)
        assertEquals("database", viewModel.uiState.value.errorMessage)
    }
}
