package com.studysmart.app.feature.review

import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.SelectedAcademicContext
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.model.AcademicPeriodType
import com.studysmart.app.core.model.CourseBrowseModel
import com.studysmart.app.core.model.CoursePeriodsBrowseModel
import com.studysmart.app.core.model.EligibleQuestionModel
import com.studysmart.app.core.model.PeriodSubjectsBrowseModel
import com.studysmart.app.core.model.SubjectChaptersBrowseModel
import com.studysmart.app.feature.preparation.DueReviewSessionRequest
import com.studysmart.app.feature.preparation.PreparationCreationResult
import com.studysmart.app.feature.preparation.PreparationRatingRequest
import com.studysmart.app.feature.preparation.PreparationRatingResult
import com.studysmart.app.feature.preparation.PreparationRepository
import com.studysmart.app.feature.preparation.PreparationSessionModel
import com.studysmart.app.feature.preparation.PreparationSessionRequest
import com.studysmart.app.feature.readiness.ReadinessEstimate
import com.studysmart.app.feature.readiness.ReadinessRepository
import com.studysmart.app.feature.readiness.ReadinessScope
import com.studysmart.app.testing.TestDispatcherProvider
import com.studysmart.app.testing.TestPreferencesRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewViewModelTest {
    private val clock = Clock.fixed(
        Instant.parse("2026-01-01T12:00:00Z"),
        ZoneOffset.UTC,
    )
    private val courses = listOf(
        CourseBrowseModel(
            id = "course-1",
            code = "TEST",
            name = "Test Course",
            shortName = "Test",
            structureType = AcademicPeriodType.SEMESTER,
            periodCount = 1,
        ),
    )

    @Test
    fun statusSuccessDoesNotClearDueFailure() = runTest {
        val preferences = TestPreferencesRepository(StudyPreferences(SelectedAcademicContext("course-1", "period-1")))
        val rows = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.studysmart.app.core.database.ReadinessQuestionRow>())
        val review = object : ReviewRepository {
            override fun observeStatusQuestions(courseId: String, academicPeriodId: String) = rows
            override fun observeDueReview(courseId: String, academicPeriodId: String) = kotlinx.coroutines.flow.flow<DueReviewSummary> {
                throw IllegalStateException("Due unavailable")
            }
        }
        val model = createViewModel(preferences, review = review)
        val row = com.studysmart.app.core.database.ReadinessQuestionRow("question", null, null, null, false, null, null)
        rows.value = listOf(row)
        assertEquals(listOf(row), model.uiState.value.statusQuestions)
        assertNull(model.uiState.value.statusError)
        assertEquals("Due unavailable", model.uiState.value.dueError)
        assertEquals("Due unavailable", model.uiState.value.displayError)
    }

    @Test
    fun statusFailureRemainsRetryableAfterDueSuccess() = runTest {
        val preferences = TestPreferencesRepository(StudyPreferences(SelectedAcademicContext("course-1", "period-1")))
        var subscriptions = 0
        val row = com.studysmart.app.core.database.ReadinessQuestionRow("question", null, null, null, false, null, null)
        val review = object : ReviewRepository {
            override fun observeStatusQuestions(courseId: String, academicPeriodId: String) = kotlinx.coroutines.flow.flow {
                subscriptions++
                if (subscriptions == 1) throw IllegalStateException("Status unavailable")
                emit(listOf(row))
            }
            override fun observeDueReview(courseId: String, academicPeriodId: String) = flowOf(DueReviewSummary(2, 1))
        }
        val model = createViewModel(preferences, review = review)
        assertEquals("Status unavailable", model.uiState.value.displayError)
        assertEquals("Status unavailable", model.uiState.value.statusError)
        assertEquals("course-1", model.uiState.value.courseId)
        assertEquals(2, model.uiState.value.dueCount)
        model.retry()
        assertEquals(2, subscriptions)
        assertNull(model.uiState.value.displayError)
        assertEquals(listOf(row), model.uiState.value.statusQuestions)
    }

    @Test
    fun statusCreationFinishingAfterContextChangeIsDiscardedAndUnlocksStart() = runTest {
        val preferences = TestPreferencesRepository(StudyPreferences(SelectedAcademicContext("course-1", "period-1")))
        val preparation = FakePreparationRepository()
        val completion = kotlinx.coroutines.CompletableDeferred<PreparationCreationResult>()
        preparation.statusCompletion = completion
        val viewModel = createViewModel(preferences, preparation, questions = listOf(
            com.studysmart.app.core.database.ReadinessQuestionRow("question", null, null, null, false, null, null)))
        viewModel.startStatusReview(ReviewFilter.NOT_STUDIED)
        preferences.setSelectedContext("course-1", "period-2")
        completion.complete(PreparationCreationResult.Created("stale-session"))
        assertEquals(listOf("stale-session"), preparation.discardedSessions)
        assertEquals("period-2", viewModel.uiState.value.academicPeriodId)
        assertFalse(viewModel.uiState.value.isStarting)
    }

    @Test
    fun statusStartCapturesSavedCountAndPreventsDuplicateNavigationStarts() = runTest {
        val preferences = TestPreferencesRepository(StudyPreferences(SelectedAcademicContext("course-1", "period-1")))
        preferences.updatePreparationDefaults(com.studysmart.app.core.model.PreparationDefaults(7))
        val preparation = FakePreparationRepository()
        val viewModel = createViewModel(preferences, preparation, questions = listOf(
            com.studysmart.app.core.database.ReadinessQuestionRow("question", null, null, null, false, null, null)))
        viewModel.startStatusReview(ReviewFilter.NOT_STUDIED)
        viewModel.startStatusReview(ReviewFilter.NOT_STUDIED)
        assertEquals(1, preparation.statusRequests.size)
        assertEquals(7, preparation.statusRequests.single().questionCount)
        assertEquals("course-1", preparation.statusRequests.single().courseId)
    }

    @Test
    fun nextSessionUsesNewConfirmedCountWithoutChangingEarlierRequest() = runTest {
        val preferences = TestPreferencesRepository(StudyPreferences(SelectedAcademicContext("course-1", "period-1")))
        val preparation = FakePreparationRepository()
        val viewModel = createViewModel(preferences, preparation, DueReviewSummary(40, 40))
        viewModel.startDueReview()
        preferences.updatePreparationDefaults(com.studysmart.app.core.model.PreparationDefaults(15))
        viewModel.startDueReview()
        assertEquals(listOf(10, 15), preparation.dueRequests.map { it.questionCount })
        assertEquals(40, viewModel.uiState.value.dueCount)
    }

    @Test
    fun retryAfterContextChangeDoesNotReplayTheOldContextRequest() = runTest {
        val preferences = TestPreferencesRepository(StudyPreferences(SelectedAcademicContext("course-1", "period-1")))
        val preparation = FakePreparationRepository(failFirstDueCreation = true)
        val viewModel = createViewModel(preferences, preparation, DueReviewSummary(40, 40))
        viewModel.startDueReview()
        preferences.setSelectedContext("course-1", "period-2")
        preferences.updatePreparationDefaults(com.studysmart.app.core.model.PreparationDefaults(15))
        viewModel.retry()
        assertEquals(listOf("period-1", "period-2"), preparation.dueRequests.map { it.academicPeriodId })
        assertTrue(preparation.dueRequests[0].creationRequestId != preparation.dueRequests[1].creationRequestId)
        assertEquals(15, preparation.dueRequests[1].questionCount)
    }

    @Test
    fun missingContextShowsContextRequired() = runTest {
        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isContextRequired)
        assertEquals(0, viewModel.uiState.value.dueCount)
    }

    @Test
    fun validContextShowsDueAndOverdueCounts() = runTest {
        val viewModel = createViewModel(
            preferences = TestPreferencesRepository(
                StudyPreferences(SelectedAcademicContext("course-1", "period-1")),
            ),
            summary = DueReviewSummary(dueCount = 4, overdueCount = 2),
        )

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isContextRequired)
        assertEquals(4, viewModel.uiState.value.dueCount)
        assertEquals(2, viewModel.uiState.value.overdueCount)
        assertFalse(viewModel.uiState.value.isNoDue)
    }

    @Test
    fun validContextAlsoExposesReadinessEstimate() = runTest {
        val viewModel = createViewModel(
            preferences = TestPreferencesRepository(
                StudyPreferences(SelectedAcademicContext("course-1", "period-1")),
            ),
            summary = DueReviewSummary(dueCount = 2, overdueCount = 1),
        )

        assertEquals(72.5, viewModel.uiState.value.readiness?.score)
        assertEquals(73, viewModel.uiState.value.readiness?.displayPercent)
        assertFalse(viewModel.uiState.value.isReadinessLoading)
    }

    @Test
    fun validEmptyPoolDoesNotExposeRetryableError() = runTest {
        val viewModel = createViewModel(
            preferences = TestPreferencesRepository(
                StudyPreferences(SelectedAcademicContext("course-1", "period-1")),
            ),
            summary = DueReviewSummary(dueCount = 0, overdueCount = 0),
        )

        assertTrue(viewModel.uiState.value.isNoDue)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun stalePeriodClearsOnlyPeriod() = runTest {
        val preferences = TestPreferencesRepository(
            StudyPreferences(SelectedAcademicContext("course-1", "stale-period")),
        )
        val viewModel = createViewModel(preferences = preferences)

        assertTrue(viewModel.uiState.value.isContextRequired)
        assertEquals("course-1", preferences.preferences.first().selectedCourseId)
        assertNull(preferences.preferences.first().selectedAcademicPeriodId)
    }

    @Test
    fun startCreatesSessionAndEmitsNavigationEffect() = runTest {
        val preferences = TestPreferencesRepository(
            StudyPreferences(SelectedAcademicContext("course-1", "period-1")),
        )
        val preparation = FakePreparationRepository()
        val viewModel = createViewModel(
            preferences = preferences,
            preparation = preparation,
            summary = DueReviewSummary(dueCount = 2, overdueCount = 1),
        )
        val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }

        viewModel.startDueReview()

        assertEquals("session-1", (effect.await() as ReviewEffect.OpenDueReview).sessionId)
        assertEquals(1, preparation.dueRequests.size)
        assertEquals("course-1", preparation.dueRequests.single().courseId)
        assertEquals("period-1", preparation.dueRequests.single().academicPeriodId)
        assertEquals(10, preparation.dueRequests.single().questionCount)
    }

    @Test
    fun failedStartCanRetryWithTheSameCreationRequest() = runTest {
        val preferences = TestPreferencesRepository(
            StudyPreferences(SelectedAcademicContext("course-1", "period-1")),
        )
        val preparation = FakePreparationRepository(failFirstDueCreation = true)
        val viewModel = createViewModel(
            preferences = preferences,
            preparation = preparation,
            summary = DueReviewSummary(dueCount = 1, overdueCount = 1),
        )

        viewModel.startDueReview()
        assertTrue(viewModel.uiState.value.error != null)

        preferences.updatePreparationDefaults(com.studysmart.app.core.model.PreparationDefaults(1))

        viewModel.retry()

        assertNull(viewModel.uiState.value.error)
        assertEquals(2, preparation.dueRequests.size)
        assertEquals(10, preparation.dueRequests[1].questionCount)
        assertEquals(1, viewModel.uiState.value.sessionQuestionCount)
        assertEquals(
            preparation.dueRequests[0].creationRequestId,
            preparation.dueRequests[1].creationRequestId,
        )
    }

    private fun createViewModel(
        preferences: TestPreferencesRepository = TestPreferencesRepository(),
        preparation: FakePreparationRepository = FakePreparationRepository(),
        summary: DueReviewSummary = DueReviewSummary(0, 0),
        readiness: ReadinessRepository = FakeReadinessRepository(),
        questions: List<com.studysmart.app.core.database.ReadinessQuestionRow> = emptyList(),
        review: ReviewRepository? = null,
    ): ReviewViewModel = ReviewViewModel(
        preferencesRepository = preferences,
        curriculumRepository = FakeCurriculumRepository(courses),
        reviewRepository = review ?: FakeReviewRepository(summary, questions),
        readinessRepository = readiness,
        preparationRepository = preparation,
        clock = clock,
        idGenerator = FixedIdGenerator(),
        dispatchers = TestDispatcherProvider(),
    )
}

