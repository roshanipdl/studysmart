package com.studysmart.app.feature.preparation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationSessionStatus
import com.studysmart.app.core.model.Rating
import com.studysmart.app.feature.readiness.ReadinessEstimate
import com.studysmart.app.feature.readiness.ReadinessRepository
import com.studysmart.app.feature.readiness.ReadinessScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PreparationEntryUiState(
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val sessionId: String? = null,
    val isNoQuestions: Boolean = false,
    val isMissingScope: Boolean = false,
    val error: String? = null,
)

class PreparationEntryViewModel(
    private val request: PreparationSessionRequest,
    private val repository: PreparationRepository,
    private val dispatchers: DispatcherProvider,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PreparationEntryUiState())
    val uiState: StateFlow<PreparationEntryUiState> = _uiState.asStateFlow()
    private var pendingRequest: PreparationSessionRequest? = null

    init {
        create()
    }

    fun retry() {
        create()
    }

    private fun create() {
        if (_uiState.value.isCreating || _uiState.value.sessionId != null) return
        _uiState.value = PreparationEntryUiState(isCreating = true)
        viewModelScope.launch(dispatchers.main) {
            runCatching {
                val captured = pendingRequest ?: request.copy(
                    questionCount = preferencesRepository.preferences.first().preparationDefaults.questionCount,
                ).also { pendingRequest = it }
                repository.createSession(captured)
            }
                .onSuccess { result ->
                    _uiState.value = when (result) {
                        is PreparationCreationResult.Created -> PreparationEntryUiState(
                            isLoading = false,
                            sessionId = result.sessionId,
                        )
                        PreparationCreationResult.NoQuestions -> PreparationEntryUiState(
                            isLoading = false,
                            isNoQuestions = true,
                        )
                        PreparationCreationResult.MissingScope -> PreparationEntryUiState(
                            isLoading = false,
                            isMissingScope = true,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        _uiState.value = _uiState.value.copy(isCreating = false, isLoading = false)
                        throw error
                    }
                    _uiState.value = PreparationEntryUiState(
                        isLoading = false,
                        error = error.message ?: "Unable to start preparation.",
                    )
                }
        }
    }
}

data class PreparationSessionUiState(
    val isLoading: Boolean = true,
    val session: PreparationSessionModel? = null,
    val isMissingContent: Boolean = false,
    val isStaleSession: Boolean = false,
    val isSaving: Boolean = false,
    val mutationError: String? = null,
    val discardError: String? = null,
    val error: String? = null,
    val showDiscardConfirmation: Boolean = false,
    val ratingFeedback: PreparationRatingFeedback? = null,
    val completionReady: Boolean = false,
)

data class PreparationRatingFeedback(
    val item: PreparationItemModel,
    val rating: Rating,
    val reviewRecordId: String,
    val committed: Boolean = false,
    val exitFinished: Boolean = false,
    val completed: Boolean = false,
)

sealed interface PreparationSessionEffect {
    data class Completed(val sessionId: String) : PreparationSessionEffect
    data class Discarded(
        val originType: PreparationOriginType?,
        val subjectId: String?,
    ) : PreparationSessionEffect
}

