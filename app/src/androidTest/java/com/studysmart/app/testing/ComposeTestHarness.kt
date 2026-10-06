package com.studysmart.app.testing

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.studysmart.app.app.AppContainer
import com.studysmart.app.app.StudySmartApp
import com.studysmart.app.core.ui.StudySmartTheme
import org.junit.Assert.assertEquals

/** Transformed coordinates, without ancestor clipping used by boundsInRoot. */
fun SemanticsNodeInteraction.unclippedOriginInRoot(): Offset =
    fetchSemanticsNode().layoutInfo.coordinates.localToRoot(Offset.Zero)

fun SemanticsNodeInteraction.unclippedCenterInRoot(): Offset {
    val coordinates = fetchSemanticsNode().layoutInfo.coordinates
    return coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
}

/** For unrotated lower controls, including those outside a fallback scroll viewport. */
fun SemanticsNodeInteraction.unclippedControlBoundsInRoot(): Rect {
    val coordinates = fetchSemanticsNode().layoutInfo.coordinates
    return Rect(coordinates.localToRoot(Offset.Zero),
        Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
}

/** Axis-aligned envelope of transformed corners, including a settled 180-degree card face. */
fun SemanticsNodeInteraction.unclippedTransformedBoundsInRoot(): Rect {
    val coordinates = fetchSemanticsNode().layoutInfo.coordinates
    val width = coordinates.size.width.toFloat()
    val height = coordinates.size.height.toFloat()
    val corners = listOf(Offset.Zero, Offset(width, 0f), Offset(0f, height), Offset(width, height))
        .map(coordinates::localToRoot)
    return Rect(corners.minOf { it.x }, corners.minOf { it.y },
        corners.maxOf { it.x }, corners.maxOf { it.y })
}

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
    useUnmergedTree: Boolean = false,
) {
    await(timeoutMillis) {
        onAllNodesWithTag(tag, useUnmergedTree = useUnmergedTree)
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
