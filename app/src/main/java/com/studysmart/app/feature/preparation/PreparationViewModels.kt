package com.studysmart.app.feature.preparation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationSessionStatus
import com.studysmart.app.core.model.Rating
import com.studysmart.app.feature.readiness.ReadinessEstimate
import com.studysmart.app.feature.readiness.ReadinessRepository
import com.studysmart.app.feature.readiness.ReadinessScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(PreparationEntryUiState())
    val uiState: StateFlow<PreparationEntryUiState> = _uiState.asStateFlow()

    init {
        create()
    }

    fun retry() {
        create()
    }

    private fun create() {
        if (_uiState.value.isCreating || _uiState.value.sessionId != null) return
        viewModelScope.launch(dispatchers.main) {
            _uiState.value = PreparationEntryUiState(isCreating = true)
            runCatching { repository.createSession(request) }
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
    val error: String? = null,
    val showDiscardConfirmation: Boolean = false,
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

    init {
        observe()
    }

    fun retryLoad() {
        _uiState.value = PreparationSessionUiState()
        observe()
    }

    fun showDiscardConfirmation() {
        _uiState.value = _uiState.value.copy(showDiscardConfirmation = true, mutationError = null)
    }

    fun hideDiscardConfirmation() {
        _uiState.value = _uiState.value.copy(showDiscardConfirmation = false)
    }

    fun revealAnswer() {
        val current = _uiState.value.session?.currentItem ?: return
        if (_uiState.value.isSaving || current.isRevealed) return
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
        if (_uiState.value.isSaving || !current.isRevealed || pendingMutation != null) return
        pendingMutation = PendingMutation.Rating(
            questionId = current.questionId,
            expectedPresentationCount = current.presentationCount,
            rating = rating,
            reviewRecordId = idGenerator.nextId("review-record"),
        )
        submitPendingMutation()
    }

    fun retryMutation() {
        if (_uiState.value.isSaving) return
        if (pendingMutation == null) {
            val current = _uiState.value.session?.currentItem ?: return
            if (current.isRevealed) return
            revealAnswer()
        } else {
            submitPendingMutation()
        }
    }

    fun discardSession() {
        if (_uiState.value.isSaving) return
        val session = _uiState.value.session
        viewModelScope.launch(dispatchers.main) {
            _uiState.value = _uiState.value.copy(isSaving = true, mutationError = null)
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
                        mutationError = error.message ?: "Unable to discard this session.",
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
                        isStaleSession = session?.let {
                            it.status != PreparationSessionStatus.ACTIVE || it.currentItem == null
                        } == true,
                        error = null,
                        mutationError = _uiState.value.mutationError,
                    )
                }
        }
    }

    private fun submitPendingMutation() {
        val mutation = pendingMutation ?: return
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
                    _uiState.value = _uiState.value.copy(isSaving = false, mutationError = null)
                    if (result.completed) {
                        _effects.tryEmit(PreparationSessionEffect.Completed(result.sessionId))
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        mutationError = error.message ?: "Unable to save this rating.",
                    )
                }
        }
    }

    private sealed interface PendingMutation {
        data class Rating(
            val questionId: String,
            val expectedPresentationCount: Int,
            val rating: com.studysmart.app.core.model.Rating,
            val reviewRecordId: String,
        ) : PendingMutation
    }
}

data class PreparationSummaryUiState(
    val isLoading: Boolean = true,
    val session: PreparationSessionModel? = null,
    val isMissingContent: Boolean = false,
    val isNotCompleted: Boolean = false,
    val readiness: ReadinessEstimate? = null,
    val isReadinessLoading: Boolean = false,
    val readinessError: String? = null,
    val error: String? = null,
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

    init {
        observe()
    }

    fun retry() {
        readinessJob?.cancel()
        _uiState.value = PreparationSummaryUiState()
        observe()
    }

    fun retryReadiness() {
        observeReadiness(_uiState.value.session)
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(dispatchers.main) {
            repository.observeSession(sessionId)
                .catch { error ->
                    readinessJob?.cancel()
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
                        isReadinessLoading = session != null,
                        readiness = null,
                        readinessError = null,
                        error = null,
                    )
                    observeReadiness(session)
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
