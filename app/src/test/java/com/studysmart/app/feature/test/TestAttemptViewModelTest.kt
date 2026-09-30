package com.studysmart.app.feature.test

import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.MonotonicClock
import com.studysmart.app.core.database.TestAnswerEntity
import com.studysmart.app.core.database.TestAttemptEntity
import com.studysmart.app.core.database.TestAttemptQuestionEntity
import com.studysmart.app.core.model.KeywordSnapshotCodec
import com.studysmart.app.core.model.KeywordSnapshotEntry
import com.studysmart.app.core.model.QuestionType
import com.studysmart.app.core.model.TestAttemptOriginType
import com.studysmart.app.core.model.TestAttemptScopeType
import com.studysmart.app.core.model.TestAttemptStatus
import com.studysmart.app.core.model.TestQuestionSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestAttemptViewModelTest {
    @Test
    fun originLookupFailureBlocksBackUntilAConfirmedLookupCanBeRetried() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt()).apply { originFailuresRemaining = 1 }
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler))
        runCurrent()

        assertTrue(viewModel.uiState.value.canRetryOrigin)
        assertFalse(viewModel.uiState.value.canNavigateBack)

        viewModel.retryOrigin()
        runCurrent()

        assertFalse(viewModel.uiState.value.canRetryOrigin)
        assertTrue(viewModel.uiState.value.canNavigateBack)
        assertNull(viewModel.uiState.value.resolvedOrigin)
    }

    @Test
    fun createdAttemptStartsWhileOriginLookupIsSuspended() = runTest {
        val pendingOrigin = CompletableDeferred<TestResultOrigin?>()
        val repository = FakeAttemptRepository(createdAttempt(), pendingOrigin)
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler))
        runCurrent()

        assertEquals(1, repository.startAttemptCalls)
        assertEquals(TestAttemptStatus.IN_PROGRESS, viewModel.uiState.value.attempt?.attempt?.status)
        assertTrue(viewModel.uiState.value.isLoadingOrigin)
        assertFalse(viewModel.uiState.value.canNavigateBack)

        pendingOrigin.complete(TestResultOrigin(TestAttemptOriginType.CHAPTER, "subject-1"))
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoadingOrigin)
        assertTrue(viewModel.uiState.value.canNavigateBack)
    }

    @Test
    fun displaysDraftCountsAndPersistsDebouncedAnswer() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt())
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler))
        runCurrent()

        assertEquals(1, viewModel.uiState.value.answeredCount)
        assertEquals(1, viewModel.uiState.value.unansweredCount)
        assertEquals(1, viewModel.uiState.value.flaggedCount)
        assertEquals("Existing response", viewModel.uiState.value.draftAnswer)

        viewModel.onAnswerChanged("A local draft")
        assertEquals("A local draft", viewModel.uiState.value.draftAnswer)
        assertTrue(repository.savedDrafts.isEmpty())
        advanceTimeBy(350)
        runCurrent()

        assertEquals("A local draft", repository.savedDrafts.single().answerText)
        assertEquals(1, viewModel.uiState.value.answeredCount)
    }

    @Test
    fun navigationFlushesCurrentDraftAndPersistsFlagBeforeChangingQuestion() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt())
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler))
        runCurrent()
        viewModel.toggleFlag()
        viewModel.nextQuestion()
        runCurrent()

        assertEquals(1, viewModel.uiState.value.currentQuestionIndex)
        assertTrue(repository.savedDrafts.single().isFlagged.not())
        assertEquals("Existing response", repository.savedDrafts.single().answerText)
    }

    @Test
    fun failedDiscardAndCancelKeepDraftFailureAvailableForRetry() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt()).apply {
            failSaves = true
            failedDiscardsRemaining = 1
        }
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler))
        runCurrent()
        viewModel.onAnswerChanged("Unsaved response")
        advanceTimeBy(350)
        runCurrent()
        val saveError = viewModel.uiState.value.mutationError
        viewModel.showDiscardConfirmation()
        viewModel.discard()
        runCurrent()
        assertTrue(viewModel.uiState.value.showDiscardConfirmation)
        assertTrue(viewModel.uiState.value.discardError != null)
        assertEquals(saveError, viewModel.uiState.value.mutationError)
        viewModel.hideDiscardConfirmation()
        assertNull(viewModel.uiState.value.discardError)
        assertEquals(saveError, viewModel.uiState.value.mutationError)
        repository.failSaves = false
        viewModel.retryMutation()
        runCurrent()
        assertNull(viewModel.uiState.value.mutationError)
        assertEquals("Unsaved response", repository.savedDrafts.last().answerText)
    }

    @Test
    fun failedDiscardCanBeRetriedWithoutDeletingTheAttemptEarly() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt()).apply { failedDiscardsRemaining = 1 }
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler))
        runCurrent()
        viewModel.showDiscardConfirmation()
        viewModel.discard()
        runCurrent()
        assertTrue(repository.state.value != null)
        assertTrue(viewModel.uiState.value.discardError != null)
        viewModel.discard()
        runCurrent()
        assertNull(repository.state.value)
        assertFalse(viewModel.uiState.value.showDiscardConfirmation)
    }

    @Test
    fun saveFailureBlocksNavigationButConfirmedDiscardStillWorks() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt()).apply { failSaves = true }
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler))
        runCurrent()
        viewModel.onAnswerChanged("Unsaved response")
        advanceTimeBy(350)
        runCurrent()

        assertTrue(viewModel.uiState.value.mutationError != null)
        viewModel.nextQuestion()
        runCurrent()
        assertEquals(0, viewModel.uiState.value.currentQuestionIndex)

        viewModel.showDiscardConfirmation()
        assertTrue(viewModel.uiState.value.showDiscardConfirmation)
        viewModel.discard()
        runCurrent()

        assertEquals(1, repository.discardCount)
        assertNull(repository.state.value)
    }

    @Test
    fun questionTimeContinuesAcrossNavigationSaveFailureAndRetry() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt()).apply { failSaves = true }
        val monotonic = MutableMonotonicClock(10_000L)
        val viewModel = viewModel(
            repository,
            StandardTestDispatcher(testScheduler),
            monotonicClock = monotonic,
        )
        runCurrent()
        viewModel.onAnswerChanged("Answer with delayed save")
        monotonic.now = 12_000L
        viewModel.nextQuestion()
        runCurrent()
        assertEquals(0, viewModel.uiState.value.currentQuestionIndex)
        assertTrue(viewModel.uiState.value.isDraftSaveFailed)

        repository.failSaves = false
        monotonic.now = 15_000L
        viewModel.retryMutation()
        runCurrent()
        monotonic.now = 20_000L
        viewModel.requestSubmit()
        runCurrent()

        assertEquals(10_000L, repository.savedDrafts.last().accumulatedTimeMillis)
        assertTrue(viewModel.uiState.value.showSubmitConfirmation)
    }

    @Test
    fun manualSubmitConfirmationShowsCountsAndSubmitsExactlyOnce() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt())
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler))
        runCurrent()

        viewModel.requestSubmit()
        runCurrent()
        assertTrue(viewModel.uiState.value.showSubmitConfirmation)
        assertEquals(1, viewModel.uiState.value.answeredCount)
        assertEquals(1, viewModel.uiState.value.unansweredCount)
        assertEquals(1, viewModel.uiState.value.flaggedCount)

        viewModel.confirmSubmit()
        runCurrent()
        assertEquals(1, repository.submissions.size)
        assertEquals(TestSubmitAction.MANUAL, repository.submissions.single().action)
        assertFalse(viewModel.uiState.value.showSubmitConfirmation)
    }

    @Test
    fun failedSubmissionKeepsConfirmationAvailableForRetry() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt()).apply { failedSubmissionsRemaining = 1 }
        val monotonic = MutableMonotonicClock(10_000L)
        val viewModel = viewModel(
            repository,
            StandardTestDispatcher(testScheduler),
            monotonicClock = monotonic,
        )
        runCurrent()
        monotonic.now = 12_000L
        viewModel.requestSubmit()
        runCurrent()

        monotonic.now = 15_000L
        viewModel.confirmSubmit()
        runCurrent()
        assertTrue(viewModel.uiState.value.showSubmitConfirmation)
        assertTrue(viewModel.uiState.value.mutationError != null)

        monotonic.now = 20_000L
        viewModel.confirmSubmit()
        runCurrent()
        assertEquals(1, repository.submissions.size)
        assertEquals(TestSubmitAction.MANUAL, repository.submissions.single().action)
        assertEquals(10_000L, repository.savedDrafts.last().accumulatedTimeMillis)
    }

    @Test
    fun expiryFreezesDraftOffersChoiceAndDoesNotAutoSubmit() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt(timed = true))
        val monotonic = MutableMonotonicClock(10_000L)
        val viewModel = viewModel(
            repository,
            StandardTestDispatcher(testScheduler),
            monotonicClock = monotonic,
        )
        runCurrent()
        viewModel.onAnswerChanged("Last pre-expiry draft")
        advanceTimeBy(350)
        runCurrent()
        monotonic.now = 70_000L

        viewModel.onAppForegrounded()

        assertTrue(viewModel.uiState.value.isExpired)
        assertTrue(viewModel.uiState.value.showExpiredConfirmation)
        assertEquals(0L, viewModel.uiState.value.timeRemainingSeconds)
        assertTrue(repository.submissions.isEmpty())
        viewModel.onAnswerChanged("Must be ignored")
        assertEquals("Last pre-expiry draft", viewModel.uiState.value.draftAnswer)
        viewModel.discardAfterExpiry()
        assertTrue(viewModel.uiState.value.showDiscardConfirmation)
        viewModel.hideDiscardConfirmation()
        assertTrue(viewModel.uiState.value.showExpiredConfirmation)

        viewModel.confirmExpiredSubmit()
        runCurrent()
        val submission = repository.submissions.single()
        assertEquals(TestSubmitAction.EXPIRED_CONFIRMATION, submission.action)
        assertEquals("Last pre-expiry draft", submission.finalDraft?.answerText)
        assertTrue(repository.submissions.size == 1)
    }

    @Test
    fun openingAlreadyExpiredAttemptShowsExplicitRecoveryChoice() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt(timed = true))
        val viewModel = viewModel(
            repository,
            StandardTestDispatcher(testScheduler),
            monotonicClock = MutableMonotonicClock(70_000L),
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.isExpired)
        assertTrue(viewModel.uiState.value.showExpiredConfirmation)
        assertTrue(repository.submissions.isEmpty())
    }

    @Test
    fun submitFlushAfterDeadlineFreezesQuestionTimeAtTheDeadline() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt(timed = true))
        val monotonic = MutableMonotonicClock(10_000L)
        val viewModel = viewModel(
            repository,
            StandardTestDispatcher(testScheduler),
            monotonicClock = monotonic,
        )
        runCurrent()
        monotonic.now = 90_000L

        viewModel.requestSubmit()
        runCurrent()

        assertTrue(viewModel.uiState.value.isExpired)
        assertTrue(viewModel.uiState.value.showExpiredConfirmation)
        assertTrue(repository.savedDrafts.isEmpty())
        viewModel.confirmExpiredSubmit()
        runCurrent()
        assertEquals(60_000L, repository.submissions.single().finalDraft?.accumulatedTimeMillis)
    }

    @Test
    fun cancellingExecutingDebouncedSaveOnFocusLossReleasesLockAndRetriesDraft() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt()).apply {
            pendingSave = CompletableDeferred()
        }
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler))
        runCurrent()
        viewModel.onAnswerChanged("Pending response")
        advanceTimeBy(350)
        runCurrent()
        assertEquals(1, repository.saveCalls)
        assertTrue(viewModel.uiState.value.isSaving)
        repository.pendingSave = null
        viewModel.flushDraft()
        runCurrent()
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isDraftSaveFailed)
        assertNull(viewModel.uiState.value.mutationError)
        assertEquals(2, repository.saveCalls)
        assertEquals("Pending response", repository.savedDrafts.single().answerText)
    }

    @Test
    fun cancellingExecutingSaveAcrossExpiryReleasesLockAndPreservesFrozenDraft() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt(timed = true)).apply {
            pendingSave = CompletableDeferred()
        }
        val monotonic = MutableMonotonicClock(10_000L)
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler), monotonicClock = monotonic)
        runCurrent()
        viewModel.onAnswerChanged("Frozen pending response")
        advanceTimeBy(350)
        runCurrent()
        assertTrue(viewModel.uiState.value.isSaving)
        monotonic.now = 70_000L
        viewModel.onAppBackgrounded()
        runCurrent()
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isDraftSaveFailed)
        assertTrue(viewModel.uiState.value.isExpired)
        viewModel.onAppForegrounded()
        viewModel.confirmExpiredSubmit()
        runCurrent()
        assertEquals(TestSubmitAction.EXPIRED_CONFIRMATION, repository.submissions.single().action)
        assertEquals("Frozen pending response", repository.submissions.single().finalDraft?.answerText)
    }

    @Test
    fun expiredAttemptSubmitsFrozenDraftDespiteEarlierDraftSaveFailure() = runTest {
        val repository = FakeAttemptRepository(inProgressAttempt(timed = true)).apply { failSaves = true }
        val monotonic = MutableMonotonicClock(10_000L)
        val viewModel = viewModel(
            repository,
            StandardTestDispatcher(testScheduler),
            monotonicClock = monotonic,
        )
        runCurrent()
        viewModel.onAnswerChanged("Not persisted")
        advanceTimeBy(350)
        runCurrent()
        assertTrue(viewModel.uiState.value.mutationError != null)

        monotonic.now = 70_000L
        viewModel.onAppForegrounded()
        assertTrue(viewModel.uiState.value.showExpiredConfirmation)
        viewModel.confirmExpiredSubmit()
        runCurrent()

        assertEquals(1, repository.submissions.size)
        assertEquals(TestSubmitAction.EXPIRED_CONFIRMATION, repository.submissions.single().action)
        assertEquals("Not persisted", repository.submissions.single().finalDraft?.answerText)
        assertNull(viewModel.uiState.value.mutationError)
    }

    @Test
    fun missingAttemptExposesNonDestructiveRecoveryState() = runTest {
        val viewModel = viewModel(
            FakeAttemptRepository(null),
            StandardTestDispatcher(testScheduler),
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.isMissingContent)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}

