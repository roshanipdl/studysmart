package com.studysmart.app.feature.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.model.TestQuestionSource
import kotlinx.coroutines.CancellationException
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
    val noQuestionsSource: TestQuestionSource? = null,
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
        val state = _uiState.value
        if (state.isCreating || state.attemptId != null || state.isNoQuestions || state.error != null) return
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
        request = null
        _uiState.value = TestAttemptLauncherUiState()
    }

    fun onCreatedNavigationHandled() {
        request = null
        _uiState.value = TestAttemptLauncherUiState()
    }

    private fun create() {
        val currentRequest = request ?: return
        viewModelScope.launch(dispatchers.main) {
            _uiState.value = TestAttemptLauncherUiState(isCreating = true)
            try {
                when (val result = repository.createChapterAttempt(currentRequest)) {
                    is TestAttemptCreationResult.Created -> {
                        _uiState.value = TestAttemptLauncherUiState(attemptId = result.attemptId)
                        _effects.tryEmit(TestAttemptLauncherEffect.Created(result.attemptId))
                    }
                    is TestAttemptCreationResult.NoQuestions -> {
                        request = null
                        _uiState.value = TestAttemptLauncherUiState(
                            isNoQuestions = true,
                            noQuestionsSource = result.questionSource,
                        )
                    }
                    TestAttemptCreationResult.MissingScope -> {
                        _uiState.value = TestAttemptLauncherUiState(isMissingScope = true)
                    }
                    TestAttemptCreationResult.MissingSource -> {
                        _uiState.value = TestAttemptLauncherUiState(
                            error = "The source test result is no longer available.",
                        )
                    }
                    TestAttemptCreationResult.CorruptSource -> {
                        _uiState.value = TestAttemptLauncherUiState(
                            error = "The source test result cannot be used safely.",
                        )
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = TestAttemptLauncherUiState(
                    error = error.message ?: "Unable to start the test.",
                )
            }
        }
    }
}
