package com.studysmart.app.feature.test

import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.database.TestAnswerEntity
import com.studysmart.app.core.database.TestAttemptEntity
import com.studysmart.app.core.database.TestAttemptQuestionEntity
import com.studysmart.app.core.model.KeywordEvidenceCodec
import com.studysmart.app.core.model.KeywordNormalizer
import com.studysmart.app.core.model.KeywordSnapshotCodec
import com.studysmart.app.core.model.KeywordSnapshotEntry
import com.studysmart.app.core.model.QuestionType
import com.studysmart.app.core.model.TestAttemptOriginType
import com.studysmart.app.core.model.TestAttemptScopeType
import com.studysmart.app.core.model.TestAttemptStatus
import com.studysmart.app.core.model.TestQuestionSource
import com.studysmart.app.testing.TestDispatcherProvider
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestResultViewModelTest {
    @Test fun bookmarkUsesCurriculumIdAndDoesNotAlterFrozenEvidence() = runTest {
        val bookmarks = com.studysmart.app.testing.TestBookmarkRepository()
        val viewModel = TestResultViewModel("source-attempt", FakeResultRepository(), ResultTestIdGenerator(),
            TestDispatcherProvider(), bookmarks)
        val report = viewModel.uiState.value.report
        viewModel.toggleBookmark("attempt-question-1")
        viewModel.toggleBookmark("unknown")
        assertTrue(bookmarks.writes.isEmpty())
        viewModel.toggleBookmark("question-1")
        assertEquals(listOf("question-1" to true), bookmarks.writes)
        assertEquals(report, viewModel.uiState.value.report)
        viewModel.retry()
        assertEquals(setOf("question-1"), viewModel.uiState.value.bookmarks!!.savedIds)
        assertEquals(report, viewModel.uiState.value.report)
    }

    @Test fun resultLoadFailureAndRetryKeepBookmarkState() = runTest {
        val backing = FakeResultRepository()
        var failLoad = false
        val repository = object : TestAttemptRepository by backing {
            override fun observeAttempt(attemptId: String): Flow<TestAttemptModel?> = kotlinx.coroutines.flow.flow {
                if (failLoad) error("injected load failure") else emit(resultModel())
            }
        }
        val bookmarks = com.studysmart.app.testing.TestBookmarkRepository()
        val viewModel = TestResultViewModel("source-attempt", repository, ResultTestIdGenerator(), TestDispatcherProvider(), bookmarks)
        viewModel.toggleBookmark("question-1")
        failLoad = true
        viewModel.retry()
        assertTrue(viewModel.uiState.value.error != null)
        assertEquals(setOf("question-1"), viewModel.uiState.value.bookmarks!!.savedIds)
        failLoad = false
        viewModel.retry()
        assertEquals(setOf("question-1"), viewModel.uiState.value.bookmarks!!.savedIds)
        assertEquals("Historical question", viewModel.uiState.value.report!!.questions.single().snapshot.questionTextSnapshot)
    }

    @Test
    fun loadsFrozenReportAndExpandsOnlyKnownQuestions() = runTest {
        val viewModel = viewModel(FakeResultRepository())

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Historical question", viewModel.uiState.value.report?.questions?.single()?.snapshot?.questionTextSnapshot)
        assertEquals(1, viewModel.uiState.value.actionContext?.activeReviewQuestionCount)

        viewModel.toggleQuestion("unknown")
        assertNull(viewModel.uiState.value.expandedQuestionId)
        viewModel.toggleQuestion("attempt-question-1")
        assertEquals("attempt-question-1", viewModel.uiState.value.expandedQuestionId)
        viewModel.toggleQuestion("attempt-question-1")
        assertNull(viewModel.uiState.value.expandedQuestionId)
    }

    @Test
    fun newTestUsesTheSourceAndEmitsOpenAttempt() = runTest {
        val repository = FakeResultRepository()
        val viewModel = viewModel(repository)
        val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }

        viewModel.startNewTest()

        assertEquals(TestResultEffect.OpenAttempt("new-attempt"), effect.await())
        assertEquals("source-attempt", repository.newRequests.single().sourceAttemptId)
        assertEquals("test-follow-up-request-0", repository.newRequests.single().creationRequestId)
    }

    @Test
    fun emptyRetestWeakIsDisabledAndCreatesNothing() = runTest {
        val repository = FakeResultRepository(actionContext = TestResultActionContext(
            TestResultOrigin(TestAttemptOriginType.CHAPTER, "subject-1"),
            activeReviewQuestionCount = 0,
        ))
        val viewModel = viewModel(repository)

        assertFalse(viewModel.uiState.value.isRetestWeakEnabled)
        viewModel.startRetestWeak()

        assertTrue(repository.retestRequests.isEmpty())
    }

    @Test
    fun backWaitsUntilSavedOriginIsResolved() = runTest {
        val origin = CompletableDeferred<TestResultOrigin?>()
        val expectedOrigin = TestResultOrigin(TestAttemptOriginType.CHAPTER, "subject-1")
        val viewModel = viewModel(FakeResultRepository(originResult = origin))

        assertTrue(viewModel.uiState.value.isLoadingOrigin)
        assertFalse(viewModel.uiState.value.canNavigateBack)

        origin.complete(expectedOrigin)

        assertEquals(expectedOrigin, viewModel.uiState.value.origin)
        assertFalse(viewModel.uiState.value.isLoadingOrigin)
        assertTrue(viewModel.uiState.value.canNavigateBack)
    }

    @Test
    fun originLookupFailureRequiresSuccessfulRetryBeforeBackIsEnabled() = runTest {
        val viewModel = viewModel(FakeResultRepository(originFailureCount = 1))

        assertTrue(viewModel.uiState.value.canRetryOrigin)
        assertFalse(viewModel.uiState.value.canNavigateBack)

        viewModel.retryOrigin()

        assertFalse(viewModel.uiState.value.canRetryOrigin)
        assertTrue(viewModel.uiState.value.canNavigateBack)
        assertEquals(TestResultOrigin(TestAttemptOriginType.CHAPTER, "subject-1"), viewModel.uiState.value.origin)
    }

    @Test
    fun retryAfterCreationFailureReusesTheIdempotencyRequest() = runTest {
        val repository = FakeResultRepository(failFirstRetest = true)
        val viewModel = viewModel(repository)
        val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }

        viewModel.startRetestWeak()

        assertTrue(viewModel.uiState.value.canRetryFollowUp)
        assertFalse(viewModel.uiState.value.areFollowUpsEnabled)
        viewModel.retryFollowUp()

        assertEquals(2, repository.retestRequests.size)
        assertEquals(
            repository.retestRequests[0].creationRequestId,
            repository.retestRequests[1].creationRequestId,
        )
        assertEquals(TestResultEffect.OpenAttempt("retest-attempt"), effect.await())
    }

    private fun viewModel(repository: FakeResultRepository) = TestResultViewModel(
        attemptId = "source-attempt",
        repository = repository,
        idGenerator = ResultTestIdGenerator(),
        dispatchers = TestDispatcherProvider(),
    )
}

