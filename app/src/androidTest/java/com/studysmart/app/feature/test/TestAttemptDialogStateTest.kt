package com.studysmart.app.feature.test

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.studysmart.app.core.ui.StudySmartTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TestAttemptDialogStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun expiredSaveFailureStillOffersSubmissionOfLockedAnswers() {
        var submissions = 0
        show(TestAttemptUiState(isExpired = true, showExpiredConfirmation = true,
            isDraftSaveFailed = true, mutationError = "Unable to save draft."),
            onExpiredSubmit = { submissions++ })
        composeRule.onNodeWithText("Unable to save draft.").assertIsDisplayed()
        composeRule.onNodeWithTag("test_expired_submit").assertIsEnabled().performClick()
        assertEquals(1, submissions)
    }

    @Test
    fun discardErrorIsVisibleWithRetryAndCancelActions() {
        var retries = 0
        var cancels = 0
        show(TestAttemptUiState(showDiscardConfirmation = true, discardError = "Unable to discard attempt."),
            onDiscard = { retries++ }, onCancel = { cancels++ })
        composeRule.onNodeWithTag("test_discard_error").assertIsDisplayed()
        composeRule.onNodeWithText("Retry discard").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("test_cancel_discard").assertIsEnabled().performClick()
        assertEquals(1, retries)
        assertEquals(1, cancels)
    }

    private fun show(state: TestAttemptUiState, onExpiredSubmit: () -> Unit = {},
        onDiscard: () -> Unit = {}, onCancel: () -> Unit = {}) {
        composeRule.setContent {
            StudySmartTheme {
                TestAttemptScreen(
                    state = state, onRetry = {}, onRetryOrigin = {}, onRetryMutation = {},
                    onAnswerChanged = {}, onFlag = {}, onPrevious = {}, onNext = {},
                    onSelectQuestion = {}, onFlushDraft = {}, onRequestSubmit = {},
                    onConfirmSubmit = {}, onCancelSubmit = {}, onConfirmExpiredSubmit = onExpiredSubmit,
                    onDiscardAfterExpiry = {}, onClose = {}, onDiscard = onDiscard,
                    onCancelDiscard = onCancel, onBack = {}, onViewResult = {},
                    onAppBackgrounded = {}, onAppForegrounded = {},
                )
            }
        }
    }
}
