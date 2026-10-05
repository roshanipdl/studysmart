package com.studysmart.app.feature.important

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.studysmart.app.core.ui.BookmarkControl
import com.studysmart.app.core.ui.BookmarkStatus
import com.studysmart.app.core.ui.BookmarkUiState
import com.studysmart.app.core.ui.StudySmartTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Pure Compose state tests: no persisted preferences or database are opened. */
class BookmarkControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savedIconHasCheckedStateAndAccessibleAction() {
        var taps = 0
        compose.setContent { StudySmartTheme {
            BookmarkControl(BookmarkUiState(isLoading = false, savedIds = setOf("question")), "question",
                { taps++ }, Modifier.testTag("bookmark"), iconOnly = true)
        } }
        compose.onNodeWithTag("bookmark").assertIsOn().assertIsEnabled().performClick()
        assertEquals(1, taps)
    }

    @Test fun saveInFlightDisablesControlsAndShowsSaving() {
        val state = BookmarkUiState(isLoading = false, isSaving = true)
        compose.setContent { StudySmartTheme { Column {
            BookmarkControl(state, "question", {}, Modifier.testTag("bookmark"))
            BookmarkStatus(state, {})
        } } }
        compose.onNodeWithTag("bookmark").assertIsNotEnabled()
        compose.onNodeWithTag("bookmark_saving").assertExists()
    }

    @Test fun writeErrorDisablesNewActionsAndOffersRetry() {
        var retries = 0
        val state = BookmarkUiState(isLoading = false, hasWriteError = true)
        compose.setContent { StudySmartTheme { Column {
            BookmarkControl(state, "question", {}, Modifier.testTag("bookmark"))
            BookmarkStatus(state, { retries++ })
        } } }
        compose.onNodeWithTag("bookmark").assertIsNotEnabled()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }
}
