package com.studysmart.app.feature.test

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.ui.StudySmartTheme
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class FormattedTestResultScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyReferenceAnswerIsFormattedAndMalformedSnapshotShowsError(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = createInMemoryStudySmartDatabase(context)
        try {
            db.openHelper.writableDatabase.execSQL(
                "UPDATE questions SET answer = '<p>Reference <strong>formatted</strong></p>', answerFormat = 'html' WHERE chapterId = 'chapter-bca-s1-dbms-01'")
            val repo = coreAlphaTestContainer(context, db).testAttemptRepository
            val created = repo.createChapterAttempt(ChapterTestAttemptRequest(
                chapterId = "chapter-bca-s1-dbms-01", creationRequestId = "formatted-result")) as TestAttemptCreationResult.Created
            repo.startAttempt(created.attemptId)
            val question = repo.observeAttempt(created.attemptId).first()!!.questions.first()
            repo.submitAttempt(created.attemptId, TestSubmitAction.MANUAL,
                TestAnswerDraft(question.id, "<p>student literal</p>", false, 100L))
            val report = (TestResultProjector.project(repo.observeAttempt(created.attemptId).first()!!) as TestResultProjection.Available).report
            val state = mutableStateOf(TestResultUiState(isLoading = false, report = report,
                isLoadingOrigin = false, expandedQuestionId = question.id))
            compose.setContent {
                StudySmartTheme {
                    TestResultScreen(state.value, onRetry = {}, onRetryOrigin = {}, onRetryFollowUp = {},
                        onRetryActionContext = {}, onToggleQuestion = {}, onRetestWeak = {}, onNewTest = {}, onBack = {})
                }
            }
            compose.onNodeWithText("Reference formatted").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("<p>student literal</p>").performScrollTo().assertIsDisplayed()
            compose.runOnIdle {
                state.value = state.value.copy(report = report.copy(questions = report.questions.map {
                    if (it.snapshot.id == question.id) it.copy(snapshot = it.snapshot.copy(referenceAnswerSnapshot = "<script>bad</script>")) else it
                }))
            }
            compose.onNodeWithText("This answer cannot be displayed because its formatting is invalid.")
                .performScrollTo().assertIsDisplayed()
        } finally { db.close() }
    }
}
