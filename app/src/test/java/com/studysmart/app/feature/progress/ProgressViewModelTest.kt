package com.studysmart.app.feature.progress

import com.studysmart.app.core.common.*
import com.studysmart.app.core.data.SelectedAcademicContext
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.model.*
import com.studysmart.app.feature.preparation.*
import com.studysmart.app.feature.readiness.*
import com.studysmart.app.feature.review.*
import com.studysmart.app.testing.TestDispatcherProvider
import com.studysmart.app.testing.TestPreferencesRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    @Test fun dailyGoalUpdatesWithoutChangingActualActivityOrSessionDefaults() = runTest {
        val fixture = Fixture()
        fixture.progress.value = ProgressSnapshot(ProgressMetrics(2, 1, 1, 20, true), null)
        val vm = fixture.viewModel(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        fixture.preferences.updateDailyCardGoal(7)
        runCurrent()
        assertEquals(7, vm.uiState.value.dailyGoal)
        assertEquals(20, vm.uiState.value.snapshot!!.metrics.dailyRatedCount)
        assertEquals(10, fixture.preferences.preferences.first().preparationDefaults.questionCount)
    }
    @Test fun testOnlyHistoryIsActivityAndForegroundRefreshesTime() = runTest {
        val fixture = Fixture()
        fixture.progress.value = ProgressSnapshot(ProgressMetrics(2, 0, 0, 0, true), null)
        val vm = fixture.viewModel(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        assertTrue(vm.uiState.value.snapshot!!.metrics.hasActivity)
        assertEquals(0, vm.uiState.value.snapshot!!.metrics.studiedCount)
        vm.onForeground()
        assertEquals(1, fixture.refreshes)
    }

    @Test fun missingAndStaleContextAreSafeAndRepairSelection() = runTest {
        val fixture = Fixture()
        val vm = fixture.viewModel(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        fixture.courses.value = emptyList()
        runCurrent()
        assertNull(vm.uiState.value.context)
        assertNull(fixture.preferences.preferences.first().selectedContext)
        assertFalse(vm.uiState.value.hasError)
    }

    @Test fun dueCreationUsesPreferencesAndRetriesTheSameRequest() = runTest {
        val fixture = Fixture()
        fixture.preferences.updatePreparationDefaults(PreparationDefaults(4))
        fixture.preparation.fail = true
        val vm = fixture.viewModel(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        val events = mutableListOf<String>()
        backgroundScope.launch { vm.sessions.collect { events += it } }
        runCurrent()
        assertTrue(vm.uiState.value.canStartDue)
        vm.startDue()
        vm.startDue()
        runCurrent()
        assertTrue(vm.uiState.value.hasActionError)
        assertEquals(1, fixture.preparation.requests.size)
        fixture.preferences.updatePreparationDefaults(PreparationDefaults(7))
        runCurrent()
        fixture.preparation.fail = false
        vm.retryDue()
        runCurrent()
        assertEquals(fixture.preparation.requests.first(), fixture.preparation.requests.last())
        assertEquals(4, fixture.preparation.requests.last().questionCount)
        assertEquals(listOf("session"), events)
    }

    @Test fun noDueNeverStartsSession() = runTest {
        val fixture = Fixture()
        fixture.due.value = DueReviewSummary(0, 0)
        val vm = fixture.viewModel(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        vm.startDue()
        assertTrue(fixture.preparation.requests.isEmpty())
    }

    @Test fun completedCreationIsRetainedUntilNavigationCollectorAcknowledgesIt() = runTest {
        val fixture = Fixture()
        val vm = fixture.viewModel(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        vm.startDue()
        runCurrent()
        // The screen can be behind Settings when creation completes.
        assertEquals("session", vm.sessions.first())
        vm.acknowledgeSession("session")
        val events = mutableListOf<String>()
        backgroundScope.launch { vm.sessions.collect { events += it } }
        runCurrent()
        assertTrue(events.isEmpty())
    }

    @Test fun contextChangeDiscardsStaleCreatedSession() = runTest {
        val fixture = Fixture()
        fixture.preparation.release = CompletableDeferred()
        val vm = fixture.viewModel(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        vm.startDue()
        runCurrent()
        fixture.preferences.clearSelectedContext()
        runCurrent()
        fixture.preparation.release!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("session"), fixture.preparation.discarded)
        assertNull(vm.uiState.value.context)
    }

    @Test fun repositoryFailureCanBeRetriedWithoutInventingMetrics() = runTest {
        val fixture = Fixture().apply { fail = true }
        val vm = fixture.viewModel(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        assertTrue(vm.uiState.value.hasError)
        assertNull(vm.uiState.value.snapshot)
        fixture.fail = false
        vm.retry()
        runCurrent()
        assertFalse(vm.uiState.value.hasError)
        assertNotNull(vm.uiState.value.snapshot)
    }
}

private class Fixture {
    val course = CourseBrowseModel("course", "C", "Course", "C", AcademicPeriodType.YEAR, 1)
    val period = AcademicPeriodBrowseModel("period", "course", 1, AcademicPeriodType.YEAR, "Year 1", "Y1", 0)
    val courses = MutableStateFlow(listOf(course))
    val preferences = TestPreferencesRepository(StudyPreferences(SelectedAcademicContext("course", "period")))
    val progress = MutableStateFlow(ProgressSnapshot(ProgressMetrics(2, 1, 1, 1, true), null))
    val due = MutableStateFlow(DueReviewSummary(1, 1))
    val preparation = DuePreparation()
    var refreshes = 0
    var fail = false
    fun viewModel(dispatchers: DispatcherProvider) = ProgressViewModel(preferences, object : CurriculumRepository {
        override fun observeCourses() = courses
        override fun observeCoursePeriods(courseId: String) = flowOf(CoursePeriodsBrowseModel(course, listOf(period)))
        override fun observePeriodSubjects(academicPeriodId: String) = flowOf(PeriodSubjectsBrowseModel(course, period, emptyList()))
        override fun observeSubjectChapters(subjectId: String) = flowOf<SubjectChaptersBrowseModel?>(null)
        override suspend fun getEligibleQuestionsForChapter(chapterId: String) = emptyList<EligibleQuestionModel>()
        override suspend fun getEligibleQuestionsForTopic(topicId: String) = emptyList<EligibleQuestionModel>()
        override suspend fun isValidPeriod(courseId: String, academicPeriodId: String) = true
    }, object : ProgressRepository {
        override fun observeProgress(courseId: String, periodId: String): Flow<ProgressSnapshot> = flow { check(!fail); progress.collect { emit(it) } }
    }, object : ReadinessRepository {
        override fun observeReadiness(scope: ReadinessScope) = flowOf<ReadinessEstimate?>(null)
    }, object : ReviewRepository {
        override fun observeDueReview(courseId: String, academicPeriodId: String) = due
    }, preparation, object : StudyTimeSource {
        override fun observeTime() = flowOf(StudyTime(Instant.EPOCH, ZoneId.of("UTC")))
        override fun refresh() { refreshes++ }
    }, object : IdGenerator { override fun nextId(prefix: String) = "request" }, dispatchers)
}

private class DuePreparation : PreparationRepository {
    val requests = mutableListOf<DueReviewSessionRequest>()
    val discarded = mutableListOf<String>()
    var fail = false
    var release: CompletableDeferred<Unit>? = null
    override suspend fun getChapterCoverage(chapterId: String): PreparationChapterCoverage? = null
    override fun observeSession(sessionId: String) = flowOf<PreparationSessionModel?>(null)
    override suspend fun createSession(request: PreparationSessionRequest) = PreparationCreationResult.MissingScope
    override suspend fun createDueReviewSession(request: DueReviewSessionRequest): PreparationCreationResult {
        requests += request
        release?.await()
        check(!fail)
        return PreparationCreationResult.Created("session")
    }
    override suspend fun revealCurrent(sessionId: String, questionId: String) = false
    override suspend fun rateCurrent(request: PreparationRatingRequest): PreparationRatingResult = error("unused")
    override suspend fun discardSession(sessionId: String): Boolean { discarded += sessionId; return true }
}
