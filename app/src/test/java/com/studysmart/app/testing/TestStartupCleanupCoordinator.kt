package com.studysmart.app.testing

import com.studysmart.app.app.StartupCleanupCoordinator

class TestStartupCleanupCoordinator(
    private val failure: Throwable? = null,
) : StartupCleanupCoordinator {
    var cleanupStarted: Boolean = false
        private set
    var cleanupCompleted: Boolean = false
        private set

    override suspend fun cleanup() {
        cleanupStarted = true
        failure?.let { throw it }
        cleanupCompleted = true
    }
}
