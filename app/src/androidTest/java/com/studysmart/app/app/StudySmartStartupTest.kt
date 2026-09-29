package com.studysmart.app.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.ui.StudySmartTheme
import com.studysmart.app.testing.assertNoNodeWithTag
import com.studysmart.app.testing.awaitNodeWithTag
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudySmartStartupTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var database: StudySmartDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun navigationIsHiddenUntilStartupCleanupCompletes() {
        val coordinator = BlockingCleanupCoordinator()
        database = createInMemoryStudySmartDatabase(
            ApplicationProvider.getApplicationContext(),
        )
        val container = AppContainer(
            context = ApplicationProvider.getApplicationContext(),
            database = database!!,
            startupCleanupCoordinator = coordinator,
        )

        composeRule.setContent {
            StudySmartTheme {
                StudySmartApp(container = container)
            }
        }

        composeRule.awaitNodeWithTag("startup_loading")
        composeRule.assertNoNodeWithTag("app_nav_host")
        coordinator.release()
        composeRule.awaitNodeWithTag("app_nav_host")
    }
}

private class BlockingCleanupCoordinator : StartupCleanupCoordinator {
    private val releaseSignal = CompletableDeferred<Unit>()

    override suspend fun cleanup() {
        releaseSignal.await()
    }

    fun release() {
        releaseSignal.complete(Unit)
    }
}
