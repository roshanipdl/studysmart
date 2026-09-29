package com.studysmart.app.feature.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.model.TestAttemptOriginType
import com.studysmart.app.core.model.TestAttemptStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class TestAttemptUiState(
    val isLoading: Boolean = true,
    val attempt: TestAttemptModel? = null,
    val isMissingContent: Boolean = false,
    val isDiscarding: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    val error: String? = null,
)

sealed interface TestAttemptEffect {
    data class Discarded(
        val originType: TestAttemptOriginType?,
        val originReferenceId: String?,
        val subjectId: String?,
    ) : TestAttemptEffect
}

class TestAttemptViewModel(
    private val attemptId: String,
    private val repository: TestAttemptRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TestAttemptUiState())
    val uiState: StateFlow<TestAttemptUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TestAttemptEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<TestAttemptEffect> = _effects.asSharedFlow()

    private var observeJob: Job? = null

    init {
        observe()
    }

    fun retry() {
        _uiState.value = TestAttemptUiState()
        observe()
    }

    fun showDiscardConfirmation() {
        if (_uiState.value.attempt?.attempt?.status?.isTransient() == true) {
            _uiState.value = _uiState.value.copy(showDiscardConfirmation = true, error = null)
        }
    }

    fun hideDiscardConfirmation() {
        _uiState.value = _uiState.value.copy(showDiscardConfirmation = false)
    }

    fun discard() {
        val current = _uiState.value.attempt?.attempt ?: return
        if (!current.status.isTransient() || _uiState.value.isDiscarding) return
        viewModelScope.launch(dispatchers.main) {
            _uiState.value = _uiState.value.copy(isDiscarding = true, error = null)
            runCatching { repository.discardActiveAttempt(current.id) }
                .onSuccess { discarded ->
                    if (!discarded) {
                        _uiState.value = _uiState.value.copy(
                            isDiscarding = false,
                            showDiscardConfirmation = false,
                            error = "This test attempt is no longer active.",
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isDiscarding = false,
                            showDiscardConfirmation = false,
                        )
                        _effects.tryEmit(
                            TestAttemptEffect.Discarded(
                                originType = current.originType,
                                originReferenceId = current.originReferenceId,
                                subjectId = current.subjectId,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isDiscarding = false,
                        error = error.message ?: "Unable to discard this test attempt.",
                    )
                }
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(dispatchers.main) {
            repository.observeAttempt(attemptId)
                .catch { error ->
                    _uiState.value = TestAttemptUiState(
                        isLoading = false,
                        error = error.message ?: "Unable to load the test attempt.",
                    )
                }
                .collect { attempt ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        attempt = attempt,
                        isMissingContent = attempt == null,
                        error = null,
                    )
                }
        }
    }
}

internal fun TestAttemptStatus.isTransient(): Boolean = when (this) {
    TestAttemptStatus.CREATED,
    TestAttemptStatus.IN_PROGRESS,
    TestAttemptStatus.SUBMITTING,
    -> true
    TestAttemptStatus.SUBMITTED -> false
}
