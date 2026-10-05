package com.studysmart.app.feature.important

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.data.BookmarkRepository
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.database.SavedQuestionRow
import com.studysmart.app.core.model.ChapterBrowseModel
import com.studysmart.app.core.model.PeriodSubjectsBrowseModel
import com.studysmart.app.core.model.SavedQuestionScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class ImportantUiState(
    val isLoading: Boolean = true,
    val context: PeriodSubjectsBrowseModel? = null,
    val scope: SavedQuestionScope? = null,
    val chapters: List<ChapterBrowseModel> = emptyList(),
    val rows: List<SavedQuestionRow> = emptyList(),
    val removedRows: List<SavedQuestionRow> = emptyList(),
    val removingId: String? = null,
    val hasError: Boolean = false,
    val hasRemoveError: Boolean = false,
    val isStarting: Boolean = false,
    val hasLaunchError: Boolean = false,
    val noQuestions: Boolean = false,
    val noTestQuestions: Boolean = false,
) {
    val displayedRows: List<SavedQuestionRow> get() = (rows + removedRows)
        .distinctBy { it.questionId }.sortedWith(compareBy<SavedQuestionRow> { it.bookmarkedAt }.thenBy { it.questionId })
}

private data class ImportantFilters(val subjectId: String? = null, val chapterId: String? = null)
private data class PendingRemoval(val row: SavedQuestionRow, val generation: Int)
private data class PendingLaunch(val scope: SavedQuestionScope, val generation: Int, val id: String,
    val prepare: Boolean, val questionCount: Int? = null)

