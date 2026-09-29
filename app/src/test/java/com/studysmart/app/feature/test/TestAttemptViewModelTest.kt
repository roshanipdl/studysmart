package com.studysmart.app.feature.test

import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.database.TestAttemptEntity
import com.studysmart.app.core.model.TestAttemptOriginType
import com.studysmart.app.core.model.TestAttemptScopeType
import com.studysmart.app.core.model.TestAttemptStatus
import com.studysmart.app.core.model.TestQuestionSource
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class TestAttemptViewModelTest {
    @Test
    fun missingAttemptExposesNonDestructiveRecoveryState() = runTest {
        val viewModel = TestAttemptViewModel(
            attemptId = "missing",
            repository = FakeAttemptRepository(null),
            dispatchers = ImmediateDispatcherProvider,
        )

        assertTrue(viewModel.uiState.value.isMissingContent)
        assertTrue(!viewModel.uiState.value.isLoading)
    }

    @Test
    fun transientAttemptRequiresDiscardConfirmation() = runTest {
        val viewModel = TestAttemptViewModel(
            attemptId = "attempt-1",
            repository = FakeAttemptRepository(sampleAttempt()),
            dispatchers = ImmediateDispatcherProvider,
        )

        viewModel.showDiscardConfirmation()

        assertTrue(viewModel.uiState.value.showDiscardConfirmation)
    }
}

private object ImmediateDispatcherProvider : DispatcherProvider {
    override val io = kotlinx.coroutines.Dispatchers.Unconfined
    override val main = kotlinx.coroutines.Dispatchers.Unconfined
}

private class FakeAttemptRepository(initial: TestAttemptModel?) : TestAttemptRepository {
    private val state = MutableStateFlow(initial)

    override fun observeAttempt(attemptId: String): Flow<TestAttemptModel?> = state

    override suspend fun createChapterAttempt(
        request: ChapterTestAttemptRequest,
    ): TestAttemptCreationResult = TestAttemptCreationResult.NoQuestions

    override suspend fun createImportantAttempt(
        request: ImportantTestAttemptRequest,
    ): TestAttemptCreationResult = TestAttemptCreationResult.NoQuestions

    override suspend fun discardActiveAttempt(attemptId: String): Boolean {
        state.value = null
        return true
    }
}

private fun sampleAttempt(): TestAttemptModel {
    val attempt = TestAttemptEntity(
        id = "attempt-1",
        userId = "local-user",
        courseId = "course-1",
        academicPeriodId = "period-1",
        subjectId = "subject-1",
        chapterId = "chapter-1",
        topicId = null,
        scopeType = TestAttemptScopeType.CHAPTER,
        scopeReferenceId = "chapter-1",
        originType = TestAttemptOriginType.CHAPTER,
        originReferenceId = "chapter-1",
        creationRequestId = "request-1",
        requestedQuestionCount = 1,
        actualQuestionCount = 1,
        questionSource = TestQuestionSource.RANDOM,
        selectionSeed = 1L,
        isTimed = false,
        timeLimitSeconds = null,
        startedAt = null,
        submittedAt = null,
        completedAt = null,
        timeTakenSeconds = null,
        score = null,
        maxScore = null,
        percentage = null,
        status = TestAttemptStatus.CREATED,
        completionReason = null,
        evaluatorVersion = null,
    )
    return TestAttemptModel(attempt, emptyList(), emptyList())
}