private class FakeResultRepository(
    private val actionContext: TestResultActionContext = TestResultActionContext(
        TestResultOrigin(TestAttemptOriginType.CHAPTER, "subject-1"),
        activeReviewQuestionCount = 1,
    ),
    private val failFirstRetest: Boolean = false,
    private val originResult: CompletableDeferred<TestResultOrigin?>? = null,
    private var originFailureCount: Int = 0,
) : TestAttemptRepository {
    val newRequests = mutableListOf<ResultFollowUpAttemptRequest>()
    val retestRequests = mutableListOf<ResultFollowUpAttemptRequest>()

    override fun observeAttempt(attemptId: String): Flow<TestAttemptModel?> = flowOf(resultModel())

    override suspend fun startAttempt(attemptId: String): TestAttemptStartResult = TestAttemptStartResult.Missing

    override suspend fun saveAnswerDraft(
        attemptId: String,
        draft: TestAnswerDraft,
    ): TestAnswerSaveResult = TestAnswerSaveResult.MissingAttempt

    override suspend fun submitAttempt(
        attemptId: String,
        action: TestSubmitAction,
        finalDraft: TestAnswerDraft?,
    ): TestSubmissionResult = TestSubmissionResult.Missing

    override suspend fun createChapterAttempt(request: ChapterTestAttemptRequest): TestAttemptCreationResult =
        TestAttemptCreationResult.NoQuestions(TestQuestionSource.ALL)

    override suspend fun createImportantAttempt(request: ImportantTestAttemptRequest): TestAttemptCreationResult =
        TestAttemptCreationResult.NoQuestions(TestQuestionSource.ALL)

    override suspend fun createNewAttemptFromResult(request: ResultFollowUpAttemptRequest): TestAttemptCreationResult {
        newRequests += request
        return TestAttemptCreationResult.Created("new-attempt")
    }

    override suspend fun createRetestWeakAttempt(request: ResultFollowUpAttemptRequest): TestAttemptCreationResult {
        retestRequests += request
        if (failFirstRetest && retestRequests.size == 1) error("temporary failure")
        return TestAttemptCreationResult.Created("retest-attempt")
    }

    override suspend fun loadResultActionContext(attemptId: String): TestResultActionContext = actionContext

    override suspend fun loadAttemptOrigin(attemptId: String): TestResultOrigin? {
        if (originFailureCount > 0) {
            originFailureCount--
            error("temporary origin lookup failure")
        }
        return if (originResult != null) originResult.await() else actionContext.origin
    }

    override suspend fun discardActiveAttempt(attemptId: String): Boolean = false
}

