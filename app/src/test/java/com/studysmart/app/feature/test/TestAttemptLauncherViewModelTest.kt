package com.studysmart.app.feature.test

import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.model.TestQuestionSource
import com.studysmart.app.testing.TestDispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestAttemptLauncherViewModelTest {
    @Test
    fun retryReusesTheSameCreationRequestAfterFailure() = runTest {
        val repository = FakeTestAttemptRepository(failFirst = true)
        val idGenerator = SequenceIdGenerator()
        val viewModel = TestAttemptLauncherViewModel(
            repository = repository,
            idGenerator = idGenerator,
            dispatchers = TestDispatcherProvider(),
        )

        viewModel.start("chapter-1")
        assertTrue(viewModel.uiState.value.error != null)
        viewModel.retry()

        assertEquals("attempt-1", viewModel.uiState.value.attemptId)
        assertEquals(2, repository.chapterRequests.size)
        assertEquals(
            repository.chapterRequests[0].creationRequestId,
            repository.chapterRequests[1].creationRequestId,
        )
        assertEquals(1, idGenerator.generatedIds.size)
    }

    @Test
    fun noQuestionsStaysInContextAndCanBeDismissed() = runTest {
        val repository = FakeTestAttemptRepository(
            result = TestAttemptCreationResult.NoQuestions(TestQuestionSource.IMPORTANT),
        )
        val viewModel = TestAttemptLauncherViewModel(
            repository = repository,
            idGenerator = FixedIdGenerator(),
            dispatchers = TestDispatcherProvider(),
        )

        viewModel.start("chapter-1")

        assertTrue(viewModel.uiState.value.isNoQuestions)
        assertEquals(TestQuestionSource.IMPORTANT, viewModel.uiState.value.noQuestionsSource)
        viewModel.dismissNoQuestions()
        assertEquals(false, viewModel.uiState.value.isNoQuestions)
    }

    @Test
    fun emptyResultGetsFreshRequestIdForTheNextLaunch() = runTest {
        val repository = FakeTestAttemptRepository(
            result = TestAttemptCreationResult.NoQuestions(TestQuestionSource.IMPORTANT),
            subsequentResult = TestAttemptCreationResult.Created("attempt-2"),
        )
        val viewModel = TestAttemptLauncherViewModel(
            repository = repository,
            idGenerator = SequenceIdGenerator(),
            dispatchers = TestDispatcherProvider(),
        )

        viewModel.start("chapter-1")
        val firstRequestId = repository.chapterRequests.single().creationRequestId
        viewModel.dismissNoQuestions()
        viewModel.start("chapter-1")

        assertEquals("attempt-2", viewModel.uiState.value.attemptId)
        assertEquals(2, repository.chapterRequests.size)
        assertTrue(firstRequestId != repository.chapterRequests[1].creationRequestId)
    }

    @Test
    fun createdAttemptBlocksDuplicateLaunchUntilNavigationIsHandled() = runTest {
        val repository = FakeTestAttemptRepository()
        val viewModel = TestAttemptLauncherViewModel(
            repository = repository,
            idGenerator = SequenceIdGenerator(),
            dispatchers = TestDispatcherProvider(),
        )

        viewModel.start("chapter-1")
        viewModel.start("chapter-1")
        assertEquals(1, repository.chapterRequests.size)

        viewModel.onCreatedNavigationHandled()
        viewModel.start("chapter-1")
        assertEquals(2, repository.chapterRequests.size)
        assertTrue(
            repository.chapterRequests[0].creationRequestId !=
                repository.chapterRequests[1].creationRequestId,
        )
    }
}

private class FixedIdGenerator : IdGenerator {
    override fun nextId(prefix: String): String = "$prefix-fixed"
}

private class SequenceIdGenerator : IdGenerator {
    private var next = 0
    val generatedIds = mutableListOf<String>()

    override fun nextId(prefix: String): String {
        val id = "$prefix-${next++}"
        generatedIds += id
        return id
    }
}

private class FakeTestAttemptRepository(
    private val failFirst: Boolean = false,
    private val result: TestAttemptCreationResult = TestAttemptCreationResult.Created("attempt-1"),
    private val subsequentResult: TestAttemptCreationResult = result,
) : TestAttemptRepository {
    val chapterRequests = mutableListOf<ChapterTestAttemptRequest>()

    override fun observeAttempt(attemptId: String): Flow<TestAttemptModel?> = emptyFlow()

    override suspend fun startAttempt(attemptId: String): TestAttemptStartResult =
        TestAttemptStartResult.AlreadyInProgress

    override suspend fun saveAnswerDraft(
        attemptId: String,
        draft: TestAnswerDraft,
    ): TestAnswerSaveResult = TestAnswerSaveResult.Saved

    override suspend fun submitAttempt(
        attemptId: String,
        action: TestSubmitAction,
        finalDraft: TestAnswerDraft?,
    ): TestSubmissionResult = TestSubmissionResult.Submitted

    override suspend fun createChapterAttempt(request: ChapterTestAttemptRequest): TestAttemptCreationResult {
        chapterRequests += request
        if (failFirst && chapterRequests.size == 1) throw IllegalStateException("temporary failure")
        return if (chapterRequests.size == 1) result else subsequentResult
    }

    override suspend fun createImportantAttempt(
        request: ImportantTestAttemptRequest,
    ): TestAttemptCreationResult = result

    override suspend fun createNewAttemptFromResult(
        request: ResultFollowUpAttemptRequest,
    ): TestAttemptCreationResult = result

    override suspend fun createRetestWeakAttempt(
        request: ResultFollowUpAttemptRequest,
    ): TestAttemptCreationResult = result

    override suspend fun loadResultActionContext(attemptId: String): TestResultActionContext? = null

    override suspend fun loadAttemptOrigin(attemptId: String): TestResultOrigin? = null

    override suspend fun discardActiveAttempt(attemptId: String): Boolean = true
}
