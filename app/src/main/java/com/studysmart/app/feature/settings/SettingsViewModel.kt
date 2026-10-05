package com.studysmart.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.model.PreparationDefaults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val preparationDefaults: PreparationDefaults? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaveError: Boolean = false,
    val dailyCardGoal: Int? = null,
    val goalError: String? = null,
    val isGoalSaveError: Boolean = false,
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null
    private var pendingDefaults: PreparationDefaults? = null
    private var pendingGoal: Int? = null

    init { observe() }

    fun decrementQuestionCount() = changeCount(-1)
    fun incrementQuestionCount() = changeCount(1)
    fun decrementDailyGoal() = changeGoal(-1)
    fun incrementDailyGoal() = changeGoal(1)

    fun retry() {
        if (_uiState.value.isSaving) return
        when {
            pendingGoal != null -> persistGoal(requireNotNull(pendingGoal))
            pendingDefaults != null -> persist(requireNotNull(pendingDefaults))
            else -> observe()
        }
    }

    private fun changeCount(delta: Int) {
        val state = _uiState.value
        val current = state.preparationDefaults ?: return
        if (state.isLoading || state.isSaving || state.isSaveError || state.isGoalSaveError) return
        val count = (current.questionCount + delta).coerceIn(
            PreparationDefaults.MIN_QUESTION_COUNT, PreparationDefaults.MAX_QUESTION_COUNT,
        )
        if (count == current.questionCount) return
        val updated = PreparationDefaults(count)
        pendingDefaults = updated
        persist(updated)
    }

    private fun changeGoal(delta: Int) {
        val state = _uiState.value
        val current = state.dailyCardGoal ?: return
        if (state.isLoading || state.isSaving || state.isSaveError || state.isGoalSaveError) return
        val goal = (current + delta).coerceIn(com.studysmart.app.core.model.DailyCardGoal.MIN, com.studysmart.app.core.model.DailyCardGoal.MAX)
        if (goal == current) return
        pendingGoal = goal
        persistGoal(goal)
    }

    private fun persistGoal(goal: Int) {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(isSaving = true, goalError = null, isGoalSaveError = false)
        viewModelScope.launch(dispatchers.main) {
            try {
                preferencesRepository.updateDailyCardGoal(goal)
                pendingGoal = null
                _uiState.value = _uiState.value.copy(dailyCardGoal = goal, goalError = null, isGoalSaveError = false)
            } catch (error: CancellationException) {
                _uiState.value = _uiState.value.copy(isGoalSaveError = true, goalError = "Daily goal was not saved. Retry to save it.")
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isGoalSaveError = true, goalError = error.message ?: "Unable to save daily goal.")
            } finally { _uiState.value = _uiState.value.copy(isSaving = false) }
        }
    }

    private fun persist(defaults: PreparationDefaults) {
        if (_uiState.value.isSaving) return
        // Reserve admission before dispatching: rapid taps cannot enqueue two writes.
        _uiState.value = _uiState.value.copy(isSaving = true, error = null, isSaveError = false)
        viewModelScope.launch(dispatchers.main) {
            try {
                preferencesRepository.updatePreparationDefaults(defaults)
                pendingDefaults = null
                _uiState.value = _uiState.value.copy(
                    isLoading = false, preparationDefaults = defaults, error = null, isSaveError = false,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    _uiState.value = _uiState.value.copy(
                        isSaveError = true, error = "Changes were not saved. Retry to save the question count.",
                    )
                    throw error
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false, error = error.message ?: "Unable to save preparation and review count.",
                    isSaveError = true,
                )
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    private fun observe() {
        observeJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, isSaveError = false)
        observeJob = viewModelScope.launch(dispatchers.main) {
            preferencesRepository.preferences.catch { error ->
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    isLoading = false, error = error.message ?: "Unable to load preparation and review count.",
                )
            }.collect { preferences ->
                if (pendingGoal == null) _uiState.value = _uiState.value.copy(
                    dailyCardGoal = com.studysmart.app.core.model.DailyCardGoal.normalize(preferences.dailyCardGoal),
                    goalError = null, isGoalSaveError = false,
                )
                if (pendingDefaults == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, preparationDefaults = preferences.preparationDefaults,
                        error = null, isSaveError = false,
                    )
                }
            }
        }
    }
}