private class TestDispatcherProvider(
    override val main: CoroutineDispatcher,
) : DispatcherProvider {
    override val io: CoroutineDispatcher = main
}

private class FakeAttemptRepository(
    initial: TestAttemptModel?,
    private val pendingOrigin: CompletableDeferred<TestResultOrigin?>? = null,
) : TestAttemptRepository {
    val state = MutableStateFlow(initial)
    val savedDrafts = mutableListOf<TestAnswerDraft>()
    val submissions = mutableListOf<SubmissionCall>()
    var failSaves: Boolean = false
    var pendingSave: CompletableDeferred<Unit>? = null
    var saveCalls: Int = 0
    var failedSubmissionsRemaining: Int = 0
    var discardCount: Int = 0
    var failedDiscardsRemaining: Int = 0
    var originFailuresRemaining: Int = 0
    var startAttemptCalls: Int = 0

    override fun observeAttempt(attemptId: String): Flow<TestAttemptModel?> = state

    override suspend fun startAttempt(attemptId: String): TestAttemptStartResult {
        startAttemptCalls++
        val current = state.value ?: return TestAttemptStartResult.Missing
        if (current.attempt.status != TestAttemptStatus.CREATED) return TestAttemptStartResult.AlreadyInProgress
        state.value = current.copy(
            attempt = current.attempt.copy(
                status = TestAttemptStatus.IN_PROGRESS,
                startedAt = Instant.parse("2026-01-01T00:00:00Z"),
                startedAtElapsedRealtimeMillis = 10_000L,
                startedBootId = "test-boot",
            ),
        )
        return TestAttemptStartResult.Started
    }

    override suspend fun saveAnswerDraft(
        attemptId: String,
        draft: TestAnswerDraft,
    ): TestAnswerSaveResult {
        saveCalls++
        pendingSave?.await()
        if (failSaves) error("simulated save failure")
        savedDrafts += draft
        return TestAnswerSaveResult.Saved
    }

    override suspend fun submitAttempt(
        attemptId: String,
        action: TestSubmitAction,
        finalDraft: TestAnswerDraft?,
    ): TestSubmissionResult {
        if (failedSubmissionsRemaining > 0) {
            failedSubmissionsRemaining--
            error("simulated submission failure")
        }
        submissions += SubmissionCall(action, finalDraft)
        return TestSubmissionResult.Submitted
    }

    override suspend fun createChapterAttempt(
        request: ChapterTestAttemptRequest,
    ): TestAttemptCreationResult = TestAttemptCreationResult.NoQuestions(TestQuestionSource.RANDOM)

    override suspend fun createImportantAttempt(
        request: ImportantTestAttemptRequest,
    ): TestAttemptCreationResult = TestAttemptCreationResult.NoQuestions(TestQuestionSource.RANDOM)

    override suspend fun createNewAttemptFromResult(
        request: ResultFollowUpAttemptRequest,
    ): TestAttemptCreationResult = TestAttemptCreationResult.MissingSource

    override suspend fun createRetestWeakAttempt(
        request: ResultFollowUpAttemptRequest,
    ): TestAttemptCreationResult = TestAttemptCreationResult.MissingSource

    override suspend fun loadResultActionContext(attemptId: String): TestResultActionContext? = null

    override suspend fun loadAttemptOrigin(attemptId: String): TestResultOrigin? {
        if (originFailuresRemaining > 0) {
            originFailuresRemaining--
            error("simulated origin lookup failure")
        }
        return pendingOrigin?.await()
    }

    override suspend fun discardActiveAttempt(attemptId: String): Boolean {
        discardCount++
        if (failedDiscardsRemaining > 0) {
            failedDiscardsRemaining--
            error("temporary discard failure")
        }
        state.value = null
        return true
    }
}

