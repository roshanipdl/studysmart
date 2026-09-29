package com.studysmart.app.feature.preparation

import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.core.model.PreparationSessionStatus
import com.studysmart.app.core.model.QuestionType
import com.studysmart.app.core.model.Rating
import com.studysmart.app.testing.TestDispatcherProvider
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparationViewModelsTest {
    @Test
    fun entryRetryReusesTheSameCreationRequest() = runTest {
        val repository = FakePreparationRepository(failFirstCreation = true)
        val request = request("request-1")
        val viewModel = PreparationEntryViewModel(request, repository, TestDispatcherProvider())

        assertTrue(viewModel.uiState.value.error != null)

        viewModel.retry()

        assertEquals("session-1", viewModel.uiState.value.sessionId)
        assertEquals(listOf(request, request), repository.creationRequests)
    }

    @Test
    fun ratingIsBlockedUntilRevealAndCompletesAfterRepositoryCommit() = runTest {
        val repository = FakePreparationRepository()
        val viewModel = PreparationSessionViewModel(
            sessionId = "session-1",
            repository = repository,
            idGenerator = FixedIdGenerator(),
            dispatchers = TestDispatcherProvider(),
        )

        viewModel.rate(Rating.GOOD)
        assertTrue(repository.ratingRequests.isEmpty())

        viewModel.revealAnswer()
        viewModel.rate(Rating.GOOD)

        assertEquals(1, repository.ratingRequests.size)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(Rating.GOOD, repository.ratingRequests.single().rating)
    }

    @Test
    fun failedRatingKeepsTheMutationAvailableForRetry() = runTest {
        val repository = FakePreparationRepository(failFirstRating = true)
        val viewModel = PreparationSessionViewModel(
            sessionId = "session-1",
            repository = repository,
            idGenerator = FixedIdGenerator(),
            dispatchers = TestDispatcherProvider(),
        )

        viewModel.revealAnswer()
        viewModel.rate(Rating.GOOD)

        assertTrue(viewModel.uiState.value.mutationError != null)
        viewModel.retryMutation()

        assertTrue(viewModel.uiState.value.mutationError == null)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(2, repository.ratingRequests.size)
    }

    @Test
    fun missingSessionExposesRecoveryState() = runTest {
        val repository = FakePreparationRepository(initialSession = null)
        val viewModel = PreparationSessionViewModel(
            sessionId = "missing",
            repository = repository,
            idGenerator = FixedIdGenerator(),
            dispatchers = TestDispatcherProvider(),
        )

        assertTrue(viewModel.uiState.value.isMissingContent)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun completedSessionExposesStaleRecoveryState() = runTest {
        val completed = sampleSession().copy(
            status = PreparationSessionStatus.COMPLETED,
            completedAt = Instant.EPOCH.plusSeconds(60),
            reviewCount = 1,
            items = sampleSession().items.map { item ->
                item.copy(
                    isRevealed = false,
                    isCompleted = true,
                    lastRating = Rating.GOOD,
                    lastPresentedAt = Instant.EPOCH.plusSeconds(60),
                )
            },
        )
        val viewModel = PreparationSessionViewModel(
            sessionId = "session-1",
            repository = FakePreparationRepository(initialSession = completed),
            idGenerator = FixedIdGenerator(),
            dispatchers = TestDispatcherProvider(),
        )

        assertTrue(viewModel.uiState.value.isStaleSession)
        assertFalse(viewModel.uiState.value.isMissingContent)
    }

    @Test
    fun summaryReportsIncompleteSessionWithoutCreatingReplacementWork() = runTest {
        val repository = FakePreparationRepository()
        val viewModel = PreparationSummaryViewModel(
            sessionId = "session-1",
            repository = repository,
            dispatchers = TestDispatcherProvider(),
        )

        assertTrue(viewModel.uiState.value.isNotCompleted)
        assertEquals(0, repository.creationRequests.size)
    }

    private fun request(id: String): PreparationSessionRequest = PreparationSessionRequest(
        scopeType = PreparationScopeType.CHAPTER,
        scopeId = "chapter-1",
        originType = PreparationOriginType.CHAPTER,
        originReferenceId = "chapter-1",
        creationRequestId = id,
    )
}

private class FixedIdGenerator : IdGenerator {
    private var next = 0

    override fun nextId(prefix: String): String = "$prefix-${next++}"
}

private class FakePreparationRepository(
    initialSession: PreparationSessionModel? = sampleSession(),
    private val failFirstCreation: Boolean = false,
    private val failFirstRating: Boolean = false,
) : PreparationRepository {
    private val sessionState = MutableStateFlow(initialSession)
    val creationRequests = mutableListOf<PreparationSessionRequest>()
    val ratingRequests = mutableListOf<PreparationRatingRequest>()

    override fun observeSession(sessionId: String): Flow<PreparationSessionModel?> = sessionState.asStateFlow()

    override suspend fun createSession(request: PreparationSessionRequest): PreparationCreationResult {
        creationRequests += request
        if (failFirstCreation && creationRequests.size == 1) {
            throw IllegalStateException("temporary failure")
        }
        return PreparationCreationResult.Created("session-1")
    }

    override suspend fun createDueReviewSession(request: DueReviewSessionRequest): PreparationCreationResult =
        PreparationCreationResult.Created("session-1")

    override suspend fun revealCurrent(sessionId: String, questionId: String): Boolean {
        val session = sessionState.value ?: return false
        sessionState.value = session.copy(
            items = session.items.map { item ->
                if (item.questionId == questionId) item.copy(isRevealed = true) else item
            },
        )
        return true
    }

    override suspend fun rateCurrent(request: PreparationRatingRequest): PreparationRatingResult {
        ratingRequests += request
        if (failFirstRating && ratingRequests.size == 1) {
            throw IllegalStateException("temporary rating failure")
        }
        val session = sessionState.value ?: return PreparationRatingResult(request.sessionId, completed = false)
        sessionState.value = session.copy(
            reviewCount = session.reviewCount + 1,
            items = session.items.map { item ->
                if (item.questionId == request.questionId) {
                    item.copy(isCompleted = true, isRevealed = false, lastRating = request.rating)
                } else {
                    item
                }
            },
        )
        return PreparationRatingResult(request.sessionId, completed = false)
    }

    override suspend fun discardSession(sessionId: String): Boolean {
        sessionState.value = null
        return true
    }
}

private fun sampleSession(): PreparationSessionModel = PreparationSessionModel(
    id = "session-1",
    courseId = "course-1",
    academicPeriodId = "period-1",
    subjectId = "subject-1",
    chapterId = "chapter-1",
    topicId = null,
    scopeType = PreparationScopeType.CHAPTER,
    originType = PreparationOriginType.CHAPTER,
    originReferenceId = "chapter-1",
    status = PreparationSessionStatus.ACTIVE,
    startedAt = Instant.EPOCH,
    completedAt = null,
    uniqueQuestionCount = 1,
    reviewCount = 0,
    context = PreparationContextModel(
        courseId = "course-1",
        courseCode = "TEST",
        courseName = "Test Course",
        academicPeriodId = "period-1",
        academicPeriodName = "Semester 1",
        subjectId = "subject-1",
        subjectCode = "SUB",
        subjectName = "Subject",
        chapterId = "chapter-1",
        chapterTitle = "Chapter",
        topicId = null,
        topicTitle = null,
    ),
    items = listOf(
        PreparationItemModel(
            id = "item-1",
            questionId = "question-1",
            questionSnapshot = "Question",
            answerSnapshot = "Answer",
            questionTypeSnapshot = QuestionType.WRITTEN,
            originalOrder = 0,
            queueSequence = 0,
            presentationCount = 0,
            hardRequeueUsed = false,
            isRevealed = false,
            isCompleted = false,
            lastRating = null,
            lastPresentedAt = null,
        ),
    ),
    reviewEvents = emptyList(),
)
