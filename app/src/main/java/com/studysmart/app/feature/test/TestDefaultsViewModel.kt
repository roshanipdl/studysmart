package com.studysmart.app.feature.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.TestQuestionSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class TestDefaultsUiState(
    val isLoading: Boolean = true,
    val defaults: TestDefaults? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaveError: Boolean = false,
)

class TestDefaultsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TestDefaultsUiState())
    val uiState: StateFlow<TestDefaultsUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var pendingDefaults: TestDefaults? = null

    init {
        observe()
    }

    fun decrementQuestionCount() = updateDefaults { defaults ->
        defaults.copy(questionCount = (defaults.questionCount - 1).coerceAtLeast(TestDefaults.MIN_QUESTION_COUNT))
    }

    fun incrementQuestionCount() = updateDefaults { defaults ->
        defaults.copy(questionCount = (defaults.questionCount + 1).coerceAtMost(TestDefaults.MAX_QUESTION_COUNT))
    }

    fun setTimedMode(enabled: Boolean) = updateDefaults { it.copy(timedMode = enabled) }

    fun setDurationMinutes(minutes: Int) = updateDefaults { defaults ->
        if (
            !defaults.timedMode ||
            minutes !in TestDefaults.MIN_DURATION_MINUTES..TestDefaults.MAX_DURATION_MINUTES
        ) {
            defaults
        } else {
            defaults.copy(durationMinutes = minutes)
        }
    }

    fun setQuestionSource(source: TestQuestionSource) = updateDefaults { it.copy(questionSource = source) }

    fun retry() {
        val pending = pendingDefaults
        if (pending != null) {
            persist(pending)
        } else {
            observe()
        }
    }

    private fun updateDefaults(transform: (TestDefaults) -> TestDefaults) {
        val state = _uiState.value
        val current = state.defaults ?: return
        if (state.isLoading || state.isSaving || state.isSaveError) return
        val updated = transform(current)
        if (updated == current) return
        pendingDefaults = updated
        persist(updated)
    }

    private fun persist(defaults: TestDefaults) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch(dispatchers.main) {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, isSaveError = false)
            try {
                preferencesRepository.updateTestDefaults(defaults)
                if (pendingDefaults == defaults) pendingDefaults = null
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    defaults = defaults,
                    isSaving = false,
                    error = null,
                    isSaveError = false,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSaving = false,
                    error = error.message ?: "Unable to save test defaults.",
                    isSaveError = true,
                )
            }
        }
    }

    private fun observe() {
        observeJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, isSaveError = false)
        observeJob = viewModelScope.launch(dispatchers.main) {
            preferencesRepository.preferences
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Unable to load test defaults.",
                        isSaveError = false,
                    )
                }
                .collect { preferences ->
                    if (pendingDefaults == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            defaults = preferences.testDefaults,
                            error = null,
                            isSaveError = false,
                        )
                    }
                }
        }
    }
}
