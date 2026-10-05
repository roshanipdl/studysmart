package com.studysmart.app.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.studysmart.app.core.data.importer.NoOpCurriculumImporter

data class StartupUiState(
    val isLoading: Boolean = true,
    val isReady: Boolean = false,
    val errorMessage: String? = null,
)

class StartupViewModel(
    private val initializationCoordinator: StartupInitializationCoordinator,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    /** Existing fixture callers can retain cleanup-only startup. */
    constructor(cleanupCoordinator: StartupCleanupCoordinator, dispatcher: CoroutineDispatcher) :
        this(DefaultStartupInitializationCoordinator(NoOpCurriculumImporter, cleanupCoordinator), dispatcher)
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
        if (cleanupJob?.isActive == true) return
        _uiState.value = StartupUiState()
        cleanupJob = viewModelScope.launch(dispatcher) {
            runCatching { initializationCoordinator.initialize() }
                .onSuccess {
                    _uiState.value = StartupUiState(
                        isLoading = false,
                        isReady = true,
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = StartupUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to initialize StudySmart.",
                    )
                }
        }
    }
}