sealed interface ImportantEffect {
    data class OpenPreparation(val sessionId: String) : ImportantEffect
    data class OpenTest(val attemptId: String) : ImportantEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
class ImportantViewModel(
    private val preferences: PreferencesRepository,
    private val curriculum: CurriculumRepository,
    private val bookmarks: BookmarkRepository,
    private val dispatchers: DispatcherProvider,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val preparation: com.studysmart.app.feature.preparation.PreparationRepository? = null,
    private val tests: com.studysmart.app.feature.test.TestAttemptRepository? = null,
    private val ids: com.studysmart.app.core.common.IdGenerator? = null,
) : ViewModel() {
    private val state = MutableStateFlow(ImportantUiState())
    val uiState = state.asStateFlow()
    private val events = MutableSharedFlow<ImportantEffect>(extraBufferCapacity = 1)
    val effects = events.asSharedFlow()
    private val filters = MutableStateFlow(ImportantFilters(savedState["important_subject"], savedState["important_chapter"]))
    private var observer: Job? = null
    private var pendingRemoval: PendingRemoval? = null
    private var generation = 0
    private var writing = false
    private var launching = false
    private var pendingLaunch: PendingLaunch? = null

    init { refresh() }

    fun selectSubject(id: String?) {
        if (writing || launching || state.value.isLoading || state.value.hasError) return
        if (id != null && state.value.context?.subjects?.none { it.id == id } != false) return
        if (filters.value.subjectId != id) changeFilters(ImportantFilters(id))
    }

    fun selectChapter(id: String?) {
        if (writing || launching || state.value.isLoading || state.value.hasError) return
        if (id != null && state.value.chapters.none { it.id == id }) return
        if (filters.value.chapterId != id) changeFilters(filters.value.copy(chapterId = id))
    }

    private fun changeFilters(value: ImportantFilters) {
        generation++
        pendingRemoval = null
        pendingLaunch = null
        savedState["important_subject"] = value.subjectId
        savedState["important_chapter"] = value.chapterId
        state.value = state.value.copy(isLoading = true, rows = emptyList(), removedRows = emptyList(), hasRemoveError = false,
            hasLaunchError = false, noQuestions = false, noTestQuestions = false)
        filters.value = value
    }

    fun refresh() {
        if (writing || launching) return
        observer?.cancel()
        generation++
        pendingRemoval = null
        pendingLaunch = null
        state.value = ImportantUiState()
        observer = viewModelScope.launch(dispatchers.main) {
            combine(preferences.preferences, curriculum.observeCourses()) { preferences, courses ->
                Triple(preferences.selectedCourseId, preferences.selectedAcademicPeriodId,
                    courses.any { it.id == preferences.selectedCourseId })
            }.distinctUntilChanged().flatMapLatest { (courseId, periodId, courseExists) ->
                generation++
                pendingRemoval = null
                pendingLaunch = null
                state.value = ImportantUiState()
                if (courseId != null && !courseExists) {
                    preferences.clearSelectedCourseIfUnchanged(courseId, periodId)
                    return@flatMapLatest flowOf(ImportantUiState(isLoading = false))
                }
                val selection = if (courseId != null && periodId != null)
                    com.studysmart.app.core.data.SelectedAcademicContext(courseId, periodId) else null
                if (selection == null) return@flatMapLatest flowOf(ImportantUiState(isLoading = false))
                curriculum.observePeriodSubjects(selection.academicPeriodId).flatMapLatest { context ->
                    if (context == null || context.course.id != selection.courseId) {
                        preferences.clearSelectedPeriodIfUnchanged(selection.courseId, selection.academicPeriodId)
                        return@flatMapLatest flowOf(ImportantUiState(isLoading = false))
                    }
                    val contextKey = "${selection.courseId}/${selection.academicPeriodId}"
                    if (savedState.get<String>("important_context") != contextKey) {
                        savedState["important_context"] = contextKey
                        changeFilters(ImportantFilters())
                    }
                    filters.flatMapLatest { selected ->
                        val selectedSubject = selected.subjectId
                        val validSubject = selectedSubject?.takeIf { id -> context.subjects.any { it.id == id } }
                        if (selectedSubject != validSubject) {
                            changeFilters(ImportantFilters())
                            return@flatMapLatest flowOf(state.value)
                        }
                        val chaptersFlow = validSubject?.let(curriculum::observeSubjectChapters) ?: flowOf(null)
                        chaptersFlow.flatMapLatest { details ->
                            val choices = details?.chapters.orEmpty()
                            val validChapter = selected.chapterId?.takeIf { id -> choices.any { it.id == id } }
                            if (selected.chapterId != validChapter) {
                                changeFilters(selected.copy(chapterId = null))
                                return@flatMapLatest flowOf(state.value)
                            }
                            val scope = SavedQuestionScope(selection.courseId, selection.academicPeriodId, validSubject, validChapter)
                            bookmarks.observeSavedQuestions(scope).map { rows ->
                                ImportantUiState(isLoading = false, context = context, scope = scope, chapters = choices, rows = rows)
                            }
                        }
                    }
                }
            }.catch { error ->
                if (error is CancellationException) throw error
                state.value = state.value.copy(isLoading = false, hasError = true)
            }.collect { next ->
                val previous = state.value
                val removed = if (previous.scope == next.scope) previous.removedRows else emptyList()
                state.value = next.copy(
                    rows = next.rows.filterNot { row -> removed.any { it.questionId == row.questionId } },
                    removedRows = removed,
                    removingId = previous.removingId,
                    hasRemoveError = if (previous.scope == next.scope) previous.hasRemoveError else false,
                    isStarting = launching,
                    hasLaunchError = if (previous.scope == next.scope) previous.hasLaunchError else false,
                    noQuestions = if (previous.scope == next.scope) previous.noQuestions else false,
                    noTestQuestions = if (previous.scope == next.scope) previous.noTestQuestions else false,
                )
            }
        }
    }

    fun remove(questionId: String) {
        if (writing || launching || pendingRemoval != null || pendingLaunch != null || state.value.isLoading || state.value.hasError) return
        val row = state.value.rows.firstOrNull { it.questionId == questionId } ?: return
        pendingRemoval = PendingRemoval(row, generation)
        submitRemoval()
    }

    fun retryRemoval() { if (!writing) submitRemoval() }

    fun leaveScreen() {
        generation++
        pendingRemoval = null
        pendingLaunch = null
        state.value = state.value.copy(removedRows = emptyList(), hasRemoveError = false, hasLaunchError = false, noQuestions = false, noTestQuestions = false)
    }

    fun prepareSaved() = startSaved(true)
    fun testSaved() = startSaved(false)
    fun retryLaunch() { if (!launching && !writing) submitLaunch() }

    private fun startSaved(prepare: Boolean) {
        val current = state.value
        if (launching || writing || pendingLaunch != null || pendingRemoval != null || current.isLoading || current.hasError || current.rows.isEmpty()) return
        val scope = current.scope ?: return
        val id = ids?.nextId("important-request") ?: return
        pendingLaunch = PendingLaunch(scope, generation, id, prepare)
        submitLaunch()
    }

    private fun submitLaunch() {
        val original = pendingLaunch ?: return
        if (original.generation != generation) { pendingLaunch = null; return }
        launching = true
        var refreshAfterLaunch = false
        state.value = state.value.copy(isStarting = true, hasLaunchError = false, noQuestions = false, noTestQuestions = false)
        viewModelScope.launch(dispatchers.main) {
            try {
                if (original.prepare) {
                    val request = original.copy(questionCount = original.questionCount ?: preferences.preferences.first().preparationDefaults.questionCount)
                    if (original.generation == generation) pendingLaunch = request
                    val result = requireNotNull(preparation).createImportantSession(com.studysmart.app.feature.preparation.ImportantPreparationRequest(
                        request.scope, request.id, requireNotNull(request.questionCount)))
                    if (request.generation != generation) {
                        if (result is com.studysmart.app.feature.preparation.PreparationCreationResult.Created) preparation.discardSession(result.sessionId)
                        return@launch
                    }
                    when (result) {
                        is com.studysmart.app.feature.preparation.PreparationCreationResult.Created -> events.emit(ImportantEffect.OpenPreparation(result.sessionId))
                        com.studysmart.app.feature.preparation.PreparationCreationResult.NoQuestions -> state.value = state.value.copy(noQuestions = true)
                        com.studysmart.app.feature.preparation.PreparationCreationResult.MissingScope -> { refreshAfterLaunch = true }
                    }
                } else {
                    val scope = original.scope
                    val result = requireNotNull(tests).createImportantAttempt(com.studysmart.app.feature.test.ImportantTestAttemptRequest(
                        scope.courseId, scope.academicPeriodId, creationRequestId = original.id, subjectId = scope.subjectId, chapterId = scope.chapterId))
                    if (original.generation != generation) {
                        if (result is com.studysmart.app.feature.test.TestAttemptCreationResult.Created) tests.discardActiveAttempt(result.attemptId)
                        return@launch
                    }
                    when (result) {
                        is com.studysmart.app.feature.test.TestAttemptCreationResult.Created -> events.emit(ImportantEffect.OpenTest(result.attemptId))
                        is com.studysmart.app.feature.test.TestAttemptCreationResult.NoQuestions -> state.value = state.value.copy(noQuestions = true, noTestQuestions = true)
                        else -> { refreshAfterLaunch = true }
                    }
                }
                pendingLaunch = null
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (original.generation == generation) state.value = state.value.copy(hasLaunchError = true)
            } finally {
                launching = false
                state.value = state.value.copy(isStarting = false)
                if (refreshAfterLaunch) { refreshAfterLaunch = false; refresh() }
            }
        }
    }


    private fun submitRemoval() {
        val request = pendingRemoval ?: return
        if (request.generation != generation) { pendingRemoval = null; return }
        val row = request.row
        writing = true
        state.value = state.value.copy(removingId = row.questionId, hasRemoveError = false)
        viewModelScope.launch(dispatchers.main) {
            try {
                check(bookmarks.setBookmarked(row.questionId, false))
                if (request.generation != generation) return@launch
                pendingRemoval = null
                state.value = state.value.copy(removingId = null, rows = state.value.rows.filterNot { it.questionId == row.questionId },
                    removedRows = state.value.removedRows.filterNot { it.questionId == row.questionId } + row)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (request.generation == generation) state.value = state.value.copy(removingId = null, hasRemoveError = true)
            } finally {
                writing = false
                state.value = state.value.copy(removingId = null)
            }
        }
    }
}
