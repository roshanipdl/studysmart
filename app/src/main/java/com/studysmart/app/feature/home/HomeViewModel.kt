package com.studysmart.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.data.LocalUserRepository
import com.studysmart.app.core.data.BookmarkRepository
import com.studysmart.app.core.database.RecentCompletedStudyRow
import com.studysmart.app.feature.review.ReviewRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.studysmart.app.core.model.SavedQuestionScope

data class HomeUiState(
    val isLoading: Boolean = true,
    val courses: List<HomeCourseUiModel> = emptyList(),
    val selectedCourseId: String? = null,
    val selectedAcademicPeriodId: String? = null,
    val recentStudy: String? = null,
    val error: String? = null,
    val isSelecting: Boolean = false,
    val selectionError: String? = null,
    val displayName: String = "User",
    val recentContext: RecentCompletedStudyRow? = null,
    val dueCount: Int = 0,
    val savedCount: Int = 0,
    val historyError: String? = null,
    val actionsError: String? = null,
    val profileError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: DispatcherProvider,
    private val curriculumRepository: CurriculumRepository? = null,
    private val localUserRepository: LocalUserRepository? = null,
    private val reviewRepository: ReviewRepository? = null,
    private val bookmarkRepository: BookmarkRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var pendingSelection: Pair<String, () -> Unit>? = null
    private var sectionsJob: Job? = null

    init {
        observeHome()
        observeSections()
    }

    fun retry() {
        if (_uiState.value.isSelecting) return
        observeHome()
        observeSections()
    }

    private fun observeSections() {
        sectionsJob?.cancel()
        sectionsJob = viewModelScope.launch(dispatchers.main) {
            launch {
                (localUserRepository?.displayName ?: flowOf(null))
                    .catch { _uiState.value = _uiState.value.copy(profileError = "Unable to load your name. Retry.") }
                    .collect { _uiState.value = _uiState.value.copy(displayName = it?.trim()?.takeIf(String::isNotBlank) ?: "User", profileError = null) }
            }
            launch {
                homeRepository.observeRecentStudy()
                    .catch { _uiState.value = _uiState.value.copy(recentContext = null, historyError = "Unable to load recent study. Retry.") }
                    .collect { _uiState.value = _uiState.value.copy(recentContext = it, historyError = null) }
            }
            launch {
                preferencesRepository.preferences.flatMapLatest { preferences ->
                    _uiState.value = _uiState.value.copy(dueCount = 0, savedCount = 0, actionsError = null)
                    val course = preferences.selectedCourseId
                    val period = preferences.selectedAcademicPeriodId
                    if (course == null || period == null || curriculumRepository == null) {
                        flowOf(0 to 0)
                    } else curriculumRepository.observeCoursePeriods(course).flatMapLatest { context ->
                        _uiState.value = _uiState.value.copy(dueCount = 0, savedCount = 0)
                        if (context?.periods?.none { it.id == period } != false) flowOf(0 to 0)
                        else combine(
                            reviewRepository?.observeDueReview(course, period)?.map { it.dueCount } ?: flowOf(0),
                            bookmarkRepository?.observeSavedQuestions(SavedQuestionScope(course, period))?.map { it.size } ?: flowOf(0),
                        ) { due, saved -> due to saved }
                    }
                }.catch { _uiState.value = _uiState.value.copy(dueCount = 0, savedCount = 0, actionsError = "Unable to load quick actions. Retry.") }
                    .collect { (due, saved) -> _uiState.value = _uiState.value.copy(dueCount = due, savedCount = saved, actionsError = null) }
            }
        }
    }

    fun retrySelection() {
        if (_uiState.value.isSelecting) return
        pendingSelection?.let { (courseId, onSelected) -> persistSelection(courseId, onSelected) }
    }

    private fun observeHome() {
        observeJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        observeJob = viewModelScope.launch(dispatchers.main) {
            combine(
                homeRepository.observeCourses(),
                preferencesRepository.preferences,
            ) { courses, preferences ->
                HomeUiState(
                    isLoading = false,
                    courses = courses,
                    selectedCourseId = preferences.selectedCourseId,
                    selectedAcademicPeriodId = preferences.selectedAcademicPeriodId,
                )
            }
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Unable to load courses.",
                    )
                }
                .collectLatest { state ->
                    _uiState.value = state.copy(
                        displayName = _uiState.value.displayName,
                        recentContext = _uiState.value.recentContext,
                        dueCount = _uiState.value.dueCount,
                        savedCount = _uiState.value.savedCount,
                        historyError = _uiState.value.historyError,
                        actionsError = _uiState.value.actionsError,
                        profileError = _uiState.value.profileError,
                        isSelecting = _uiState.value.isSelecting,
                        selectionError = _uiState.value.selectionError,
                    )
                    try {
                        withContext(dispatchers.io) { validateSelectedContext(state) }
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        _uiState.value = _uiState.value.copy(error = "Unable to verify your study context. Retry.")
                    }
                }
        }
    }

    fun selectCourse(courseId: String, onSelected: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isLoading || state.isSelecting || state.selectionError != null ||
            state.courses.none { it.id == courseId }
        ) return
        pendingSelection = courseId to onSelected
        persistSelection(courseId, onSelected)
    }

    private fun persistSelection(courseId: String, onSelected: () -> Unit) {
        _uiState.value = _uiState.value.copy(isSelecting = true, selectionError = null)
        viewModelScope.launch(dispatchers.main) {
            try {
                withContext(dispatchers.io) { preferencesRepository.selectCourse(courseId) }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(selectionError = "Unable to save your course. Retry.")
                return@launch
            } finally {
                _uiState.value = _uiState.value.copy(isSelecting = false)
            }
            pendingSelection = null
            onSelected()
        }
    }

    private suspend fun validateSelectedContext(state: HomeUiState) {
        val courseId = state.selectedCourseId ?: return
        if (state.courses.none { it.id == courseId }) {
            preferencesRepository.clearSelectedCourseIfUnchanged(courseId, state.selectedAcademicPeriodId)
            return
        }
        val periodId = state.selectedAcademicPeriodId ?: return
        val repository = curriculumRepository ?: return
        if (!repository.isValidPeriod(courseId, periodId)) {
            preferencesRepository.clearSelectedPeriodIfUnchanged(courseId, periodId)
        }
    }
}
