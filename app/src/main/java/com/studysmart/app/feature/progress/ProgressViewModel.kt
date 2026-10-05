package com.studysmart.app.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.common.StudyTimeSource
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.data.SelectedAcademicContext
import com.studysmart.app.core.model.PeriodSubjectsBrowseModel
import com.studysmart.app.feature.preparation.DueReviewSessionRequest
import com.studysmart.app.feature.preparation.PreparationCreationResult
import com.studysmart.app.feature.preparation.PreparationRepository
import com.studysmart.app.feature.readiness.ReadinessEstimate
import com.studysmart.app.feature.readiness.ReadinessRepository
import com.studysmart.app.feature.readiness.ReadinessScope
import com.studysmart.app.feature.review.DueReviewSummary
import com.studysmart.app.feature.review.ReviewRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProgressUiState(
    val isLoading: Boolean = true,
    val context: PeriodSubjectsBrowseModel? = null,
    val snapshot: ProgressSnapshot? = null,
    val readiness: ReadinessEstimate? = null,
    val reviews: DueReviewSummary = DueReviewSummary(0, 0),
    val hasError: Boolean = false,
    val isStarting: Boolean = false,
    val hasActionError: Boolean = false,
    val dailyGoal: Int = com.studysmart.app.core.model.DailyCardGoal.DEFAULT,
) {
    val canStartDue: Boolean get() = !isLoading && !hasError && !isStarting && !hasActionError && context != null && reviews.dueCount > 0
}

class ProgressViewModel(
    private val preferences: PreferencesRepository,
    private val curriculum: CurriculumRepository,
    private val progress: ProgressRepository,
    private val readiness: ReadinessRepository,
    private val review: ReviewRepository,
    private val preparation: PreparationRepository,
    private val time: StudyTimeSource,
    private val ids: IdGenerator,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val state = MutableStateFlow(ProgressUiState())
    val uiState = state.asStateFlow()
    private val pendingSession = MutableStateFlow<String?>(null)
    val sessions = pendingSession.filterNotNull()
    fun acknowledgeSession(sessionId: String) { if (pendingSession.value == sessionId) pendingSession.value = null }
    private var observer: Job? = null
    private var pending: DueReviewSessionRequest? = null
    private var contextKey: SelectedAcademicContext? = null
    private var questionCount = com.studysmart.app.core.model.PreparationDefaults.DEFAULT_QUESTION_COUNT
    private var starting = false
    private var generation = 0

    init { observe() }
    fun onForeground() { time.refresh() }
    fun retry() { if (!starting) observe() }
    fun retryDue() { if (!starting) createDue() }

    fun startDue() {
        if (!state.value.canStartDue) return
        createDue()
    }

    private fun createDue() {
        val context = state.value.context ?: return
        if (starting || state.value.isLoading || state.value.hasError) return
        val request = pending ?: DueReviewSessionRequest(context.course.id, context.period.id, ids.nextId("progress-review"), questionCount)
            .also { pending = it }
        if (request.courseId != context.course.id || request.academicPeriodId != context.period.id) { pending = null; return }
        val requestGeneration = generation
        starting = true
        state.value = state.value.copy(isStarting = true, hasActionError = false)
        viewModelScope.launch(dispatchers.main) {
            var reload = false
            try {
                val result = preparation.createDueReviewSession(request)
                if (requestGeneration != generation) {
                    if (result is PreparationCreationResult.Created) preparation.discardSession(result.sessionId)
                    return@launch
                }
                pending = null
                when (result) {
                    is PreparationCreationResult.Created -> pendingSession.value = result.sessionId
                    PreparationCreationResult.NoQuestions, PreparationCreationResult.MissingScope -> reload = true
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (requestGeneration == generation) state.value = state.value.copy(hasActionError = true) }
            finally {
                starting = false
                state.value = state.value.copy(isStarting = false)
                if (reload) observe()
            }
        }
    }

    private fun observe() {
        observer?.cancel()
        state.value = state.value.copy(isLoading = true, hasError = false)
        observer = viewModelScope.launch(dispatchers.main) {
            try {
                combine(preferences.preferences, curriculum.observeCourses()) { preference, courses -> preference to courses }
                    .collectLatest { (preference, courses) ->
                        val goal = com.studysmart.app.core.model.DailyCardGoal.normalize(preference.dailyCardGoal)
                        questionCount = preference.preparationDefaults.questionCount
                        val courseId = preference.selectedCourseId
                        val periodId = preference.selectedAcademicPeriodId
                        val key = if (!courseId.isNullOrBlank() && !periodId.isNullOrBlank()) SelectedAcademicContext(courseId, periodId) else null
                        if (contextKey != key) {
                            generation++
                            pending = null
                            pendingSession.value = null
                            contextKey = key
                            state.value = ProgressUiState(isStarting = starting, dailyGoal = goal)
                        }
                        if (courseId != null && courses.none { it.id == courseId }) {
                            preferences.clearSelectedCourseIfUnchanged(courseId, periodId)
                            state.value = ProgressUiState(isLoading = false, isStarting = starting, dailyGoal = goal)
                        } else if (key == null) {
                            state.value = ProgressUiState(isLoading = false, isStarting = starting, dailyGoal = goal)
                        } else {
                            curriculum.observePeriodSubjects(key.academicPeriodId).collectLatest { context ->
                                if (context == null || context.course.id != key.courseId) {
                                    preferences.clearSelectedPeriodIfUnchanged(key.courseId, key.academicPeriodId)
                                    state.value = ProgressUiState(isLoading = false, isStarting = starting, dailyGoal = goal)
                                    return@collectLatest
                                }
                                state.value = state.value.copy(isLoading = true, context = context, hasError = false, dailyGoal = goal)
                                combine(progress.observeProgress(key.courseId, key.academicPeriodId),
                                    readiness.observeReadiness(ReadinessScope(key.courseId, key.academicPeriodId)),
                                    review.observeDueReview(key.courseId, key.academicPeriodId)) { snapshot, estimate, due -> Triple(snapshot, estimate, due) }
                                    .collect { (snapshot, estimate, due) ->
                                        state.value = state.value.copy(isLoading = false, snapshot = snapshot, readiness = estimate, reviews = due,
                                            hasError = false, isStarting = starting)
                                    }
                            }
                        }
                    }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.value = state.value.copy(isLoading = false, hasError = true) }
        }
    }
}
