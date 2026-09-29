package com.studysmart.app.testing

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.studysmart.app.app.AppContainer
import com.studysmart.app.app.StudySmartApp
import com.studysmart.app.core.ui.StudySmartTheme
import org.junit.Assert.assertEquals

fun ComposeContentTestRule.setStudySmartContent(
    container: AppContainer? = null,
    startRoute: String = com.studysmart.app.core.navigation.AppRoutes.HOME,
) {
    setContent {
        StudySmartTheme {
            StudySmartApp(startRoute = startRoute, container = container)
        }
    }
}

fun ComposeContentTestRule.awaitNodeWithTag(
    tag: String,
    timeoutMillis: Long = 5_000,
) {
    await(timeoutMillis) {
        onAllNodesWithTag(tag)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
    }
}

fun ComposeContentTestRule.awaitNodeWithText(
    text: String,
    timeoutMillis: Long = 5_000,
) {
    await(timeoutMillis) {
        onAllNodesWithText(text)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
    }
}

fun ComposeContentTestRule.assertNoNodeWithTag(tag: String) {
    waitForIdle()
    assertEquals(
        0,
        onAllNodesWithTag(tag)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .size,
    )
}

private fun ComposeContentTestRule.await(
    timeoutMillis: Long,
    condition: () -> Boolean,
) {
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000
    while (System.nanoTime() < deadline) {
        waitForIdle()
        if (condition()) return
        Thread.sleep(50)
    }
    waitForIdle()
    if (!condition()) {
        throw AssertionError("Timed out waiting for Compose content")
    }
}
