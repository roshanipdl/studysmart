package com.studysmart.app.feature.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TestAttemptLauncherUiState(
    val isCreating: Boolean = false,
    val attemptId: String? = null,
    val isNoQuestions: Boolean = false,
    val isMissingScope: Boolean = false,
    val error: String? = null,
)

sealed interface TestAttemptLauncherEffect {
    data class Created(val attemptId: String) : TestAttemptLauncherEffect
}

class TestAttemptLauncherViewModel(
    private val repository: TestAttemptRepository,
    private val idGenerator: IdGenerator,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TestAttemptLauncherUiState())
    val uiState: StateFlow<TestAttemptLauncherUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TestAttemptLauncherEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<TestAttemptLauncherEffect> = _effects.asSharedFlow()

    private var request: ChapterTestAttemptRequest? = null

    fun start(chapterId: String) {
        if (_uiState.value.isCreating) return
        if (request?.chapterId != chapterId) {
            request = ChapterTestAttemptRequest(
                chapterId = chapterId,
                creationRequestId = idGenerator.nextId("test-attempt-request"),
            )
        }
        create()
    }

    fun retry() {
        if (_uiState.value.isCreating || request == null) return
        create()
    }

    fun dismissNoQuestions() {
        _uiState.value = _uiState.value.copy(isNoQuestions = false, error = null)
    }

    private fun create() {
        val currentRequest = request ?: return
        viewModelScope.launch(dispatchers.main) {
            _uiState.value = TestAttemptLauncherUiState(isCreating = true)
            runCatching { repository.createChapterAttempt(currentRequest) }
                .onSuccess { result ->
                    _uiState.value = when (result) {
                        is TestAttemptCreationResult.Created -> TestAttemptLauncherUiState(
                            attemptId = result.attemptId,
                        )
                        TestAttemptCreationResult.NoQuestions -> TestAttemptLauncherUiState(
                            isNoQuestions = true,
                        )
                        TestAttemptCreationResult.MissingScope -> TestAttemptLauncherUiState(
                            isMissingScope = true,
                        )
                    }
                    if (result is TestAttemptCreationResult.Created) {
                        _effects.tryEmit(TestAttemptLauncherEffect.Created(result.attemptId))
                    }
                }
                .onFailure { error ->
                    _uiState.value = TestAttemptLauncherUiState(
                        error = error.message ?: "Unable to start the test.",
                    )
                }
        }
    }
}
