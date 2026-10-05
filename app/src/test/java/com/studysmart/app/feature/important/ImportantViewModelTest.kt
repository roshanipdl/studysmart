package com.studysmart.app.feature.important

import androidx.lifecycle.SavedStateHandle
import com.studysmart.app.core.data.BookmarkRepository
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.SelectedAcademicContext
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.database.SavedQuestionRow
import com.studysmart.app.core.model.*
import com.studysmart.app.testing.TestDispatcherProvider
import com.studysmart.app.testing.TestPreferencesRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportantViewModelTest {
    @Test fun savedLaunchCapturesFiltersCountAndRetryRequestIdentity() = runTest {
        val preferences = preferences()
        preferences.updatePreparationDefaults(PreparationDefaults(5))
        val preparation = SavedPreparation().apply { fail = true }
        val events = mutableListOf<ImportantEffect>()
        val vm = ImportantViewModel(preferences, Curriculum(), Bookmarks(), TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
            preparation = preparation, ids = object : com.studysmart.app.core.common.IdGenerator {
                override fun nextId(prefix: String) = "request"
            })
        backgroundScope.launch { vm.effects.collect { events += it } }
        runCurrent()
        vm.selectSubject("subject")
        runCurrent()
        vm.selectChapter("chapter")
        runCurrent()
        vm.prepareSaved()
        vm.prepareSaved()
        runCurrent()
        assertTrue(vm.uiState.value.hasLaunchError)
        assertEquals(1, preparation.requests.size)
        assertEquals(SavedQuestionScope("course", "period", "subject", "chapter"), preparation.requests.single().scope)
        preferences.updatePreparationDefaults(PreparationDefaults(7))
        preparation.fail = false
        vm.retryLaunch()
        runCurrent()
        assertEquals(preparation.requests.first(), preparation.requests.last())
        assertEquals(5, preparation.requests.last().questionCount)
        assertEquals(listOf(ImportantEffect.OpenPreparation("saved-session")), events)
    }

    @Test fun navigationDuringCreationDiscardsCreatedTransientSessionInsteadOfOpeningIt() = runTest {
        val preparation = SavedPreparation().apply { release = CompletableDeferred() }
        val events = mutableListOf<ImportantEffect>()
        val vm = ImportantViewModel(preferences(), Curriculum(), Bookmarks(), TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
            preparation = preparation, ids = object : com.studysmart.app.core.common.IdGenerator {
                override fun nextId(prefix: String) = "request"
            })
        backgroundScope.launch { vm.effects.collect { events += it } }
        runCurrent()
        vm.prepareSaved()
        runCurrent()
        vm.leaveScreen()
        preparation.release!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("saved-session"), preparation.discarded)
        assertTrue(events.isEmpty())
    }
    @Test fun noContextIsNotAnError() = runTest {
        val vm = ImportantViewModel(TestPreferencesRepository(), Curriculum(), Bookmarks(), TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.context)
        assertFalse(vm.uiState.value.hasError)
    }

    @Test fun restoredFiltersAreUsedAndInvalidChildIsCleared() = runTest {
        val saved = SavedStateHandle(mapOf("important_context" to "course/period", "important_subject" to "subject", "important_chapter" to "chapter"))
        val curriculum = Curriculum()
        val vm = ImportantViewModel(preferences(), curriculum, Bookmarks(), TestDispatcherProvider(StandardTestDispatcher(testScheduler)), saved)
        runCurrent()
        assertEquals(SavedQuestionScope("course", "period", "subject", "chapter"), vm.uiState.value.scope)
        curriculum.chapters.value = curriculum.chapters.value!!.copy(chapters = emptyList())
        runCurrent()
        assertNull(vm.uiState.value.scope!!.chapterId)
        assertNull(saved.get<String>("important_chapter"))
        curriculum.context.value = context.copy(subjects = emptyList())
        runCurrent()
        assertNull(vm.uiState.value.scope!!.subjectId)
        assertNull(saved.get<String>("important_subject"))
    }

    @Test fun subjectChangeAtomicallyClearsChapterAndOldRows() = runTest {
        val bookmarks = Bookmarks()
        val vm = ImportantViewModel(preferences(), Curriculum(), bookmarks, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        vm.selectSubject("subject")
        runCurrent()
        vm.selectChapter("chapter")
        runCurrent()
        vm.selectSubject(null)
        assertTrue(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.rows.isEmpty())
        runCurrent()
        assertEquals(SavedQuestionScope("course", "period"), vm.uiState.value.scope)
    }

    @Test fun removalKeepsOrderedDisabledRowUntilRefresh() = runTest {
        val vm = ImportantViewModel(preferences(), Curriculum(), Bookmarks(), TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        vm.remove("first")
        runCurrent()
        assertEquals(listOf("first"), vm.uiState.value.removedRows.map { it.questionId })
        assertEquals(listOf("first", "second"), vm.uiState.value.displayedRows.map { it.questionId })
        assertEquals(1, vm.uiState.value.rows.size)
        vm.refresh()
        runCurrent()
        assertTrue(vm.uiState.value.removedRows.isEmpty())
        assertEquals(listOf("second"), vm.uiState.value.displayedRows.map { it.questionId })
    }

    @Test fun failedRemovalRetriesSameIntentAndChangingFiltersDropsRetry() = runTest {
        val bookmarks = Bookmarks().apply { failWrite = true }
        val vm = ImportantViewModel(preferences(), Curriculum(), bookmarks, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        vm.remove("first")
        runCurrent()
        assertTrue(vm.uiState.value.hasRemoveError)
        assertTrue(vm.uiState.value.removedRows.isEmpty())
        vm.retryRemoval()
        runCurrent()
        assertEquals(listOf("first", "first"), bookmarks.writes)
        vm.selectSubject("subject")
        runCurrent()
        bookmarks.failWrite = false
        vm.retryRemoval()
        runCurrent()
        assertEquals(2, bookmarks.writes.size)
        assertFalse(vm.uiState.value.hasRemoveError)
    }

    @Test fun contextSwitchIgnoresInFlightRemovalCompletion() = runTest {
        val preferences = preferences()
        val bookmarks = Bookmarks().apply { release = CompletableDeferred() }
        val vm = ImportantViewModel(preferences, Curriculum(), bookmarks, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        vm.remove("first")
        vm.remove("first")
        runCurrent()
        assertEquals(1, bookmarks.writes.size)
        preferences.clearSelectedContext()
        runCurrent()
        bookmarks.release!!.complete(Unit)
        runCurrent()
        assertNull(vm.uiState.value.context)
        assertTrue(vm.uiState.value.removedRows.isEmpty())
    }

    @Test fun repositoryFailureCanBeRefreshed() = runTest {
        val bookmarks = Bookmarks().apply { failRead = true }
        val vm = ImportantViewModel(preferences(), Curriculum(), bookmarks, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        assertTrue(vm.uiState.value.hasError)
        bookmarks.failRead = false
        vm.refresh()
        runCurrent()
        assertFalse(vm.uiState.value.hasError)
        assertEquals(2, vm.uiState.value.rows.size)
    }

    @Test fun navigationClearsRemovedRowsWithoutLosingFiltersAndRebookmarkedRowsReturn() = runTest {
        val bookmarks = Bookmarks()
        val vm = ImportantViewModel(preferences(), Curriculum(), bookmarks, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        vm.selectSubject("subject")
        runCurrent()
        vm.remove("first")
        runCurrent()
        vm.leaveScreen()
        bookmarks.rows.value = bookmarks.rows.value + row("first", 2)
        vm.refresh()
        runCurrent()
        assertEquals("subject", vm.uiState.value.scope!!.subjectId)
        assertTrue(vm.uiState.value.removedRows.isEmpty())
        assertEquals(setOf("first", "second"), vm.uiState.value.rows.map { it.questionId }.toSet())
    }

    @Test fun missingCourseRepairsPreferencesWithoutTouchingBookmarks() = runTest {
        val preferences = preferences()
        val curriculum = Curriculum().apply { courses.value = emptyList() }
        val bookmarks = Bookmarks()
        val vm = ImportantViewModel(preferences, curriculum, bookmarks, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        assertNull(vm.uiState.value.context)
        assertTrue(bookmarks.writes.isEmpty())
        assertNull(preferences.preferences.first().selectedCourseId)
    }

    private fun preferences() = TestPreferencesRepository(StudyPreferences(SelectedAcademicContext("course", "period")))
}

private val course = CourseBrowseModel("course", "C", "Course", "C", AcademicPeriodType.SEMESTER, 1)
private val period = AcademicPeriodBrowseModel("period", "course", 1, AcademicPeriodType.SEMESTER, "Semester 1", "S1", 1)
private val subject = SubjectBrowseModel("subject", "period", "SUB", "Subject", 1)
private val chapter = ChapterBrowseModel("chapter", "subject", 1, "Chapter", 2)
private val context = PeriodSubjectsBrowseModel(course, period, listOf(subject))

private class Curriculum : CurriculumRepository {
    val courses = MutableStateFlow(listOf(course))
    val context = MutableStateFlow<PeriodSubjectsBrowseModel?>(com.studysmart.app.feature.important.context)
    val chapters = MutableStateFlow<SubjectChaptersBrowseModel?>(SubjectChaptersBrowseModel(course, period, subject, listOf(chapter)))
    override fun observeCourses() = courses
    override fun observeCoursePeriods(courseId: String) = flowOf(CoursePeriodsBrowseModel(course, listOf(period)))
    override fun observePeriodSubjects(academicPeriodId: String) = context
    override fun observeSubjectChapters(subjectId: String) = chapters
    override suspend fun getEligibleQuestionsForChapter(chapterId: String) = emptyList<EligibleQuestionModel>()
    override suspend fun getEligibleQuestionsForTopic(topicId: String) = emptyList<EligibleQuestionModel>()
    override suspend fun isValidPeriod(courseId: String, academicPeriodId: String) = courseId == "course" && academicPeriodId == "period"
}

private class Bookmarks : BookmarkRepository {
    val rows = MutableStateFlow(listOf(row("first", 0), row("second", 1)))
    val writes = mutableListOf<String>()
    var failWrite = false
    var failRead = false
    var release: CompletableDeferred<Unit>? = null
    override fun observeBookmarkedIds() = rows.map { rows -> rows.map { it.questionId }.toSet() }
    override fun observeSavedQuestions(scope: SavedQuestionScope): Flow<List<SavedQuestionRow>> = flow {
        check(!failRead)
        rows.collect { rows -> emit(rows.filter { (scope.subjectId == null || it.subjectId == scope.subjectId) &&
            (scope.chapterId == null || it.chapterId == scope.chapterId) }) }
    }
    override suspend fun setBookmarked(questionId: String, bookmarked: Boolean): Boolean {
        check(!bookmarked)
        writes += questionId
        release?.await()
        check(!failWrite)
        rows.value = rows.value.filterNot { it.questionId == questionId }
        return true
    }
}

private fun row(id: String, seconds: Long) = SavedQuestionRow(id, "Question $id", "course", "C", "period", "Semester 1",
    "subject", "SUB", "Subject", "chapter", "Chapter", null, Instant.EPOCH.plusSeconds(seconds))

private class SavedPreparation : com.studysmart.app.feature.preparation.PreparationRepository {
    var fail = false
    var release: CompletableDeferred<Unit>? = null
    val requests = mutableListOf<com.studysmart.app.feature.preparation.ImportantPreparationRequest>()
    val discarded = mutableListOf<String>()
    override suspend fun getChapterCoverage(chapterId: String): com.studysmart.app.feature.preparation.PreparationChapterCoverage? = null
    override fun observeSession(sessionId: String) = flowOf<com.studysmart.app.feature.preparation.PreparationSessionModel?>(null)
    override suspend fun createSession(request: com.studysmart.app.feature.preparation.PreparationSessionRequest) = com.studysmart.app.feature.preparation.PreparationCreationResult.MissingScope
    override suspend fun createDueReviewSession(request: com.studysmart.app.feature.preparation.DueReviewSessionRequest) = com.studysmart.app.feature.preparation.PreparationCreationResult.MissingScope
    override suspend fun createImportantSession(request: com.studysmart.app.feature.preparation.ImportantPreparationRequest): com.studysmart.app.feature.preparation.PreparationCreationResult {
        requests += request
        release?.await()
        check(!fail)
        return com.studysmart.app.feature.preparation.PreparationCreationResult.Created("saved-session")
    }
    override suspend fun revealCurrent(sessionId: String, questionId: String) = false
    override suspend fun rateCurrent(request: com.studysmart.app.feature.preparation.PreparationRatingRequest): com.studysmart.app.feature.preparation.PreparationRatingResult = error("unused")
    override suspend fun discardSession(sessionId: String): Boolean { discarded += sessionId; return true }
}
