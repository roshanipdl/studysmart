package com.studysmart.app.feature.test

import com.studysmart.app.core.common.IdGenerator
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
        val viewModel = TestAttemptLauncherViewModel(
            repository = repository,
            idGenerator = FixedIdGenerator(),
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
    }

    @Test
    fun noQuestionsStaysInContextAndCanBeDismissed() = runTest {
        val repository = FakeTestAttemptRepository(result = TestAttemptCreationResult.NoQuestions)
        val viewModel = TestAttemptLauncherViewModel(
            repository = repository,
            idGenerator = FixedIdGenerator(),
            dispatchers = TestDispatcherProvider(),
        )

        viewModel.start("chapter-1")

        assertTrue(viewModel.uiState.value.isNoQuestions)
        viewModel.dismissNoQuestions()
        assertEquals(false, viewModel.uiState.value.isNoQuestions)
    }
}

private class FixedIdGenerator : IdGenerator {
    override fun nextId(prefix: String): String = "$prefix-fixed"
}

private class FakeTestAttemptRepository(
    private val failFirst: Boolean = false,
    private val result: TestAttemptCreationResult = TestAttemptCreationResult.Created("attempt-1"),
) : TestAttemptRepository {
    val chapterRequests = mutableListOf<ChapterTestAttemptRequest>()

    override fun observeAttempt(attemptId: String): Flow<TestAttemptModel?> = emptyFlow()

    override suspend fun createChapterAttempt(request: ChapterTestAttemptRequest): TestAttemptCreationResult {
        chapterRequests += request
        if (failFirst && chapterRequests.size == 1) throw IllegalStateException("temporary failure")
        return result
    }

    override suspend fun createImportantAttempt(
        request: ImportantTestAttemptRequest,
    ): TestAttemptCreationResult = result

    override suspend fun discardActiveAttempt(attemptId: String): Boolean = true
}
