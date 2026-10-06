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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.LocalUserRepository
import com.studysmart.app.core.data.normalizedDisplayName
import com.studysmart.app.core.model.ThemePreference
import com.studysmart.app.core.model.TestDefaults

data class SettingsUiState(
    val clearConfirmation: Boolean = false,
    val clearError: String? = null,
    val clearSucceeded: Boolean = false,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val testDefaults: TestDefaults? = null,
    val courseId: String? = null,
    val courseName: String? = null,
    val periodName: String? = null,
    val contextError: String? = null,
    val displayName: String? = null,
    val nameDraft: String? = null,
    val nameError: String? = null,
    val preferenceError: String? = null,
    val isPreferenceSaveError: Boolean = false,
    val isLoading: Boolean = true,
    val preparationDefaults: PreparationDefaults? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaveError: Boolean = false,
    val dailyCardGoal: Int? = null,
    val goalError: String? = null,
    val isGoalSaveError: Boolean = false,
) {
    val hasSaveError: Boolean get() = isSaveError || isGoalSaveError || isPreferenceSaveError
    val canMutate: Boolean get() = !isLoading && !isSaving && !hasSaveError && !clearConfirmation
}

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: DispatcherProvider,
    private val curriculumRepository: CurriculumRepository? = null,
    private val localUserRepository: LocalUserRepository? = null,
    private val learningDataRepository: com.studysmart.app.core.data.LearningDataRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null
    private var pendingDefaults: PreparationDefaults? = null
    private var pendingGoal: Int? = null
    private var pendingTheme: ThemePreference? = null
    private var pendingName: String? = null
    private var userJob: Job? = null
    private var contextJob: Job? = null

    init { observe(); observeUser(); observeContext() }

    fun requestClear() {
        if (!_uiState.value.canMutate || learningDataRepository == null) return
        _uiState.value = _uiState.value.copy(clearConfirmation = true, clearError = null, clearSucceeded = false)
    }

    fun cancelClear() {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(clearConfirmation = false, clearError = null)
    }

    fun confirmClear() {
        val state = _uiState.value
        val repository = learningDataRepository ?: return
        if (!state.clearConfirmation || state.isSaving || state.hasSaveError) return
        _uiState.value = state.copy(isSaving = true, clearError = null, clearSucceeded = false)
        viewModelScope.launch(dispatchers.main) {
            try {
                repository.clearLocalLearningData()
                _uiState.value = _uiState.value.copy(clearConfirmation = false, clearSucceeded = true)
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(clearError = "Learning progress was not cleared. Retry or cancel.")
                if (error is CancellationException) throw error
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    fun editName() {
        if (!_uiState.value.canMutate) return
        _uiState.value.displayName?.let { _uiState.value = _uiState.value.copy(nameDraft = it, nameError = null) }
    }

    fun changeName(value: String) {
        if (_uiState.value.isSaving || pendingName != null) return
        _uiState.value = _uiState.value.copy(nameDraft = value, nameError = null)
    }

    fun cancelName() {
        if (_uiState.value.isSaving) return
        pendingName = null
        _uiState.value = _uiState.value.copy(nameDraft = null, nameError = null,
            isPreferenceSaveError = pendingTheme != null, preferenceError = null)
    }

    fun saveName() {
        if (!_uiState.value.canMutate) return
        val draft = _uiState.value.nameDraft ?: return
        val name = try { normalizedDisplayName(draft) } catch (error: IllegalArgumentException) {
            _uiState.value = _uiState.value.copy(nameError = error.message)
            return
        }
        pendingName = name
        persistPreference()
    }

    fun setTheme(theme: ThemePreference) {
        if (!_uiState.value.canMutate || theme == _uiState.value.theme) return
        pendingTheme = theme
        persistPreference()
    }

    private fun persistPreference() {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(isSaving = true, preferenceError = null, isPreferenceSaveError = false)
        viewModelScope.launch(dispatchers.main) {
            try {
                pendingName?.let {
                    requireNotNull(localUserRepository).updateDisplayName(it)
                    pendingName = null
                    _uiState.value = _uiState.value.copy(displayName = it, nameDraft = null, nameError = null)
                }
                pendingTheme?.let {
                    preferencesRepository.updateTheme(it)
                    pendingTheme = null
                    _uiState.value = _uiState.value.copy(theme = it)
                }
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isPreferenceSaveError = true,
                    preferenceError = "Changes were not saved. Retry to save them.")
                if (error is CancellationException) throw error
            } finally { _uiState.value = _uiState.value.copy(isSaving = false) }
        }
    }

    private fun observeUser() {
        val repository = localUserRepository ?: return
        userJob?.cancel()
        userJob = viewModelScope.launch(dispatchers.main) {
            repository.displayName.catch { error ->
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(nameError = "Unable to load local name. Retry.")
            }.collect { name ->
                if (pendingName == null) _uiState.value = _uiState.value.copy(displayName = name,
                    nameError = if (name == null) "Local user is unavailable." else null)
            }
        }
    }

    private fun observeContext() {
        val repository = curriculumRepository ?: return
        contextJob?.cancel()
        contextJob = viewModelScope.launch(dispatchers.main) {
            combine(preferencesRepository.preferences, repository.observeCourses()) { preferences, courses -> preferences to courses }
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = _uiState.value.copy(contextError = "Unable to load study context. Retry.")
                }.collectLatest { (preferences, courses) ->
                    val courseId = preferences.selectedCourseId
                    val periodId = preferences.selectedAcademicPeriodId
                    val course = courses.firstOrNull { it.id == courseId }
                    _uiState.value = _uiState.value.copy(courseId = course?.id, courseName = course?.name,
                        periodName = null, contextError = null)
                    try {
                        if (courseId != null && course == null) {
                            preferencesRepository.clearSelectedCourseIfUnchanged(courseId, periodId)
                        } else if (course != null && periodId != null) {
                            repository.observeCoursePeriods(course.id).collect { context ->
                                val period = context?.periods?.firstOrNull { it.id == periodId }
                                if (period == null) preferencesRepository.clearSelectedPeriodIfUnchanged(course.id, periodId)
                                _uiState.value = _uiState.value.copy(periodName = period?.name)
                            }
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        _uiState.value = _uiState.value.copy(contextError = "Unable to verify study context. Retry.")
                    }
                }
        }
    }

    fun decrementQuestionCount() = changeCount(-1)
    fun incrementQuestionCount() = changeCount(1)
    fun decrementDailyGoal() = changeGoal(-1)
    fun incrementDailyGoal() = changeGoal(1)

    fun retry() {
        if (_uiState.value.isSaving) return
        when {
            pendingTheme != null || pendingName != null -> persistPreference()
            pendingGoal != null -> persistGoal(requireNotNull(pendingGoal))
            pendingDefaults != null -> persist(requireNotNull(pendingDefaults))
            else -> { observe(); observeUser(); observeContext() }
        }
    }

    private fun changeCount(delta: Int) {
        val state = _uiState.value
        val current = state.preparationDefaults ?: return
        if (!state.canMutate) return
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
        if (!state.canMutate) return
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
                _uiState.value = _uiState.value.copy(testDefaults = preferences.testDefaults,
                    theme = if (pendingTheme == null) preferences.theme else _uiState.value.theme)
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
