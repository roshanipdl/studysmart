package com.studysmart.app.feature.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class TestResultUiState(
    val isLoading: Boolean = true,
    val report: TestResultReport? = null,
    val isMissingContent: Boolean = false,
    val isNotSubmitted: Boolean = false,
    val isCorruptContent: Boolean = false,
    val error: String? = null,
    val origin: TestResultOrigin? = null,
    val isLoadingOrigin: Boolean = true,
    val canRetryOrigin: Boolean = false,
    val originError: String? = null,
    val actionContext: TestResultActionContext? = null,
    val isLoadingActionContext: Boolean = false,
    val canRetryActionContext: Boolean = false,
    val expandedQuestionId: String? = null,
    val isCreatingFollowUp: Boolean = false,
    val canRetryFollowUp: Boolean = false,
    val mutationError: String? = null,
) {
    val canNavigateBack: Boolean
        get() = !isLoadingOrigin && !canRetryOrigin

    val isRetestWeakEnabled: Boolean
        get() = !isLoadingActionContext && !isCreatingFollowUp && !canRetryFollowUp &&
            actionContext?.activeReviewQuestionCount?.let { it > 0 } == true

    val areFollowUpsEnabled: Boolean
        get() = !isLoadingActionContext && !isCreatingFollowUp && !canRetryFollowUp && actionContext != null &&
            report != null && !isCorruptContent
}

sealed interface TestResultEffect {
    data class OpenAttempt(val attemptId: String) : TestResultEffect
}