private class FakeReadinessRepository : ReadinessRepository {
    override fun observeReadiness(scope: ReadinessScope): Flow<ReadinessEstimate?> = flowOf(
        ReadinessEstimate(
            score = 72.5,
            masteryScore = 70.0,
            recentTestScore = 80.0,
            coverageScore = 60.0,
            freshnessScore = 90.0,
            activeQuestionCount = 10,
            ratedQuestionCount = 6,
            recentAttemptCount = 2,
        ),
    )
}

private class FakeReviewRepository(
    private val summary: DueReviewSummary,
    private val questions: List<com.studysmart.app.core.database.ReadinessQuestionRow>,
) : ReviewRepository {
    override fun observeStatusQuestions(courseId: String, academicPeriodId: String) = flowOf(questions)
    override fun observeDueReview(
        courseId: String,
        academicPeriodId: String,
    ): Flow<DueReviewSummary> = flowOf(summary)
}

private class FakePreparationRepository(
    private val failFirstDueCreation: Boolean = false,
) : PreparationRepository {
    override suspend fun getChapterCoverage(chapterId: String): com.studysmart.app.feature.preparation.PreparationChapterCoverage? = null
    val dueRequests = mutableListOf<DueReviewSessionRequest>()
    val statusRequests = mutableListOf<com.studysmart.app.feature.preparation.StatusFilterSessionRequest>()
    var statusCompletion: kotlinx.coroutines.CompletableDeferred<PreparationCreationResult>? = null
    val discardedSessions = mutableListOf<String>()
    override suspend fun createStatusFilterSession(request: com.studysmart.app.feature.preparation.StatusFilterSessionRequest): PreparationCreationResult {
        statusRequests += request
        return statusCompletion?.await() ?: PreparationCreationResult.Created("status-session")
    }

    override fun observeSession(sessionId: String): Flow<PreparationSessionModel?> = emptyFlow()

    override suspend fun createSession(request: PreparationSessionRequest): PreparationCreationResult =
        PreparationCreationResult.NoQuestions

    override suspend fun createDueReviewSession(request: DueReviewSessionRequest): PreparationCreationResult {
        dueRequests += request
        if (failFirstDueCreation && dueRequests.size == 1) {
            throw IllegalStateException("temporary failure")
        }
        return PreparationCreationResult.Created("session-1")
    }

    override suspend fun revealCurrent(sessionId: String, questionId: String): Boolean = false

    override suspend fun rateCurrent(request: PreparationRatingRequest): PreparationRatingResult =
        PreparationRatingResult(request.sessionId, completed = false)

    override suspend fun discardSession(sessionId: String): Boolean {
        discardedSessions += sessionId
        return true
    }
}

