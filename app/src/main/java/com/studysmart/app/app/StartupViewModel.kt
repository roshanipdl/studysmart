package com.studysmart.app.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class StartupUiState(
    val isLoading: Boolean = true,
    val isReady: Boolean = false,
    val errorMessage: String? = null,
)

class StartupViewModel(
    private val cleanupCoordinator: StartupCleanupCoordinator,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StartupUiState())
    val uiState: StateFlow<StartupUiState> = _uiState.asStateFlow()
    private var cleanupJob: Job? = null

    init {
        startCleanup()
    }

    fun retry() {
        startCleanup()
    }

    private fun startCleanup() {
        cleanupJob?.cancel()
        _uiState.value = StartupUiState()
        cleanupJob = viewModelScope.launch(dispatcher) {
            runCatching { cleanupCoordinator.cleanup() }
                .onSuccess {
                    _uiState.value = StartupUiState(
                        isLoading = false,
                        isReady = true,
                    )
                }
                .onFailure { error ->
                    _uiState.value = StartupUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to initialize StudySmart.",
                    )
                }
        }
    }
}