private class ResultTestIdGenerator : IdGenerator {
    private var next = 0
    override fun nextId(prefix: String): String = "$prefix-${next++}"
}

private fun resultModel(): TestAttemptModel {
    val at = Instant.parse("2026-09-30T12:00:00Z")
    val question = TestAttemptQuestionEntity(
        id = "attempt-question-1",
        testAttemptId = "source-attempt",
        questionId = "question-1",
        displayOrder = 0,
        questionTextSnapshot = "Historical question",
        referenceAnswerSnapshot = "Frozen reference answer",
        questionTypeSnapshot = QuestionType.WRITTEN,
        maxScoreSnapshot = 2.0,
        keywordSnapshotJson = KeywordSnapshotCodec.encode(
            listOf(KeywordSnapshotEntry(KeywordNormalizer.normalize("alpha"), 1.0)),
        ),
        keywordSnapshotVersion = KeywordSnapshotCodec.CURRENT_VERSION,
    )
    val draft = TestAnswerEntity(
        id = "answer-1",
        testAttemptQuestionId = question.id,
        answerText = "",
        selectedOption = null,
        isFlagged = false,
        timeSpentSeconds = 0,
        keywordMatchPercent = null,
        normalizedAnswer = null,
        matchedKeywordsJson = null,
        missingKeywordsJson = null,
        score = null,
        maxScore = null,
        requiresReview = null,
        updatedAt = at,
    )
    val evaluation = KeywordAnswerEvaluator().evaluate(listOf(question), listOf(draft))
    val evaluated = evaluation.answers.single()
    val answer = draft.copy(
        normalizedAnswer = evaluated.normalizedAnswer,
        matchedKeywordsJson = KeywordEvidenceCodec.encode(evaluated.matchedKeywords),
        missingKeywordsJson = KeywordEvidenceCodec.encode(evaluated.missingKeywords),
        keywordMatchPercent = evaluated.keywordMatchPercent,
        score = evaluated.score,
        maxScore = evaluated.maxScore,
        requiresReview = evaluated.requiresReview,
    )
    val attempt = TestAttemptEntity(
        id = "source-attempt",
        userId = "local-user",
        courseId = "course-1",
        academicPeriodId = "period-1",
        subjectId = "subject-1",
        chapterId = "chapter-1",
        topicId = null,
        scopeType = TestAttemptScopeType.CHAPTER,
        scopeReferenceId = "chapter-1",
        originType = TestAttemptOriginType.CHAPTER,
        originReferenceId = "chapter-1",
        creationRequestId = "creation-1",
        requestedQuestionCount = 1,
        actualQuestionCount = 1,
        questionSource = TestQuestionSource.ALL,
        selectionSeed = null,
        isTimed = false,
        timeLimitSeconds = null,
        startedAt = at.minusSeconds(5),
        submittedAt = at,
        completedAt = at,
        timeTakenSeconds = 5,
        score = evaluation.totalScore,
        maxScore = evaluation.maxScore,
        percentage = evaluation.percentage,
        status = TestAttemptStatus.SUBMITTED,
        completionReason = "submitted",
        evaluatorVersion = KeywordAnswerEvaluator().version,
    )
    return TestAttemptModel(attempt, listOf(question), listOf(answer))
}
