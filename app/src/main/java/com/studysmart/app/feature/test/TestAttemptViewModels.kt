package com.studysmart.app.feature.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.AndroidMonotonicClock
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.MonotonicClock
import com.studysmart.app.core.model.TestAttemptStatus
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TestQuestionNavigationState(
    val attemptQuestionId: String,
    val number: Int,
    val isCurrent: Boolean,
    val isAnswered: Boolean,
    val isFlagged: Boolean,
)

data class TestAttemptUiState(
    val isLoading: Boolean = true,
    val attempt: TestAttemptModel? = null,
    val resolvedOrigin: TestResultOrigin? = null,
    val isLoadingOrigin: Boolean = true,
    val canRetryOrigin: Boolean = false,
    val originError: String? = null,
    val isMissingContent: Boolean = false,
    val isCorruptContent: Boolean = false,
    val currentQuestionIndex: Int = 0,
    val draftAnswer: String = "",
    val isCurrentFlagged: Boolean = false,
    val questionNavigation: List<TestQuestionNavigationState> = emptyList(),
    val answeredCount: Int = 0,
    val unansweredCount: Int = 0,
    val flaggedCount: Int = 0,
    val timeRemainingSeconds: Long? = null,
    val isExpired: Boolean = false,
    val isSaving: Boolean = false,
    val isDraftSaveFailed: Boolean = false,
    val isSubmitting: Boolean = false,
    val isDiscarding: Boolean = false,
    val showSubmitConfirmation: Boolean = false,
    val showExpiredConfirmation: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    val error: String? = null,
    val mutationError: String? = null,
    val discardError: String? = null,
) {
    val canNavigateBack: Boolean
        get() = !isLoadingOrigin && !canRetryOrigin

    val isReadOnly: Boolean
        get() = attempt?.attempt?.status == TestAttemptStatus.SUBMITTED
}

sealed interface TestAttemptEffect {
    data object Discarded : TestAttemptEffect

    data class Submitted(val attemptId: String) : TestAttemptEffect
}