class TestResultViewModel(
    private val attemptId: String,
    private val repository: TestAttemptRepository,
    private val idGenerator: IdGenerator,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TestResultUiState())
    val uiState: StateFlow<TestResultUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TestResultEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<TestResultEffect> = _effects.asSharedFlow()

    private var observeJob: Job? = null
    private var originJob: Job? = null
    private var pendingFollowUp: ResultFollowUpAttemptRequest? = null
    private var pendingRetestWeak: Boolean = false

    init {
        resolveOrigin()
        observe()
    }

    fun retry() {
        if (_uiState.value.isCreatingFollowUp) return
        _uiState.value = TestResultUiState()
        resolveOrigin()
        observe()
    }

    fun retryOrigin() {
        if (_uiState.value.isLoadingOrigin || !_uiState.value.canRetryOrigin) return
        resolveOrigin()
    }

    fun toggleQuestion(questionId: String) {
        if (_uiState.value.report?.questions?.none { it.snapshot.id == questionId } != false) return
        _uiState.value = _uiState.value.copy(
            expandedQuestionId = if (_uiState.value.expandedQuestionId == questionId) null else questionId,
        )
    }

    fun startNewTest() = startFollowUp(retestWeak = false)

    fun startRetestWeak() {
        if (!_uiState.value.isRetestWeakEnabled) return
        startFollowUp(retestWeak = true)
    }

    fun retryFollowUp() {
        if (pendingFollowUp == null || _uiState.value.isCreatingFollowUp) return
        createPendingFollowUp()
    }

    fun retryActionContext() {
        if (_uiState.value.isLoadingActionContext || _uiState.value.report == null) return
        viewModelScope.launch(dispatchers.main) { loadActionContext() }
    }

    private fun startFollowUp(retestWeak: Boolean) {
        val state = _uiState.value
        if (!state.areFollowUpsEnabled || (retestWeak && !state.isRetestWeakEnabled)) return
        if (pendingFollowUp != null) return
        pendingRetestWeak = retestWeak
        pendingFollowUp = ResultFollowUpAttemptRequest(
            sourceAttemptId = attemptId,
            creationRequestId = idGenerator.nextId("test-follow-up-request"),
        )
        createPendingFollowUp()
    }

    private fun createPendingFollowUp() {
        val request = pendingFollowUp ?: return
        val retestWeak = pendingRetestWeak
        viewModelScope.launch(dispatchers.main) {
            _uiState.value = _uiState.value.copy(
                isCreatingFollowUp = true,
                canRetryFollowUp = false,
                mutationError = null,
            )
            try {
                val result = if (retestWeak) {
                    repository.createRetestWeakAttempt(request)
                } else {
                    repository.createNewAttemptFromResult(request)
                }
                when (result) {
                    is TestAttemptCreationResult.Created -> {
                        pendingFollowUp = null
                        _uiState.value = _uiState.value.copy(
                            isCreatingFollowUp = false,
                            canRetryFollowUp = false,
                            mutationError = null,
                        )
                        _effects.tryEmit(TestResultEffect.OpenAttempt(result.attemptId))
                    }
                    is TestAttemptCreationResult.NoQuestions -> {
                        pendingFollowUp = null
                        _uiState.value = _uiState.value.copy(
                            isCreatingFollowUp = false,
                            canRetryFollowUp = false,
                            actionContext = if (retestWeak) {
                                _uiState.value.actionContext?.copy(activeReviewQuestionCount = 0)
                            } else {
                                _uiState.value.actionContext
                            },
                            mutationError = if (retestWeak) {
                                "No active questions currently require review, so no retest was created."
                            } else {
                                "No eligible questions are available. No test was created."
                            },
                        )
                    }
                    TestAttemptCreationResult.MissingScope -> failClosed(
                        "The source test scope is no longer available.",
                    )
                    TestAttemptCreationResult.MissingSource -> failClosed(
                        "The source test result is no longer available.",
                    )
                    TestAttemptCreationResult.CorruptSource -> failClosed(
                        "The source test result cannot be used safely.",
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    isCreatingFollowUp = false,
                    canRetryFollowUp = true,
                    mutationError = error.message ?: "Unable to create the follow-up test. Retry.",
                )
            }
        }
    }

    private fun failClosed(message: String) {
        pendingFollowUp = null
        _uiState.value = _uiState.value.copy(
            isCreatingFollowUp = false,
            canRetryFollowUp = false,
            actionContext = null,
            mutationError = message,
        )
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(dispatchers.main) {
            repository.observeAttempt(attemptId)
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        report = null,
                        isMissingContent = false,
                        isNotSubmitted = false,
                        isCorruptContent = false,
                        error = error.message ?: "Unable to load this test result.",
                    )
                }
                .collectLatest { model ->
                    if (model == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            report = null,
                            isMissingContent = true,
                            isNotSubmitted = false,
                            isCorruptContent = false,
                            error = null,
                        )
                        return@collectLatest
                    }
                    when (val projection = TestResultProjector.project(model)) {
                        TestResultProjection.NotSubmitted -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                report = null,
                                isMissingContent = false,
                                isNotSubmitted = true,
                                isCorruptContent = false,
                                error = null,
                            )
                        }
                        is TestResultProjection.Corrupt -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                report = null,
                                isMissingContent = false,
                                isNotSubmitted = false,
                                isCorruptContent = true,
                                error = projection.reason,
                            )
                        }
                        is TestResultProjection.Available -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                report = projection.report,
                                isMissingContent = false,
                                isNotSubmitted = false,
                                isCorruptContent = false,
                                error = null,
                                isLoadingActionContext = true,
                            )
                            loadActionContext()
                        }
                    }
                }
        }
    }

    private fun resolveOrigin() {
        originJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isLoadingOrigin = true,
            canRetryOrigin = false,
            originError = null,
        )
        originJob = viewModelScope.launch(dispatchers.main) {
            try {
                val origin = repository.loadAttemptOrigin(attemptId)
                _uiState.value = _uiState.value.copy(
                    origin = origin,
                    isLoadingOrigin = false,
                    canRetryOrigin = false,
                    originError = null,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    isLoadingOrigin = false,
                    canRetryOrigin = true,
                    originError = error.message ?: "Unable to verify where to return from this result.",
                )
            }
        }
    }

    private suspend fun loadActionContext() {
        _uiState.value = _uiState.value.copy(
            isLoadingActionContext = true,
            canRetryActionContext = false,
            actionContext = null,
            mutationError = null,
        )
        try {
            val context = repository.loadResultActionContext(attemptId)
            _uiState.value = _uiState.value.copy(
                actionContext = context,
                isLoadingActionContext = false,
                canRetryActionContext = false,
                mutationError = if (context == null) {
                    "Follow-up tests are unavailable because the saved source context could not be verified."
                } else {
                    null
                },
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            _uiState.value = _uiState.value.copy(
                isLoadingActionContext = false,
                canRetryActionContext = true,
                mutationError = error.message ?: "Unable to verify follow-up test options.",
            )
        }
    }
}