class PreparationSessionViewModel(
    private val sessionId: String,
    private val repository: PreparationRepository,
    private val idGenerator: IdGenerator,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PreparationSessionUiState())
    val uiState: StateFlow<PreparationSessionUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PreparationSessionEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<PreparationSessionEffect> = _effects.asSharedFlow()

    private var observeJob: Job? = null
    private var pendingMutation: PendingMutation? = null
    private var closeAfterFeedback = false

    init {
        observe()
    }

    fun retryLoad() {
        _uiState.value = PreparationSessionUiState()
        observe()
    }

    fun showDiscardConfirmation() {
        if (_uiState.value.ratingFeedback != null) {
            closeAfterFeedback = true
            return
        }
        _uiState.value = _uiState.value.copy(showDiscardConfirmation = true, discardError = null)
    }

    fun hideDiscardConfirmation() {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(showDiscardConfirmation = false, discardError = null)
    }

    fun revealAnswer() {
        val current = _uiState.value.session?.currentItem ?: return
        if (_uiState.value.isSaving || current.isRevealed || _uiState.value.ratingFeedback != null) return
        viewModelScope.launch(dispatchers.main) {
            _uiState.value = _uiState.value.copy(isSaving = true, mutationError = null)
            runCatching { repository.revealCurrent(sessionId, current.questionId) }
                .onSuccess { revealed ->
                    _uiState.value = if (revealed) {
                        _uiState.value.copy(isSaving = false, mutationError = null)
                    } else {
                        _uiState.value.copy(
                            isSaving = false,
                            mutationError = "This preparation card is no longer available.",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        mutationError = error.message ?: "Unable to reveal the answer.",
                    )
                }
        }
    }

    fun rate(rating: Rating) {
        val current = _uiState.value.session?.currentItem ?: return
        if (_uiState.value.isSaving || !current.isRevealed || pendingMutation != null ||
            _uiState.value.ratingFeedback != null || _uiState.value.showDiscardConfirmation) return
        pendingMutation = PendingMutation.Rating(
            questionId = current.questionId,
            expectedPresentationCount = current.presentationCount,
            rating = rating,
            reviewRecordId = idGenerator.nextId("review-record"),
            item = current,
        )
        submitPendingMutation()
    }

    fun retryMutation() {
        if (_uiState.value.isSaving || _uiState.value.ratingFeedback != null) return
        if (pendingMutation == null) {
            val current = _uiState.value.session?.currentItem ?: return
            if (current.isRevealed) return
            revealAnswer()
        } else {
            submitPendingMutation()
        }
    }

    fun discardSession() {
        if (_uiState.value.isSaving || _uiState.value.ratingFeedback != null) return
        val session = _uiState.value.session
        viewModelScope.launch(dispatchers.main) {
            _uiState.value = _uiState.value.copy(isSaving = true, discardError = null)
            runCatching { repository.discardSession(sessionId) }
                .onSuccess { discarded ->
                    if (!discarded) {
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            showDiscardConfirmation = false,
                            mutationError = "This session is no longer active.",
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            showDiscardConfirmation = false,
                        )
                        _effects.tryEmit(
                            PreparationSessionEffect.Discarded(
                                originType = session?.originType,
                                subjectId = session?.context?.subjectId,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        discardError = error.message ?: "Unable to discard this session.",
                    )
                }
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(dispatchers.main) {
            repository.observeSession(sessionId)
                .catch { error ->
                    _uiState.value = PreparationSessionUiState(
                        isLoading = false,
                        error = error.message ?: "Unable to load preparation.",
                    )
                }
                .collect { session ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        session = session,
                        isMissingContent = session == null,
                        isStaleSession = _uiState.value.ratingFeedback == null && session?.let {
                            it.status != PreparationSessionStatus.ACTIVE || it.currentItem == null
                        } == true,
                        error = null,
                        mutationError = _uiState.value.mutationError,
                    )
                    settleRatingFeedback()
                }
        }
    }

    private fun submitPendingMutation() {
        val mutation = pendingMutation ?: return
        if (mutation is PendingMutation.Rating) {
            _uiState.value = _uiState.value.copy(
                ratingFeedback = PreparationRatingFeedback(mutation.item, mutation.rating, mutation.reviewRecordId),
            )
        }
        viewModelScope.launch(dispatchers.main) {
            _uiState.value = _uiState.value.copy(isSaving = true, mutationError = null)
            runCatching {
                when (mutation) {
                    is PendingMutation.Rating -> repository.rateCurrent(
                        PreparationRatingRequest(
                            sessionId = sessionId,
                            questionId = mutation.questionId,
                            expectedPresentationCount = mutation.expectedPresentationCount,
                            rating = mutation.rating,
                            reviewRecordId = mutation.reviewRecordId,
                        ),
                    )
                }
            }
                .onSuccess { result ->
                    pendingMutation = null
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        mutationError = null,
                        isStaleSession = false,
                        ratingFeedback = _uiState.value.ratingFeedback?.copy(committed = true, completed = result.completed),
                    )
                    settleRatingFeedback()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        mutationError = error.message ?: "Unable to save this rating.",
                        ratingFeedback = null,
                    )
                    if (closeAfterFeedback) {
                        closeAfterFeedback = false
                        showDiscardConfirmation()
                    }
                }
        }
    }

    fun finishRatingAnimation(reviewRecordId: String) {
        val feedback = _uiState.value.ratingFeedback ?: return
        if (!feedback.committed || feedback.reviewRecordId != reviewRecordId) return
        _uiState.value = _uiState.value.copy(ratingFeedback = feedback.copy(exitFinished = true))
        settleRatingFeedback()
    }

    private fun settleRatingFeedback() {
        val state = _uiState.value
        val feedback = state.ratingFeedback ?: return
        if (!feedback.committed || !feedback.exitFinished) return
        val session = state.session ?: return
        if (session.reviewEvents.none { it.id == feedback.reviewRecordId }) return
        if (feedback.completed) {
            if (session.isComplete) {
                closeAfterFeedback = false
                _uiState.value = state.copy(completionReady = true, isStaleSession = false)
            }
        } else {
            val next = session.currentItem ?: return
            if (next.questionId == feedback.item.questionId && next.presentationCount == feedback.item.presentationCount) return
            _uiState.value = state.copy(
                ratingFeedback = null,
                showDiscardConfirmation = state.showDiscardConfirmation || closeAfterFeedback,
            )
            closeAfterFeedback = false
        }
    }

    private sealed interface PendingMutation {
        data class Rating(
            val questionId: String,
            val expectedPresentationCount: Int,
            val rating: com.studysmart.app.core.model.Rating,
            val reviewRecordId: String,
            val item: PreparationItemModel,
        ) : PendingMutation
    }
}

