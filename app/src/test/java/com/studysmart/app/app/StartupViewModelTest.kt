package com.studysmart.app.app

import com.studysmart.app.testing.TestDispatcherProvider
import com.studysmart.app.testing.TestStartupCleanupCoordinator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException

class StartupViewModelTest {
    @Test fun retryDoesNotOverlapInitialization() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val viewModel = StartupViewModel(
            initializationCoordinator = StartupInitializationCoordinator { calls++; gate.await() },
            dispatcher = TestDispatcherProvider().main,
        )
        viewModel.retry()
        assertEquals(1, calls)
        gate.complete(Unit)
        assertTrue(viewModel.uiState.value.isReady)
    }

    @Test fun initializationFailureCanBeRetried() = runTest {
        var calls = 0
        val viewModel = StartupViewModel(
            initializationCoordinator = StartupInitializationCoordinator { if (++calls == 1) error("retry me") },
            dispatcher = TestDispatcherProvider().main,
        )
        assertFalse(viewModel.uiState.value.isReady)
        viewModel.retry()
        assertTrue(viewModel.uiState.value.isReady)
        assertEquals(2, calls)
    }

    @Test fun cancellationIsNotDisplayedAsAnError() = runTest {
        val viewModel = StartupViewModel(
            initializationCoordinator = StartupInitializationCoordinator { throw CancellationException("cancelled") },
            dispatcher = TestDispatcherProvider().main,
        )
        assertFalse(viewModel.uiState.value.isReady)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }
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
