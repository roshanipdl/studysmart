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
)

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
    private var pendingCreationRequestId: String? = null

    init {
        observe()
    }

    fun retry() {
        if (pendingCreationRequestId != null) {
            startDueReview()
        } else {
            observe()
        }
    }

    fun startDueReview() {
        val state = _uiState.value
        val courseId = state.courseId ?: return
        val academicPeriodId = state.academicPeriodId ?: return
        if (state.isLoading || state.isContextRequired || state.isNoDue || state.isStarting) return

        val requestId = pendingCreationRequestId ?: idGenerator.nextId("due-review-request").also {
            pendingCreationRequestId = it
        }
        viewModelScope.launch(dispatchers.main) {
            _uiState.value = _uiState.value.copy(isStarting = true, error = null)
            try {
                val result = preparationRepository.createDueReviewSession(
                    DueReviewSessionRequest(
                        courseId = courseId,
                        academicPeriodId = academicPeriodId,
                        creationRequestId = requestId,
                    ),
                )
                when (result) {
                    is PreparationCreationResult.Created -> {
                        pendingCreationRequestId = null
                        _uiState.value = _uiState.value.copy(isStarting = false, error = null)
                        _effects.tryEmit(ReviewEffect.OpenDueReview(result.sessionId))
                    }
                    PreparationCreationResult.NoQuestions -> {
                        pendingCreationRequestId = null
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
                        pendingCreationRequestId = null
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
                    val courseId = preferences.selectedCourseId
                    val academicPeriodId = preferences.selectedAcademicPeriodId
                    if (courseId.isNullOrBlank() || academicPeriodId.isNullOrBlank()) {
                        _uiState.value = ReviewUiState(
                            isLoading = false,
                            isContextRequired = true,
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
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isContextRequired = false,
            courseId = courseId,
            academicPeriodId = academicPeriodId,
            readiness = null,
            isReadinessLoading = true,
            readinessError = null,
            error = null,
        )
        kotlinx.coroutines.coroutineScope {
            launch {
                reviewRepository.observeDueReview(
                    courseId = courseId,
                    academicPeriodId = academicPeriodId,
                    now = clock.instant(),
                ).catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        courseId = courseId,
                        academicPeriodId = academicPeriodId,
                        error = error.message ?: "Unable to load due reviews.",
                    )
                }.collect { summary ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isContextRequired = false,
                        courseId = courseId,
                        academicPeriodId = academicPeriodId,
                        dueCount = summary.dueCount,
                        overdueCount = summary.overdueCount,
                        isNoDue = summary.dueCount == 0,
                        error = null,
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
