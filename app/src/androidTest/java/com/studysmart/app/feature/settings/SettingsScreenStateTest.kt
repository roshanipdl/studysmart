package com.studysmart.app.feature.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.studysmart.app.core.model.PreparationDefaults
import com.studysmart.app.core.ui.StudySmartTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenStateTest {
    @Test fun clearDialogExplainsScopeAndHandlesPendingFailureAndSuccess() {
        val state = mutableStateOf(SettingsUiState(isLoading = false, clearConfirmation = true))
        var confirmations = 0
        var cancellations = 0
        composeRule.setContent {
            StudySmartTheme {
                SettingsScreen(state.value, {}, {}, {}, {}, {},
                    onConfirmClear = { confirmations++ }, onCancelClear = { cancellations++ })
            }
        }
        composeRule.onNodeWithText("Deletes:", substring = true).assertExists()
        composeRule.onNodeWithText("Keeps:", substring = true).assertExists()
        composeRule.onNodeWithText("Clear", substring = false).performClick()
        assertEquals(1, confirmations)
        composeRule.runOnIdle { state.value = state.value.copy(isSaving = true) }
        composeRule.onNodeWithText("Clear", substring = false).assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel", substring = false).assertIsNotEnabled()
        composeRule.runOnIdle { state.value = state.value.copy(isSaving = false, clearError = "failed") }
        composeRule.onNodeWithText("Retry", substring = false).performClick()
        assertEquals(2, confirmations)
        composeRule.onNodeWithText("Cancel", substring = false).performClick()
        assertEquals(1, cancellations)
        composeRule.runOnIdle { state.value = state.value.copy(clearConfirmation = false, clearError = null, clearSucceeded = true) }
        composeRule.onNodeWithTag("settings_clear_success").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Crash reporting: Off").performScrollTo().assertIsDisplayed()
    }
    @Test fun nameEditorExposesSaveCancelAndBlocksSavingControls() {
        val state = mutableStateOf(SettingsUiState(isLoading = false, displayName = "Student", nameDraft = "Ada"))
        var saves = 0
        var cancels = 0
        composeRule.setContent {
            StudySmartTheme {
                SettingsScreen(state.value, {}, {}, {}, {}, {}, onSaveName = { saves++ }, onCancelName = { cancels++ })
            }
        }
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(1, saves)
        assertEquals(1, cancels)
        composeRule.runOnIdle { state.value = state.value.copy(isSaving = true) }
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
        composeRule.onNodeWithTag("settings_name_input").assertIsNotEnabled()
    }
    @Test fun contextThemeNameAndSummaryUseConfirmedState() {
        var courseClicks = 0
        var selected: com.studysmart.app.core.model.ThemePreference? = null
        composeRule.setContent {
            StudySmartTheme {
                SettingsScreen(SettingsUiState(isLoading = false, courseId = "course", courseName = "Yearly programme",
                    periodName = "Year 2", displayName = "Ada", testDefaults = com.studysmart.app.core.model.TestDefaults()),
                    {}, {}, {}, {}, {}, onChooseCourse = { courseClicks++ }, onTheme = { selected = it })
            }
        }
        composeRule.onNodeWithTag("settings_course").performScrollTo().performClick()
        assertEquals(1, courseClicks)
        composeRule.onNodeWithText("Academic period: Year 2").assertIsDisplayed()
        composeRule.onNodeWithText("Ada").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_theme_DARK").performScrollTo().performClick()
        assertEquals(com.studysmart.app.core.model.ThemePreference.DARK, selected)
        composeRule.onNodeWithTag("settings_test_summary").performScrollTo().assertTextEquals("10 questions · Random · Untimed")
    }
    @get:Rule val composeRule = createComposeRule()

    @Test fun loadingAndLoadErrorKeepTestDefaultsAvailable() {
        val state = mutableStateOf(SettingsUiState())
        var retries = 0
        composeRule.setContent {
            StudySmartTheme {
                SettingsScreen(state.value, {}, {}, { retries++ }, {}, {})
            }
        }
        composeRule.onNodeWithTag("review_defaults_loading").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Test defaults").performScrollTo().assertIsEnabled()
        composeRule.runOnIdle { state.value = SettingsUiState(isLoading = false, error = "read failed") }
        composeRule.onNodeWithTag("review_defaults_error").performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText("Retry") and hasAnyAncestor(hasTestTag("review_defaults_error")))
            .performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test fun boundsAndSaveStateDisableRelevantControls() {
        val state = mutableStateOf(SettingsUiState(isLoading = false, preparationDefaults = PreparationDefaults(1)))
        composeRule.setContent { StudySmartTheme { SettingsScreen(state.value, {}, {}, {}, {}, {}) } }
        composeRule.onNodeWithTag("review_question_count").performScrollTo().assertTextEquals("1")
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
                    SettingsUiState(isLoading = false, preparationDefaults = PreparationDefaults(10), dailyCardGoal = 10,
                        isSaveError = true, error = "write failed"),
                    {}, {}, { retries++ }, {}, {},
                )
            }
        }
        composeRule.onNodeWithTag("review_question_count").performScrollTo().assertTextEquals("10")
        composeRule.onNodeWithTag("review_count_increment").assertIsNotEnabled()
        composeRule.onNodeWithTag("review_count_decrement").assertIsNotEnabled()
        composeRule.onNode(hasText("Retry") and hasAnyAncestor(hasTestTag("review_defaults_error")))
            .performScrollTo().performClick()
        assertEquals(1, retries)
    }
}
