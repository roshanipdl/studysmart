package com.studysmart.app.feature.test

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.ui.StudySmartTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TestDefaultsScreenStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun saveFailureAndPendingRetryDisableControlsUntilSavedStateArrives() {
        val defaults = TestDefaults(timedMode = true)
        val state = mutableStateOf(TestDefaultsUiState(
            isLoading = false, defaults = defaults,
            error = "Unable to save defaults.", isSaveError = true,
        ))
        var retries = 0
        var increments = 0
        composeRule.setContent {
            StudySmartTheme {
                TestDefaultsScreen(
                    state = state.value, onBack = {},
                    onRetry = {
                        retries++
                        state.value = state.value.copy(error = null, isSaveError = false, isSaving = true)
                    },
                    onIncrementCount = { increments++ }, onDecrementCount = {},
                    onSetTimedMode = {}, onIncrementDuration = {}, onDecrementDuration = {}, onSetSource = {},
                )
            }
        }
        composeRule.onNodeWithTag("test_defaults_save_error").assertIsDisplayed()
        assertControlsDisabled()
        composeRule.onNodeWithText("Retry").performScrollTo().performClick()
        assertEquals(1, retries)
        assertControlsDisabled()
        composeRule.runOnIdle {
            state.value = state.value.copy(isSaving = false, defaults = defaults.copy(questionCount = 11))
        }
        composeRule.onNodeWithContentDescription("Question count: 11").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Increase Question count").assertIsEnabled().performClick()
        assertEquals(1, increments)
    }

    @Test
    fun untimedDefaultsDisableDurationWhileCountAndSourcesRemainAvailable() {
        composeRule.setContent {
            StudySmartTheme {
                TestDefaultsScreen(
                    state = TestDefaultsUiState(isLoading = false, defaults = TestDefaults()),
                    onBack = {}, onRetry = {}, onIncrementCount = {}, onDecrementCount = {},
                    onSetTimedMode = {}, onIncrementDuration = {}, onDecrementDuration = {}, onSetSource = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Increase Question count").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithContentDescription("Increase Default duration in minutes").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Decrease Default duration in minutes").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Random question source").performScrollTo().assertIsEnabled()
    }

    private fun assertControlsDisabled() {
        listOf("Increase Question count", "Decrease Question count", "Timed test",
            "Increase Default duration in minutes", "Decrease Default duration in minutes",
            "All question source", "Random question source", "Weak question source",
            "Unseen question source", "Important question source",
        ).forEach { description ->
            composeRule.onNodeWithContentDescription(description).performScrollTo().assertIsNotEnabled()
        }
    }
}