private data class SubmissionCall(
    val action: TestSubmitAction,
    val finalDraft: TestAnswerDraft?,
)

private class MutableMonotonicClock(var now: Long) : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = now
    override fun bootId(): String = "test-boot"
}

private class FixedClock(private val current: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = current
}

private fun viewModel(
    repository: FakeAttemptRepository,
    dispatcher: CoroutineDispatcher,
    monotonicClock: MonotonicClock = MutableMonotonicClock(10_000L),
) = TestAttemptViewModel(
    attemptId = "attempt-1",
    repository = repository,
    dispatchers = TestDispatcherProvider(dispatcher),
    clock = FixedClock(Instant.parse("2026-01-01T00:00:00Z")),
    monotonicClock = monotonicClock,
)

private fun inProgressAttempt(timed: Boolean = false): TestAttemptModel {
    val start = Instant.parse("2026-01-01T00:00:00Z")
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
        requestedQuestionCount = 2,
        actualQuestionCount = 2,
        questionSource = TestQuestionSource.RANDOM,
        selectionSeed = 1L,
        isTimed = timed,
        timeLimitSeconds = if (timed) 60L else null,
        startedAt = start,
        startedAtElapsedRealtimeMillis = 10_000L,
        startedBootId = "test-boot",
        deadlineAt = if (timed) start.plusSeconds(60) else null,
        submittedAt = null,
        completedAt = null,
        timeTakenSeconds = null,
        score = null,
        maxScore = null,
        percentage = null,
        status = TestAttemptStatus.IN_PROGRESS,
        completionReason = null,
        evaluatorVersion = null,
    )
    val keywordSnapshot = KeywordSnapshotCodec.encode(
        listOf(KeywordSnapshotEntry("alpha", 1.0)),
    )
    val questions = (0..1).map { index ->
        TestAttemptQuestionEntity(
            id = "attempt-question-$index",
            testAttemptId = attempt.id,
            questionId = "question-$index",
            displayOrder = index,
            questionTextSnapshot = "Question $index",
            referenceAnswerSnapshot = "Reference $index",
            questionTypeSnapshot = QuestionType.WRITTEN,
            maxScoreSnapshot = 2.0,
            keywordSnapshotJson = keywordSnapshot,
            keywordSnapshotVersion = KeywordSnapshotCodec.CURRENT_VERSION,
        )
    }
    val answers = questions.mapIndexed { index, question ->
        TestAnswerEntity(
            id = "answer-$index",
            testAttemptQuestionId = question.id,
            answerText = if (index == 0) "Existing response" else "",
            selectedOption = null,
            isFlagged = index == 0,
            timeSpentSeconds = 0,
            accumulatedTimeMillis = 0,
            keywordMatchPercent = null,
            normalizedAnswer = null,
            matchedKeywordsJson = null,
            missingKeywordsJson = null,
            score = null,
            maxScore = null,
            requiresReview = null,
            updatedAt = start,
        )
    }
    return TestAttemptModel(attempt, questions, answers)
}

private fun createdAttempt(): TestAttemptModel = inProgressAttempt().let { model ->
    model.copy(
        attempt = model.attempt.copy(
            status = TestAttemptStatus.CREATED,
            startedAt = null,
            startedAtElapsedRealtimeMillis = null,
            startedBootId = null,
            deadlineAt = null,
        ),
    )
}