data class PreparationSummaryUiState(
    val isLoading: Boolean = true,
    val session: PreparationSessionModel? = null,
    val summary: PreparationSummaryProjection? = null,
    val isMissingContent: Boolean = false,
    val isNotCompleted: Boolean = false,
    val readiness: ReadinessEstimate? = null,
    val isReadinessLoading: Boolean = false,
    val readinessError: String? = null,
    val error: String? = null,
    val chapterCoverage: PreparationChapterCoverage? = null,
    val isCoverageLoading: Boolean = false,
    val coverageError: String? = null,
    val isChapterUnavailable: Boolean = false,
)

class PreparationSummaryViewModel(
    private val sessionId: String,
    private val repository: PreparationRepository,
    private val readinessRepository: ReadinessRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PreparationSummaryUiState())
    val uiState: StateFlow<PreparationSummaryUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var readinessJob: Job? = null
    private var coverageJob: Job? = null

    init {
        observe()
    }

    fun retry() {
        readinessJob?.cancel()
        coverageJob?.cancel()
        _uiState.value = PreparationSummaryUiState()
        observe()
    }

    fun retryReadiness() {
        observeReadiness(_uiState.value.session)
    }

    fun retryCoverage() {
        loadCoverage(_uiState.value.session)
    }

    private fun loadCoverage(session: PreparationSessionModel?) {
        coverageJob?.cancel()
        val chapterId = session?.takeIf { it.isComplete }?.chapterId
        _uiState.value = _uiState.value.copy(
            chapterCoverage = null, coverageError = null, isChapterUnavailable = false,
            isCoverageLoading = chapterId != null,
        )
        if (chapterId == null) return
        coverageJob = viewModelScope.launch(dispatchers.main) {
            try {
                val coverage = repository.getChapterCoverage(chapterId)
                _uiState.value = _uiState.value.copy(
                    chapterCoverage = coverage, isCoverageLoading = false,
                    isChapterUnavailable = coverage == null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCoverageLoading = false, coverageError = error.message ?: "Unable to check chapter coverage.",
                )
            }
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(dispatchers.main) {
            repository.observeSession(sessionId)
                .catch { error ->
                    readinessJob?.cancel()
                    coverageJob?.cancel()
                    _uiState.value = PreparationSummaryUiState(
                        isLoading = false,
                        error = error.message ?: "Unable to load the preparation summary.",
                    )
                }
                .collect { session ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        session = session,
                        isMissingContent = session == null,
                        isNotCompleted = session != null && !session.isComplete,
                        summary = session?.takeIf { it.isComplete }?.let(::projectPreparationSummary),
                        isReadinessLoading = session != null,
                        readiness = null,
                        readinessError = null,
                        error = null,
                    )
                    observeReadiness(session)
                    loadCoverage(session)
                }
        }
    }

    private fun observeReadiness(session: PreparationSessionModel?) {
        readinessJob?.cancel()
        if (session == null) {
            _uiState.value = _uiState.value.copy(
                readiness = null,
                isReadinessLoading = false,
                readinessError = null,
            )
            return
        }
        val scope = ReadinessScope(
            courseId = session.courseId,
            academicPeriodId = session.academicPeriodId,
            subjectId = session.subjectId,
            chapterId = session.chapterId,
        )
        _uiState.value = _uiState.value.copy(
            readiness = null,
            isReadinessLoading = true,
            readinessError = null,
        )
        readinessJob = viewModelScope.launch(dispatchers.main) {
            readinessRepository.observeReadiness(scope)
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isReadinessLoading = false,
                        readinessError = error.message ?: "Unable to load readiness estimate.",
                    )
                }
                .collect { estimate ->
                    _uiState.value = _uiState.value.copy(
                        isReadinessLoading = false,
                        readiness = estimate,
                        readinessError = null,
                    )
                }
        }
    }
}
