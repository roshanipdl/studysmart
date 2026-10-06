package com.studysmart.app.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.feature.preparation.DueReviewSessionRequest
import com.studysmart.app.feature.preparation.PreparationCreationResult
import com.studysmart.app.feature.preparation.PreparationRepository
import com.studysmart.app.feature.readiness.ReadinessEstimate
import com.studysmart.app.feature.readiness.ReadinessRepository
import com.studysmart.app.feature.readiness.ReadinessScope
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ReviewUiState(
    val contextLabel: String = "",
    val statusQuestions: List<com.studysmart.app.core.database.ReadinessQuestionRow> = emptyList(),
    val isLoading: Boolean = true,
    val isContextRequired: Boolean = false,
    val courseId: String? = null,
    val academicPeriodId: String? = null,
    val dueCount: Int = 0,
    val overdueCount: Int = 0,
    val isNoDue: Boolean = false,
    val isStarting: Boolean = false,
    val readiness: ReadinessEstimate? = null,
    val isReadinessLoading: Boolean = false,
    val readinessError: String? = null,
    val error: String? = null,
    val statusError: String? = null,
    val dueError: String? = null,
    val sessionQuestionCount: Int = com.studysmart.app.core.model.PreparationDefaults.DEFAULT_QUESTION_COUNT,
) {
    val displayError: String? get() = error ?: statusError ?: dueError
    val statusPools: Map<ReviewFilter, List<com.studysmart.app.core.database.ReadinessQuestionRow>> =
        ReviewFilter.entries.associateWith { it.select(statusQuestions) }
}

sealed interface ReviewEffect {
    data class OpenDueReview(val sessionId: String) : ReviewEffect
}

class ReviewViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val curriculumRepository: CurriculumRepository,
    private val reviewRepository: ReviewRepository,
    private val readinessRepository: ReadinessRepository,
    private val preparationRepository: PreparationRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ReviewEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ReviewEffect> = _effects.asSharedFlow()

    private var observeJob: Job? = null
    private var pendingRequest: DueReviewSessionRequest? = null
    private var creationInProgress = false
    private var statusRequest: com.studysmart.app.feature.preparation.StatusFilterSessionRequest? = null

    fun startStatusReview(filter: ReviewFilter) {
        val state = _uiState.value
        val course = state.courseId ?: return
        val period = state.academicPeriodId ?: return
        if (state.isLoading || state.isContextRequired || state.statusError != null || state.isStarting || creationInProgress || state.statusPools[filter].isNullOrEmpty()) return
        val request = statusRequest?.takeIf { it.courseId == course && it.academicPeriodId == period && it.filter == filter }
            ?: com.studysmart.app.feature.preparation.StatusFilterSessionRequest(course, period, filter,
                idGenerator.nextId("status-review-request"), state.sessionQuestionCount).also { statusRequest = it }
        creationInProgress = true
        _uiState.value = state.copy(isStarting = true, error = null)
        viewModelScope.launch(dispatchers.main) {
            var navigationCommitted = false
            try {
                val result = preparationRepository.createStatusFilterSession(request)
                val current = _uiState.value
                if (current.courseId != course || current.academicPeriodId != period || current.isContextRequired) {
                    if (result is PreparationCreationResult.Created) preparationRepository.discardSession(result.sessionId)
                    statusRequest = null
                    return@launch
                }
                when (result) {
                    is PreparationCreationResult.Created -> {
                        navigationCommitted = true
                        _uiState.value = current.copy(isStarting = true)
                        _effects.tryEmit(ReviewEffect.OpenDueReview(result.sessionId))
                    }
                    PreparationCreationResult.NoQuestions -> _uiState.value = current.copy(statusQuestions = emptyList(), isStarting = false)
                    PreparationCreationResult.MissingScope -> _uiState.value = current.copy(isContextRequired = true, isStarting = false)
                }
                statusRequest = null
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(error = error.message ?: "Unable to start review.")
            } finally {
                creationInProgress = false
                if (!navigationCommitted) _uiState.value = _uiState.value.copy(isStarting = false)
            }
        }
    }

    init {
        observe()
    }

    fun retry() {
        if (_uiState.value.statusError != null || _uiState.value.dueError != null) {
            observe()
        } else if (statusRequest != null) {
            startStatusReview(statusRequest!!.filter)
        } else if (pendingRequest != null) {
            startDueReview()
        } else {
            observe()
        }
    }

    fun startDueReview() {
        val state = _uiState.value
        val courseId = state.courseId ?: return
        val academicPeriodId = state.academicPeriodId ?: return
        if (state.isLoading || state.isContextRequired || state.dueError != null || state.isNoDue || creationInProgress) return

        val request = pendingRequest?.takeIf {
            it.courseId == courseId && it.academicPeriodId == academicPeriodId
        } ?: DueReviewSessionRequest(courseId, academicPeriodId,
            idGenerator.nextId("due-review-request"), state.sessionQuestionCount).also { pendingRequest = it }
        creationInProgress = true
        _uiState.value = state.copy(isStarting = true, error = null)
        viewModelScope.launch(dispatchers.main) {
            try {
                val result = preparationRepository.createDueReviewSession(request)
                if (_uiState.value.courseId != courseId || _uiState.value.academicPeriodId != academicPeriodId || _uiState.value.isContextRequired) {
                    if (result is PreparationCreationResult.Created) preparationRepository.discardSession(result.sessionId)
                    pendingRequest = null
                    return@launch
                }
                when (result) {
                    is PreparationCreationResult.Created -> {
                        pendingRequest = null
                        _uiState.value = _uiState.value.copy(isStarting = false, error = null)
                        _effects.tryEmit(ReviewEffect.OpenDueReview(result.sessionId))
                    }
                    PreparationCreationResult.NoQuestions -> {
                        pendingRequest = null
                        _uiState.value = _uiState.value.copy(
                            isStarting = false,
                            dueCount = 0,
                            overdueCount = 0,
                            isNoDue = true,
                            error = null,
                        )
                        observe()
                    }
                    PreparationCreationResult.MissingScope -> {
                        pendingRequest = null
                        _uiState.value = _uiState.value.copy(
                            isStarting = false,
                            isContextRequired = true,
                            isNoDue = false,
                            error = null,
                        )
                        observe()
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    isStarting = false,
                    error = error.message ?: "Unable to start due review.",
                )
            } finally {
                creationInProgress = false
                _uiState.value = _uiState.value.copy(isStarting = false)
            }
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(dispatchers.main) {
            combine(
                preferencesRepository.preferences,
                curriculumRepository.observeCourses(),
            ) { preferences, courses -> preferences to courses }
                .catch { error ->
                    _uiState.value = ReviewUiState(
                        isLoading = false,
                        error = error.message ?: "Unable to load due reviews.",
                    )
                }
                .collectLatest { (preferences, courses) ->
                    _uiState.value = _uiState.value.copy(
                        sessionQuestionCount = preferences.preparationDefaults.questionCount,
                    )
                    val courseId = preferences.selectedCourseId
                    val academicPeriodId = preferences.selectedAcademicPeriodId
                    if (courseId.isNullOrBlank() || academicPeriodId.isNullOrBlank()) {
                        _uiState.value = ReviewUiState(
                            isLoading = false,
                            isContextRequired = true,
                            courseId = courses.firstOrNull { it.id == courseId }?.id,
                            contextLabel = courses.firstOrNull { it.id == courseId }?.name ?: "",
                        )
                        return@collectLatest
                    }

                    if (courses.none { it.id == courseId }) {
                        try {
                            preferencesRepository.clearSelectedCourseIfUnchanged(courseId, academicPeriodId)
                            _uiState.value = ReviewUiState(
                                isLoading = false,
                                isContextRequired = true,
                            )
                        } catch (error: Throwable) {
                            if (error is CancellationException) throw error
                            showError(error)
                        }
                        return@collectLatest
                    }

                    try {
                        if (curriculumRepository.isValidPeriod(courseId, academicPeriodId)) {
                            observeDueReview(courseId, academicPeriodId)
                        } else {
                            preferencesRepository.clearSelectedPeriodIfUnchanged(courseId, academicPeriodId)
                            _uiState.value = ReviewUiState(
                                isLoading = false,
                                isContextRequired = true,
                                courseId = courseId,
                                contextLabel = courses.firstOrNull { it.id == courseId }?.name ?: "",
                            )
                        }
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        showError(error)
                    }
                }
        }
    }

    private fun showError(error: Throwable) {
        _uiState.value = ReviewUiState(
            isLoading = false,
            error = error.message ?: "Unable to load due reviews.",
        )
    }

    private suspend fun observeDueReview(courseId: String, academicPeriodId: String) {
        val sameContext = _uiState.value.courseId == courseId && _uiState.value.academicPeriodId == academicPeriodId
        _uiState.value = _uiState.value.copy(
            contextLabel = if (sameContext) _uiState.value.contextLabel else "",
            statusQuestions = if (sameContext) _uiState.value.statusQuestions else emptyList(),
            isLoading = true,
            isContextRequired = false,
            courseId = courseId,
            academicPeriodId = academicPeriodId,
            readiness = null,
            isReadinessLoading = true,
            readinessError = null,
            error = null,
            statusError = null,
            dueError = null,
        )
        kotlinx.coroutines.coroutineScope {
            launch {
                curriculumRepository.observePeriodSubjects(academicPeriodId).collect { context ->
                    if (context == null) {
                        _uiState.value = ReviewUiState(isLoading = false, isContextRequired = true)
                    } else {
                        _uiState.value = _uiState.value.copy(contextLabel = "${context.course.name} • ${context.period.name}")
                    }
                }
            }
            launch {
                reviewRepository.observeStatusQuestions(courseId, academicPeriodId).catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusError = error.message ?: "Unable to load review status questions.",
                    )
                }.collect { rows ->
                    _uiState.value = _uiState.value.copy(statusQuestions = rows, statusError = null)
                }
            }
            launch {
                reviewRepository.observeDueReview(
                    courseId = courseId,
                    academicPeriodId = academicPeriodId,
                ).catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        courseId = courseId,
                        academicPeriodId = academicPeriodId,
                        dueError = error.message ?: "Unable to load due reviews.",
                    )
                }.collect { summary ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        courseId = courseId,
                        academicPeriodId = academicPeriodId,
                        dueCount = summary.dueCount,
                        overdueCount = summary.overdueCount,
                        isNoDue = summary.dueCount == 0,
                        dueError = null,
                    )
                }
            }
            launch {
                readinessRepository.observeReadiness(
                    ReadinessScope(courseId = courseId, academicPeriodId = academicPeriodId),
                ).catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isReadinessLoading = false,
                        readinessError = error.message ?: "Unable to load readiness estimate.",
                    )
                }.collect { estimate ->
                    _uiState.value = _uiState.value.copy(
                        isReadinessLoading = false,
                        readiness = estimate,
                        readinessError = null,
                    )
                }
            }
        }
    }
}