class TestAttemptViewModel(
    private val attemptId: String,
    private val repository: TestAttemptRepository,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val monotonicClock: MonotonicClock = AndroidMonotonicClock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TestAttemptUiState())
    val uiState: StateFlow<TestAttemptUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TestAttemptEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<TestAttemptEffect> = _effects.asSharedFlow()

    private val mutationMutex = Mutex()
    private val draftCache = mutableMapOf<String, DraftValue>()
    private val dirtyQuestionIds = mutableSetOf<String>()
    private var nextDraftRevision = 0L
    private var observeJob: Job? = null
    private var timerJob: Job? = null
    private var draftSaveJob: Job? = null
    private var startRequested = false
    private var screenInForeground = true
    private var activeQuestionSinceElapsed: Long? = null
    private var frozenExpiryDraft: TestAnswerDraft? = null
    private var expiryFrozen = false
    private var submissionEffectEmitted = false
    private var hasResolvedNavigationOrigin = false
    private var originJob: Job? = null

    init {
        observe()
    }

    fun retry() {
        val previous = _uiState.value
        _uiState.value = TestAttemptUiState(
            resolvedOrigin = previous.resolvedOrigin,
            isLoadingOrigin = previous.isLoadingOrigin,
            canRetryOrigin = previous.canRetryOrigin,
            originError = previous.originError,
        )
        startRequested = false
        observe()
    }

    fun retryOrigin() {
        if (_uiState.value.isLoadingOrigin || !_uiState.value.canRetryOrigin) return
        hasResolvedNavigationOrigin = false
        resolveNavigationOrigin()
    }

    fun onAnswerChanged(value: String) {
        if (!canEdit()) return
        if (refreshTimerAndExpireIfNeeded()) return
        val question = currentQuestion() ?: return
        val current = draftFor(question.id)
        draftCache[question.id] = current.copy(answerText = value, revision = newRevision())
        dirtyQuestionIds += question.id
        rebuildState()
        scheduleDraftSave()
    }

    fun toggleFlag() {
        if (!canEdit()) return
        if (refreshTimerAndExpireIfNeeded()) return
        val question = currentQuestion() ?: return
        val current = draftFor(question.id)
        draftCache[question.id] = current.copy(isFlagged = !current.isFlagged, revision = newRevision())
        dirtyQuestionIds += question.id
        rebuildState()
        scheduleDraftSave()
    }

    fun previousQuestion() = selectQuestion(_uiState.value.currentQuestionIndex - 1)

    fun nextQuestion() = selectQuestion(_uiState.value.currentQuestionIndex + 1)

    fun selectQuestion(index: Int) {
        val state = _uiState.value
        val questions = state.attempt?.questions.orEmpty()
        if (state.isExpired || state.isReadOnly || state.isLoading || state.isSubmitting ||
            state.isSaving || index !in questions.indices || index == state.currentQuestionIndex
        ) return
        viewModelScope.launch(dispatchers.main) {
            cancelDebouncedSave()
            if (!flushCurrentDraft(stopTimer = true)) return@launch
            _uiState.value = _uiState.value.copy(currentQuestionIndex = index)
            activeQuestionSinceElapsed = if (screenInForeground) monotonicClock.elapsedRealtimeMillis() else null
            rebuildState()
        }
    }

    fun requestSubmit() {
        val state = _uiState.value
        if (state.isLoading || state.isReadOnly || state.isExpired || state.isSubmitting || state.isSaving) return
        viewModelScope.launch(dispatchers.main) {
            cancelDebouncedSave()
            if (!flushCurrentDraft(stopTimer = true)) return@launch
            if (refreshTimerAndExpireIfNeeded()) return@launch
            _uiState.value = _uiState.value.copy(
                showSubmitConfirmation = true,
                mutationError = null,
            )
            if (screenInForeground) activeQuestionSinceElapsed = monotonicClock.elapsedRealtimeMillis()
        }
    }

    fun cancelSubmitConfirmation() {
        if (!_uiState.value.isSubmitting) {
            val state = _uiState.value
            _uiState.value = state.copy(
                showSubmitConfirmation = false,
                mutationError = if (state.isDraftSaveFailed) state.mutationError else null,
            )
        }
    }

    fun confirmSubmit() {
        if (!_uiState.value.showSubmitConfirmation || _uiState.value.isSubmitting) return
        viewModelScope.launch(dispatchers.main) {
            _uiState.value = _uiState.value.copy(showSubmitConfirmation = false)
            if (!flushCurrentDraft(stopTimer = true)) return@launch
            if (refreshTimerAndExpireIfNeeded()) return@launch
            captureCurrentQuestionTime(stopTimer = true)
            submit(TestSubmitAction.MANUAL, currentDraftForSubmission())
        }
    }

    fun confirmExpiredSubmit() {
        if (!_uiState.value.showExpiredConfirmation || _uiState.value.isSubmitting ||
            _uiState.value.isSaving
        ) return
        viewModelScope.launch(dispatchers.main) {
            val finalDraft = frozenExpiryDraft ?: currentDraftForSubmission()
            _uiState.value = _uiState.value.copy(showExpiredConfirmation = false)
            submit(TestSubmitAction.EXPIRED_CONFIRMATION, finalDraft)
        }
    }

    fun discardAfterExpiry() {
        if (!_uiState.value.isExpired) return
        showDiscardConfirmation()
        _uiState.value = _uiState.value.copy(showExpiredConfirmation = false)
    }

    fun showDiscardConfirmation() {
        if (_uiState.value.attempt?.attempt?.status?.isTransient() == true &&
            !_uiState.value.isDiscarding && !_uiState.value.isSubmitting
        ) {
            _uiState.value = _uiState.value.copy(
                showDiscardConfirmation = true,
                showSubmitConfirmation = false,
                showExpiredConfirmation = false,
                discardError = null,
            )
        }
    }

    fun hideDiscardConfirmation() {
        if (!_uiState.value.isDiscarding) {
            _uiState.value = _uiState.value.copy(
                showDiscardConfirmation = false,
                showExpiredConfirmation = _uiState.value.isExpired,
                discardError = null,
            )
        }
    }

    /** Discard is intentionally allowed even if draft persistence is failing. */
    fun discard() {
        val current = _uiState.value.attempt?.attempt ?: return
        if (!current.status.isTransient() || _uiState.value.isDiscarding) return
        viewModelScope.launch(dispatchers.main) {
            cancelDebouncedSave()
            mutationMutex.withLock {
                _uiState.value = _uiState.value.copy(isDiscarding = true, discardError = null)
                runCatching { repository.discardActiveAttempt(current.id) }
                    .onSuccess { discarded ->
                        if (!discarded) {
                            _uiState.value = _uiState.value.copy(
                                isDiscarding = false,
                                showDiscardConfirmation = false,
                                mutationError = "This test attempt is no longer active.",
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isDiscarding = false,
                                showDiscardConfirmation = false,
                            )
                            _effects.tryEmit(TestAttemptEffect.Discarded)
                        }
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        _uiState.value = _uiState.value.copy(
                            isDiscarding = false,
                            discardError = error.message ?: "Unable to discard this test attempt.",
                        )
                    }
            }
        }
    }

    fun retryMutation() {
        if (_uiState.value.isSaving || _uiState.value.isSubmitting || _uiState.value.isDiscarding) return
        viewModelScope.launch(dispatchers.main) {
            flushCurrentDraft(stopTimer = false)
        }
    }

    fun flushDraft() {
        if (_uiState.value.isReadOnly || _uiState.value.isDiscarding) return
        viewModelScope.launch(dispatchers.main) {
            cancelDebouncedSave()
            flushCurrentDraft(stopTimer = false)
        }
    }

    fun onAppBackgrounded() {
        screenInForeground = false
        timerJob?.cancel()
        viewModelScope.launch(dispatchers.main) {
            cancelDebouncedSave()
            captureCurrentQuestionTime(stopTimer = true)
            flushCurrentDraft(stopTimer = true)
        }
    }

    fun onAppForegrounded() {
        screenInForeground = true
        val expired = refreshTimerAndExpireIfNeeded()
        if (!expired) {
            val attempt = _uiState.value.attempt?.attempt
            if (attempt?.status == TestAttemptStatus.IN_PROGRESS && attempt.isTimed) startTimerLoop()
            if (canEdit()) activeQuestionSinceElapsed = monotonicClock.elapsedRealtimeMillis()
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(dispatchers.main) {
            repository.observeAttempt(attemptId)
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Unable to load the test attempt.",
                    )
                    if (!hasResolvedNavigationOrigin) resolveNavigationOrigin()
                }
                .collect { model ->
                    if (model == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            attempt = null,
                            isMissingContent = true,
                            isLoadingOrigin = false,
                            canRetryOrigin = false,
                            originError = null,
                            error = null,
                        )
                        return@collect
                    }
                    val integrityError = model.integrityIssue()
                    if (integrityError != null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            attempt = model,
                            isCorruptContent = true,
                            isLoadingOrigin = false,
                            canRetryOrigin = false,
                            originError = null,
                            error = integrityError,
                        )
                        return@collect
                    }
                    if (!hasResolvedNavigationOrigin) resolveNavigationOrigin()
                    model.answers.forEach { answer ->
                        if (answer.testAttemptQuestionId !in dirtyQuestionIds) {
                            draftCache[answer.testAttemptQuestionId] = DraftValue(
                                answerText = answer.answerText,
                                isFlagged = answer.isFlagged,
                                accumulatedTimeMillis = answer.accumulatedTimeMillis,
                                revision = draftCache[answer.testAttemptQuestionId]?.revision ?: 0L,
                            )
                        }
                    }
                    val stillStarting = model.attempt.status == TestAttemptStatus.CREATED
                    _uiState.value = _uiState.value.copy(
                        isLoading = stillStarting,
                        attempt = model,
                        isMissingContent = false,
                        isCorruptContent = false,
                        error = null,
                    )
                    rebuildState()
                    when (model.attempt.status) {
                        TestAttemptStatus.CREATED -> if (!startRequested) startCreatedAttempt()
                        TestAttemptStatus.IN_PROGRESS -> {
                            val reading = timerReading(model)
                            if (!reading.isValid) {
                                if (model.attempt.isTimed && reading.isExpired) {
                                    freezeExpiredDraft()
                                } else {
                                    _uiState.value = _uiState.value.copy(
                                        isLoading = false,
                                        isCorruptContent = true,
                                        error = "The test timer state is unavailable. Discard this attempt to continue.",
                                    )
                                }
                            } else {
                                if (reading.isExpired) {
                                    freezeExpiredDraft()
                                } else {
                                    if (activeQuestionSinceElapsed == null && screenInForeground) {
                                        activeQuestionSinceElapsed = monotonicClock.elapsedRealtimeMillis()
                                    }
                                    startTimerLoop()
                                }
                                rebuildState()
                            }
                        }
                        TestAttemptStatus.SUBMITTING -> {
                            _uiState.value = _uiState.value.copy(isLoading = false, isSubmitting = true)
                        }
                        TestAttemptStatus.SUBMITTED -> {
                            timerJob?.cancel()
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isSubmitting = false,
                                isExpired = model.attempt.completionReason == "time_expired",
                                timeRemainingSeconds = 0L.takeIf { model.attempt.isTimed },
                            )
                            rebuildState()
                        }
                    }
                }
        }
    }

    private fun resolveNavigationOrigin() {
        if (hasResolvedNavigationOrigin || originJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(
            isLoadingOrigin = true,
            canRetryOrigin = false,
            originError = null,
        )
        originJob = viewModelScope.launch(dispatchers.main) {
            try {
                val origin = repository.loadAttemptOrigin(attemptId)
                hasResolvedNavigationOrigin = true
                _uiState.value = _uiState.value.copy(
                    resolvedOrigin = origin,
                    isLoadingOrigin = false,
                    canRetryOrigin = false,
                    originError = null,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    isLoadingOrigin = false,
                    canRetryOrigin = true,
                    originError = error.message ?: "Unable to verify where to return from this test.",
                )
            }
        }
    }

    private fun startCreatedAttempt() {
        startRequested = true
        viewModelScope.launch(dispatchers.main) {
            runCatching { repository.startAttempt(attemptId) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Unable to start the test attempt.",
                    )
                }
                .onSuccess { result ->
                    when (result) {
                        TestAttemptStartResult.Started,
                        TestAttemptStartResult.AlreadyInProgress,
                        TestAttemptStartResult.AlreadySubmitted,
                        -> Unit
                        TestAttemptStartResult.Missing -> _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isMissingContent = true,
                        )
                        TestAttemptStartResult.Corrupt -> _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isCorruptContent = true,
                            error = "This test attempt is incomplete or unsupported.",
                        )
                    }
                }
        }
    }

    private fun startTimerLoop() {
        val attempt = _uiState.value.attempt?.attempt ?: return
        if (!attempt.isTimed || timerJob?.isActive == true) return
        timerJob = viewModelScope.launch(dispatchers.main) {
            while (isActive) {
                delay(TIMER_REFRESH_MILLIS)
                if (refreshTimerAndExpireIfNeeded()) break
            }
        }
    }

    private fun refreshTimerAndExpireIfNeeded(): Boolean {
        val model = _uiState.value.attempt ?: return false
        if (model.attempt.status != TestAttemptStatus.IN_PROGRESS || !model.attempt.isTimed) return false
        val reading = timerReading(model)
        if (!reading.isValid) {
            if (model.attempt.isTimed && reading.isExpired) {
                freezeExpiredDraft()
            } else {
                _uiState.value = _uiState.value.copy(
                    isCorruptContent = true,
                    error = "The test timer state is unavailable. Discard this attempt to continue.",
                )
            }
            return true
        }
        if (reading.isExpired) {
            freezeExpiredDraft()
            return true
        }
        _uiState.value = _uiState.value.copy(timeRemainingSeconds = reading.remainingSeconds)
        return false
    }

    private fun freezeExpiredDraft() {
        if (expiryFrozen) return
        expiryFrozen = true
        timerJob?.cancel()
        captureCurrentQuestionTime(stopTimer = true)
        frozenExpiryDraft = currentDraftForSubmission()
        val discardDialogIsOpen = _uiState.value.showDiscardConfirmation
        _uiState.value = _uiState.value.copy(
            isExpired = true,
            timeRemainingSeconds = 0L,
            showExpiredConfirmation = !discardDialogIsOpen,
            showSubmitConfirmation = false,
        )
    }

    private fun timerReading(model: TestAttemptModel): TestTimerReading = TestAttemptTimer.read(
        attempt = model.attempt,
        now = clock.instant(),
        elapsedRealtimeMillis = monotonicClock.elapsedRealtimeMillis(),
        currentBootId = monotonicClock.bootId(),
    )

    private fun scheduleDraftSave() {
        val generation = nextRevisionGeneration()
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch(dispatchers.main) {
            delay(DRAFT_DEBOUNCE_MILLIS)
            if (generation == currentSaveGeneration) flushCurrentDraft(stopTimer = false)
        }
    }

    private suspend fun cancelDebouncedSave() {
        currentSaveGeneration++
        val pending = draftSaveJob
        draftSaveJob = null
        pending?.cancel()
        pending?.join()
    }

    private suspend fun flushCurrentDraft(stopTimer: Boolean): Boolean {
        if (refreshTimerAndExpireIfNeeded()) return false
        if (stopTimer) captureCurrentQuestionTime(stopTimer = true)
        val question = currentQuestion() ?: return true
        if (question.id !in dirtyQuestionIds) {
            if (stopTimer && screenInForeground && !_uiState.value.isExpired) {
                activeQuestionSinceElapsed = monotonicClock.elapsedRealtimeMillis()
            }
            return true
        }
        return mutationMutex.withLock {
            val draft = draftFor(question.id)
            _uiState.value = _uiState.value.copy(isSaving = true, mutationError = null)
            val result = runCatching {
                try {
                    repository.saveAnswerDraft(
                        attemptId,
                        TestAnswerDraft(
                            attemptQuestionId = question.id,
                            answerText = draft.answerText,
                            isFlagged = draft.isFlagged,
                            accumulatedTimeMillis = draft.accumulatedTimeMillis,
                        ),
                    )
                } finally {
                    // Dirty drafts remain available for replacement flush or expired submission.
                    _uiState.value = _uiState.value.copy(isSaving = false)
                }
            }
            result.fold(
                onSuccess = { saved ->
                    when (saved) {
                        TestAnswerSaveResult.Saved -> {
                            if (draftFor(question.id).revision == draft.revision) dirtyQuestionIds -= question.id
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                isDraftSaveFailed = false,
                                mutationError = null,
                            )
                            rebuildState()
                            if (question.id in dirtyQuestionIds) scheduleDraftSave()
                            if (stopTimer && screenInForeground && !_uiState.value.isExpired) {
                                activeQuestionSinceElapsed = monotonicClock.elapsedRealtimeMillis()
                            }
                            true
                        }
                        TestAnswerSaveResult.Expired -> {
                            _uiState.value = _uiState.value.copy(isSaving = false)
                            freezeExpiredDraft()
                            false
                        }
                        TestAnswerSaveResult.AlreadySubmitted -> {
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                mutationError = "This test has already been submitted.",
                            )
                            false
                        }
                        TestAnswerSaveResult.MissingAttempt,
                        TestAnswerSaveResult.MissingQuestion,
                        TestAnswerSaveResult.NotActive,
                        TestAnswerSaveResult.Corrupt,
                        -> {
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                isDraftSaveFailed = true,
                                mutationError = "This test could not save the current answer. Retry or discard the attempt.",
                            )
                            if (stopTimer) resumeQuestionClockAfterMutation()
                            false
                        }
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        isDraftSaveFailed = true,
                        mutationError = error.message ?: "Unable to save the answer. Retry.",
                    )
                    if (stopTimer) resumeQuestionClockAfterMutation()
                    false
                },
            )
        }
    }

    private suspend fun submit(action: TestSubmitAction, finalDraft: TestAnswerDraft?) {
        mutationMutex.withLock {
            _uiState.value = _uiState.value.copy(isSubmitting = true, mutationError = null)
            runCatching { repository.submitAttempt(attemptId, action, finalDraft) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        showSubmitConfirmation = action == TestSubmitAction.MANUAL,
                        showExpiredConfirmation = action == TestSubmitAction.EXPIRED_CONFIRMATION,
                        mutationError = error.message ?: "Unable to submit the test. Retry.",
                    )
                    if (action == TestSubmitAction.MANUAL) resumeQuestionClockAfterMutation()
                }
                .onSuccess { result ->
                    when (result) {
                        TestSubmissionResult.Submitted,
                        TestSubmissionResult.AlreadySubmitted,
                        -> {
                            _uiState.value = _uiState.value.copy(
                                isSubmitting = false,
                                showSubmitConfirmation = false,
                                showExpiredConfirmation = false,
                                showDiscardConfirmation = false,
                                mutationError = null,
                            )
                            if (!submissionEffectEmitted) {
                                submissionEffectEmitted = true
                                _effects.tryEmit(TestAttemptEffect.Submitted(attemptId))
                            }
                        }
                        TestSubmissionResult.Expired -> {
                            _uiState.value = _uiState.value.copy(isSubmitting = false)
                            freezeExpiredDraft()
                        }
                        TestSubmissionResult.NotExpired -> _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            showExpiredConfirmation = action == TestSubmitAction.EXPIRED_CONFIRMATION,
                            mutationError = "The test time has not expired. Submit it normally instead.",
                        )
                        TestSubmissionResult.InProgress -> _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            showSubmitConfirmation = action == TestSubmitAction.MANUAL,
                            showExpiredConfirmation = action == TestSubmitAction.EXPIRED_CONFIRMATION,
                            mutationError = "Submission is already in progress. Retry shortly.",
                        )
                        TestSubmissionResult.NotActive -> _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            showExpiredConfirmation = action == TestSubmitAction.EXPIRED_CONFIRMATION,
                            mutationError = "This test is no longer active.",
                        )
                        TestSubmissionResult.Missing -> _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            isMissingContent = true,
                        )
                        TestSubmissionResult.Corrupt -> _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            isCorruptContent = true,
                            error = "This test attempt is incomplete or unsupported.",
                        )
                    }
                }
        }
    }

    private fun currentDraftForSubmission(): TestAnswerDraft? {
        val question = currentQuestion() ?: return null
        val draft = draftFor(question.id)
        return TestAnswerDraft(
            attemptQuestionId = question.id,
            answerText = draft.answerText,
            isFlagged = draft.isFlagged,
            accumulatedTimeMillis = draft.accumulatedTimeMillis,
        )
    }

    private fun captureCurrentQuestionTime(
        stopTimer: Boolean,
    ) {
        val question = currentQuestion() ?: return
        val started = activeQuestionSinceElapsed ?: return
        val now = monotonicClock.elapsedRealtimeMillis()
        val attempt = _uiState.value.attempt?.attempt ?: return
        val stopAt = if (attempt.isTimed) {
            val start = attempt.startedAtElapsedRealtimeMillis ?: now
            val limitMillis = (attempt.timeLimitSeconds ?: 0L) * MILLIS_PER_SECOND
            minOf(now, start + limitMillis)
        } else {
            now
        }
        val delta = maxOf(0L, stopAt - started)
        if (delta > 0L) {
            val draft = draftFor(question.id)
            val cap = attempt.timeLimitSeconds?.times(MILLIS_PER_SECOND)
            val total = (draft.accumulatedTimeMillis + delta).let { cap?.let { max -> minOf(it, max) } ?: it }
            draftCache[question.id] = draft.copy(accumulatedTimeMillis = total, revision = newRevision())
            dirtyQuestionIds += question.id
        }
        activeQuestionSinceElapsed = if (stopTimer) null else now
        rebuildState()
    }

    private fun currentQuestion() = _uiState.value.attempt?.questions
        ?.getOrNull(_uiState.value.currentQuestionIndex)

    private fun resumeQuestionClockAfterMutation() {
        if (!screenInForeground || _uiState.value.isExpired) return
        if (refreshTimerAndExpireIfNeeded()) return
        if (_uiState.value.attempt?.attempt?.status == TestAttemptStatus.IN_PROGRESS) {
            activeQuestionSinceElapsed = monotonicClock.elapsedRealtimeMillis()
        }
    }

    private fun draftFor(questionId: String): DraftValue = draftCache[questionId] ?: run {
        val answer = _uiState.value.attempt?.answers?.firstOrNull { it.testAttemptQuestionId == questionId }
        DraftValue(
            answerText = answer?.answerText.orEmpty(),
            isFlagged = answer?.isFlagged ?: false,
            accumulatedTimeMillis = answer?.accumulatedTimeMillis ?: 0L,
            revision = 0L,
        ).also { draftCache[questionId] = it }
    }

    private fun rebuildState() {
        val current = _uiState.value
        val model = current.attempt ?: return
        val questions = model.questions.sortedBy { it.displayOrder }
        if (questions.isEmpty()) return
        val index = current.currentQuestionIndex.coerceIn(0, questions.lastIndex)
        val currentQuestion = questions[index]
        val answerStates = questions.map { question ->
            val draft = draftFor(question.id)
            TestQuestionNavigationState(
                attemptQuestionId = question.id,
                number = question.displayOrder + 1,
                isCurrent = question.id == currentQuestion.id,
                isAnswered = draft.answerText.isNotBlank(),
                isFlagged = draft.isFlagged,
            )
        }
        val draft = draftFor(currentQuestion.id)
        val reading = timerReading(model).takeIf { model.attempt.status == TestAttemptStatus.IN_PROGRESS }
        _uiState.value = current.copy(
            isLoading = model.attempt.status == TestAttemptStatus.CREATED,
            currentQuestionIndex = index,
            draftAnswer = draft.answerText,
            isCurrentFlagged = draft.isFlagged,
            questionNavigation = answerStates,
            answeredCount = answerStates.count { it.isAnswered },
            unansweredCount = answerStates.count { !it.isAnswered },
            flaggedCount = answerStates.count { it.isFlagged },
            timeRemainingSeconds = if (model.attempt.isTimed) reading?.remainingSeconds else null,
            isExpired = current.isExpired || reading?.isExpired == true,
        )
    }

    private fun canEdit(): Boolean {
        val state = _uiState.value
        return !state.isLoading && !state.isExpired && !state.isReadOnly && !state.isCorruptContent &&
            !state.isSaving && !state.isSubmitting && !state.isDiscarding &&
            !state.showDiscardConfirmation &&
            state.attempt?.attempt?.status == TestAttemptStatus.IN_PROGRESS
    }

    private fun newRevision(): Long = ++nextDraftRevision

    private fun nextRevisionGeneration(): Long = ++currentSaveGeneration

    private var currentSaveGeneration: Long = 0L

    private data class DraftValue(
        val answerText: String,
        val isFlagged: Boolean,
        val accumulatedTimeMillis: Long,
        val revision: Long,
    )

    private companion object {
        const val DRAFT_DEBOUNCE_MILLIS = 350L
        const val TIMER_REFRESH_MILLIS = 250L
        const val MILLIS_PER_SECOND = 1_000L
    }
}

internal fun TestAttemptStatus.isTransient(): Boolean = when (this) {
    TestAttemptStatus.CREATED,
    TestAttemptStatus.IN_PROGRESS,
    TestAttemptStatus.SUBMITTING,
    -> true
    TestAttemptStatus.SUBMITTED -> false
}