private class FakeCurriculumRepository(
    private val courses: List<CourseBrowseModel>,
) : CurriculumRepository {
    override fun observeCourses(): Flow<List<CourseBrowseModel>> = flowOf(courses)

    override fun observeCoursePeriods(courseId: String): Flow<CoursePeriodsBrowseModel?> = flowOf(null)

    override fun observePeriodSubjects(academicPeriodId: String): Flow<PeriodSubjectsBrowseModel?> = flowOf(
        courses.firstOrNull()?.let { course -> PeriodSubjectsBrowseModel(course,
            com.studysmart.app.core.model.AcademicPeriodBrowseModel(academicPeriodId, course.id, 1,
                AcademicPeriodType.SEMESTER, "Semester 1", "Sem 1", 0), emptyList()) })

    override fun observeSubjectChapters(subjectId: String): Flow<SubjectChaptersBrowseModel?> = flowOf(null)

    override suspend fun getEligibleQuestionsForChapter(chapterId: String): List<EligibleQuestionModel> = emptyList()

    override suspend fun getEligibleQuestionsForTopic(topicId: String): List<EligibleQuestionModel> = emptyList()

    override suspend fun isValidPeriod(courseId: String, academicPeriodId: String): Boolean =
        courseId == "course-1" && academicPeriodId in listOf("period-1", "period-2")
}

private class FixedIdGenerator : IdGenerator {
    private var next = 0

    override fun nextId(prefix: String): String = "$prefix-${next++}"
}
