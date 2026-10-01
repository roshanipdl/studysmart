package com.studysmart.app.feature.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.studysmart.app.core.model.PreparationDefaults
import com.studysmart.app.core.ui.StudySmartTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun loadingAndLoadErrorKeepTestDefaultsAvailable() {
        val state = mutableStateOf(SettingsUiState())
        var retries = 0
        composeRule.setContent {
            StudySmartTheme {
                SettingsScreen(state.value, {}, {}, { retries++ }, {}, {})
            }
        }
        composeRule.onNodeWithTag("review_defaults_loading").assertIsDisplayed()
        composeRule.onNodeWithText("Test defaults").performScrollTo().assertIsEnabled()
        composeRule.runOnIdle { state.value = SettingsUiState(isLoading = false, error = "read failed") }
        composeRule.onNodeWithTag("review_defaults_error").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test fun boundsAndSaveStateDisableRelevantControls() {
        val state = mutableStateOf(SettingsUiState(isLoading = false, preparationDefaults = PreparationDefaults(1)))
        composeRule.setContent { StudySmartTheme { SettingsScreen(state.value, {}, {}, {}, {}, {}) } }
        composeRule.onNodeWithTag("review_question_count").assertTextEquals("1")
        composeRule.onNodeWithTag("review_count_decrement").assertIsNotEnabled()
        composeRule.onNodeWithTag("review_count_increment").assertIsEnabled()
        composeRule.runOnIdle { state.value = state.value.copy(preparationDefaults = PreparationDefaults(30)) }
        composeRule.onNodeWithTag("review_count_increment").assertIsNotEnabled()
        composeRule.onNodeWithTag("review_count_decrement").assertIsEnabled()
        composeRule.runOnIdle { state.value = state.value.copy(isSaving = true) }
        composeRule.onNodeWithTag("review_count_decrement").assertIsNotEnabled()
        composeRule.onNodeWithText("Test defaults").performScrollTo().assertIsNotEnabled()
    }

    @Test fun failedSaveShowsRetryAndPreservesConfirmedCount() {
        var retries = 0
        composeRule.setContent {
            StudySmartTheme {
                SettingsScreen(
                    SettingsUiState(isLoading = false, preparationDefaults = PreparationDefaults(10),
                        isSaveError = true, error = "write failed"),
                    {}, {}, { retries++ }, {}, {},
                )
            }
        }
        composeRule.onNodeWithTag("review_question_count").assertTextEquals("10")
        composeRule.onNodeWithTag("review_count_increment").assertIsNotEnabled()
        composeRule.onNodeWithTag("review_count_decrement").assertIsNotEnabled()
        composeRule.onNodeWithText("Retry").performScrollTo().performClick()
        assertEquals(1, retries)
    }
}
