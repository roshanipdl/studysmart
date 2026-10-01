package com.studysmart.app.feature.preparation

import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.core.model.PreparationSessionStatus
import com.studysmart.app.core.model.QuestionType
import com.studysmart.app.core.model.Rating
import com.studysmart.app.feature.readiness.ReadinessEstimate
import com.studysmart.app.feature.readiness.ReadinessRepository
import com.studysmart.app.feature.readiness.ReadinessScope
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
    @Test fun entryCancellationReleasesAdmissionAndRetryKeepsCapturedCount() = runTest {
        val backing = FakePreparationRepository()
        val preferences = com.studysmart.app.testing.TestPreferencesRepository(
            com.studysmart.app.core.data.StudyPreferences(preparationDefaults = com.studysmart.app.core.model.PreparationDefaults(15)),
        )
        var cancel = true
        val captured = mutableListOf<PreparationSessionRequest>()
        val repository = object : PreparationRepository by backing {
            override suspend fun createSession(request: PreparationSessionRequest): PreparationCreationResult {
                captured += request
                if (cancel) throw kotlinx.coroutines.CancellationException("cancelled")
                return backing.createSession(request)
            }
        }
        val viewModel = PreparationEntryViewModel(request("cancelled-entry"), repository, TestDispatcherProvider(), preferences)
        assertFalse(viewModel.uiState.value.isCreating)
        assertEquals(null, viewModel.uiState.value.error)
        preferences.updatePreparationDefaults(com.studysmart.app.core.model.PreparationDefaults(1))
        cancel = false
        viewModel.retry()
        assertEquals(listOf(15, 15), captured.map { it.questionCount })
        assertEquals("session-1", viewModel.uiState.value.sessionId)
    }

    @Test
    fun entryRetryReusesTheSameCreationRequest() = runTest {
        val repository = FakePreparationRepository(failFirstCreation = true)
        val request = request("request-1")
        val preferences = com.studysmart.app.testing.TestPreferencesRepository(
            com.studysmart.app.core.data.StudyPreferences(preparationDefaults = com.studysmart.app.core.model.PreparationDefaults(15)),
        )
        val viewModel = PreparationEntryViewModel(request, repository, TestDispatcherProvider(),
            preferences)

        assertTrue(viewModel.uiState.value.error != null)

        preferences.updatePreparationDefaults(com.studysmart.app.core.model.PreparationDefaults(1))

        viewModel.retry()

        assertEquals("session-1", viewModel.uiState.value.sessionId)
        assertEquals(listOf(request.copy(questionCount = 15), request.copy(questionCount = 15)), repository.creationRequests)
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
        val failedRequest = repository.ratingRequests.single()
        viewModel.rate(Rating.EASY)
        assertEquals(listOf(failedRequest), repository.ratingRequests)
        viewModel.retryMutation()

        assertTrue(viewModel.uiState.value.mutationError == null)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(2, repository.ratingRequests.size)
        assertEquals(listOf(failedRequest, failedRequest), repository.ratingRequests)
    }

    @Test
    fun failedDiscardAndCancelPreserveThePendingRatingForRetry() = runTest {
        val repository = FakePreparationRepository(failFirstRating = true, failFirstDiscard = true)
        val viewModel = PreparationSessionViewModel("session-1", repository, FixedIdGenerator(), TestDispatcherProvider())
        viewModel.revealAnswer()
        viewModel.rate(Rating.GOOD)
        val request = repository.ratingRequests.single()
        val ratingError = viewModel.uiState.value.mutationError
        viewModel.showDiscardConfirmation()
        viewModel.discardSession()
        assertTrue(viewModel.uiState.value.showDiscardConfirmation)
        assertTrue(viewModel.uiState.value.discardError != null)
        assertEquals(ratingError, viewModel.uiState.value.mutationError)
        viewModel.hideDiscardConfirmation()
        assertEquals(null, viewModel.uiState.value.discardError)
        assertEquals(ratingError, viewModel.uiState.value.mutationError)
        viewModel.retryMutation()
        assertEquals(listOf(request, request), repository.ratingRequests)
    }

    @Test
    fun failedDiscardCanBeRetriedWithoutDeletingTheSessionEarly() = runTest {
        val repository = FakePreparationRepository(failFirstDiscard = true)
        val viewModel = PreparationSessionViewModel("session-1", repository, FixedIdGenerator(), TestDispatcherProvider())
        viewModel.showDiscardConfirmation()
        viewModel.discardSession()
        assertTrue(repository.sessionState.value != null)
        assertTrue(viewModel.uiState.value.discardError != null)
        viewModel.discardSession()
        assertEquals(null, repository.sessionState.value)
        assertFalse(viewModel.uiState.value.showDiscardConfirmation)
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
    fun summaryProjectsCompletedDataAndClearsItWhenTheSessionDisappears() = runTest {
        val completed = sampleSession().copy(
            status = PreparationSessionStatus.COMPLETED,
            reviewCount = 1,
            items = sampleSession().items.map { it.copy(isCompleted = true, lastRating = Rating.GOOD) },
            reviewEvents = listOf(PreparationReviewEventModel("review-1", "question-1", Rating.GOOD, Instant.EPOCH)),
        )
        val repository = FakePreparationRepository(initialSession = completed)
        val viewModel = PreparationSummaryViewModel(
            "session-1", repository, FakeReadinessRepository(), TestDispatcherProvider(),
        )
        assertEquals(projectPreparationSummary(completed), viewModel.uiState.value.summary)
        repository.sessionState.value = null
        assertTrue(viewModel.uiState.value.isMissingContent)
        assertEquals(null, viewModel.uiState.value.summary)
        assertEquals(null, viewModel.uiState.value.readiness)
        assertFalse(viewModel.uiState.value.isReadinessLoading)
    }

    @Test
    fun summaryReportsIncompleteSessionWithoutCreatingReplacementWork() = runTest {
        val repository = FakePreparationRepository()
        val viewModel = PreparationSummaryViewModel(
            sessionId = "session-1",
            repository = repository,
            readinessRepository = FakeReadinessRepository(),
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

private class FakeReadinessRepository : ReadinessRepository {
    override fun observeReadiness(scope: ReadinessScope): Flow<ReadinessEstimate?> =
        kotlinx.coroutines.flow.flowOf(null)
}

private class FixedIdGenerator : IdGenerator {
    private var next = 0

    override fun nextId(prefix: String): String = "$prefix-${next++}"
}

private class FakePreparationRepository(
    initialSession: PreparationSessionModel? = sampleSession(),
    private val failFirstCreation: Boolean = false,
    private val failFirstRating: Boolean = false,
    private val failFirstDiscard: Boolean = false,
) : PreparationRepository {
    val sessionState = MutableStateFlow(initialSession)
    val creationRequests = mutableListOf<PreparationSessionRequest>()
    val ratingRequests = mutableListOf<PreparationRatingRequest>()
    private var discardCalls = 0

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
        discardCalls++
        if (failFirstDiscard && discardCalls == 1) error("temporary discard failure")
        sessionState.value = null
        return true
    }
}

internal fun sampleSession(): PreparationSessionModel = PreparationSessionModel(
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
