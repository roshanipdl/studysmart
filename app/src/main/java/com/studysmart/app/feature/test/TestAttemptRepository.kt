package com.studysmart.app.feature.test

import androidx.room.withTransaction
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.AndroidMonotonicClock
import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.common.MonotonicClock
import com.studysmart.app.core.common.RandomSource
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.database.QuestionKeywordEntity
import com.studysmart.app.core.database.TestAttemptContextRow
import com.studysmart.app.core.database.TestAttemptDao
import com.studysmart.app.core.database.TestAttemptEntity
import com.studysmart.app.core.database.TestAttemptQuestionEntity
import com.studysmart.app.core.database.TestAttemptWithQuestionRows
import com.studysmart.app.core.database.TestAnswerEntity
import com.studysmart.app.core.database.TestCandidateRow
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.model.KeywordNormalizer
import com.studysmart.app.core.model.KeywordEvidenceCodec
import com.studysmart.app.core.model.KeywordSnapshotCodec
import com.studysmart.app.core.model.KeywordSnapshotEntry
import com.studysmart.app.core.model.TestAttemptOriginType
import com.studysmart.app.core.model.TestAttemptScopeType
import com.studysmart.app.core.model.TestAttemptStatus
import com.studysmart.app.core.model.QuestionType
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.TestQuestionSource
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

sealed interface TestAttemptCreationResult {
    data class Created(val attemptId: String) : TestAttemptCreationResult
    data class NoQuestions(val questionSource: TestQuestionSource) : TestAttemptCreationResult
    data object MissingScope : TestAttemptCreationResult
    data object MissingSource : TestAttemptCreationResult
    data object CorruptSource : TestAttemptCreationResult
}

data class ChapterTestAttemptRequest(
    val chapterId: String,
    val originReferenceId: String? = chapterId,
    val creationRequestId: String,
)

data class ImportantTestAttemptRequest(
    val courseId: String,
    val academicPeriodId: String,
    val originReferenceId: String? = null,
    val creationRequestId: String,
    val subjectId: String? = null,
    val chapterId: String? = null,
)

data class ResultFollowUpAttemptRequest(
    val sourceAttemptId: String,
    val creationRequestId: String,
)

data class TestAttemptModel(
    val attempt: TestAttemptEntity,
    val questions: List<TestAttemptQuestionEntity>,
    val answers: List<TestAnswerEntity>,
)

data class TestResultOrigin(
    val originType: TestAttemptOriginType,
    val subjectId: String?,
)

data class TestResultActionContext(
    val origin: TestResultOrigin,
    val activeReviewQuestionCount: Int,
)

sealed interface TestAttemptStartResult {
    data object Started : TestAttemptStartResult
    data object AlreadyInProgress : TestAttemptStartResult
    data object AlreadySubmitted : TestAttemptStartResult
    data object Missing : TestAttemptStartResult
    data object Corrupt : TestAttemptStartResult
}

data class TestAnswerDraft(
    val attemptQuestionId: String,
    val answerText: String,
    val isFlagged: Boolean,
    val accumulatedTimeMillis: Long,
)

enum class TestSubmitAction {
    MANUAL,
    EXPIRED_CONFIRMATION,
}

sealed interface TestSubmissionResult {
    data object Submitted : TestSubmissionResult
    data object AlreadySubmitted : TestSubmissionResult
    data object Expired : TestSubmissionResult
    data object NotExpired : TestSubmissionResult
    data object InProgress : TestSubmissionResult
    data object NotActive : TestSubmissionResult
    data object Missing : TestSubmissionResult
    data object Corrupt : TestSubmissionResult
}

sealed interface TestAnswerSaveResult {
    data object Saved : TestAnswerSaveResult
    data object AlreadySubmitted : TestAnswerSaveResult
    data object NotActive : TestAnswerSaveResult
    data object Expired : TestAnswerSaveResult
    data object MissingAttempt : TestAnswerSaveResult
    data object MissingQuestion : TestAnswerSaveResult
    data object Corrupt : TestAnswerSaveResult
}

interface TestAttemptRepository {
    fun observeAttempt(attemptId: String): Flow<TestAttemptModel?>

    suspend fun startAttempt(attemptId: String): TestAttemptStartResult

    suspend fun saveAnswerDraft(attemptId: String, draft: TestAnswerDraft): TestAnswerSaveResult

    suspend fun submitAttempt(
        attemptId: String,
        action: TestSubmitAction,
        finalDraft: TestAnswerDraft?,
    ): TestSubmissionResult

    suspend fun createChapterAttempt(request: ChapterTestAttemptRequest): TestAttemptCreationResult

    suspend fun createImportantAttempt(request: ImportantTestAttemptRequest): TestAttemptCreationResult

    suspend fun createNewAttemptFromResult(request: ResultFollowUpAttemptRequest): TestAttemptCreationResult

    suspend fun createRetestWeakAttempt(request: ResultFollowUpAttemptRequest): TestAttemptCreationResult

    suspend fun loadResultActionContext(attemptId: String): TestResultActionContext?

    suspend fun loadAttemptOrigin(attemptId: String): TestResultOrigin?

    suspend fun discardActiveAttempt(attemptId: String): Boolean
}

internal fun TestAttemptModel.integrityIssue(): String? {
    if (attempt.actualQuestionCount !in 1..TestDefaults.MAX_QUESTION_COUNT ||
        questions.size != attempt.actualQuestionCount
    ) {
        return "attempt question count does not match its snapshots"
    }
    if (questions.map { it.id }.distinct().size != questions.size ||
        questions.map { it.displayOrder } != questions.indices.toList()
    ) {
        return "attempt question snapshots are not uniquely ordered"
    }
    if (answers.size != questions.size ||
        answers.map { it.testAttemptQuestionId }.distinct().size != answers.size ||
        answers.any { answer -> questions.none { it.id == answer.testAttemptQuestionId } }
    ) {
        return "attempt answer rows do not match its question snapshots"
    }
    return runCatching {
        questions.forEach { question ->
            require(question.questionTypeSnapshot == QuestionType.WRITTEN)
            require(question.maxScoreSnapshot.isFinite() && question.maxScoreSnapshot > 0.0)
            require(question.keywordSnapshotVersion == KeywordSnapshotCodec.CURRENT_VERSION)
            KeywordSnapshotCodec.decode(question.keywordSnapshotJson)
        }
    }.exceptionOrNull()?.message
}

class RoomTestAttemptRepository(
    private val database: StudySmartDatabase,
    private val preferencesRepository: PreferencesRepository,
    private val clock: Clock,
    private val randomSource: RandomSource,
    private val idGenerator: IdGenerator,
    private val dispatchers: DispatcherProvider,
    private val monotonicClock: MonotonicClock = AndroidMonotonicClock,
    private val answerEvaluator: AnswerEvaluator = KeywordAnswerEvaluator(),
    private val selectionEngine: TestQuestionSelectionEngine = TestQuestionSelectionEngine(),
    private val userId: String = LOCAL_USER_ID,
) : TestAttemptRepository {
    private val dao: TestAttemptDao = database.testAttemptDao()

    override fun observeAttempt(attemptId: String): Flow<TestAttemptModel?> =
        dao.observeAttemptWithQuestions(attemptId).map { it?.toModel() }

    override suspend fun startAttempt(attemptId: String): TestAttemptStartResult =
        withContext(dispatchers.io) {
            require(attemptId.isNotBlank()) { "attemptId must not be blank" }
            database.withTransaction {
                val bundle = dao.getAttemptWithQuestions(attemptId)
                    ?: return@withTransaction TestAttemptStartResult.Missing
                val model = bundle.toModel()
                if (model.integrityIssue() != null || !model.attempt.hasValidTimingDefaults()) {
                    return@withTransaction TestAttemptStartResult.Corrupt
                }
                when (model.attempt.status) {
                    TestAttemptStatus.CREATED -> {
                        val now = clock.instant()
                        val elapsed = monotonicClock.elapsedRealtimeMillis()
                        val bootId = monotonicClock.bootId()
                        if (elapsed < 0L || (model.attempt.isTimed && bootId.isNullOrBlank())) {
                            return@withTransaction TestAttemptStartResult.Corrupt
                        }
                        val deadline = model.attempt.timeLimitSeconds?.let(now::plusSeconds)
                        val changed = dao.startAttempt(attemptId, now, elapsed, bootId, deadline)
                        if (changed == 1) TestAttemptStartResult.Started
                        else attemptStatusResult(dao.getAttempt(attemptId)?.status)
                    }
                    TestAttemptStatus.IN_PROGRESS -> TestAttemptStartResult.AlreadyInProgress
                    TestAttemptStatus.SUBMITTING,
                    TestAttemptStatus.SUBMITTED,
                    -> TestAttemptStartResult.AlreadySubmitted
                }
            }
        }

    override suspend fun saveAnswerDraft(
        attemptId: String,
        draft: TestAnswerDraft,
    ): TestAnswerSaveResult = withContext(dispatchers.io) {
        require(attemptId.isNotBlank()) { "attemptId must not be blank" }
        require(draft.attemptQuestionId.isNotBlank()) { "attemptQuestionId must not be blank" }
        require(draft.accumulatedTimeMillis >= 0L) { "accumulatedTimeMillis must not be negative" }
        database.withTransaction {
            val attempt = dao.getAttempt(attemptId)
                ?: return@withTransaction TestAnswerSaveResult.MissingAttempt
            when (attempt.status) {
                TestAttemptStatus.SUBMITTED,
                TestAttemptStatus.SUBMITTING,
                -> return@withTransaction TestAnswerSaveResult.AlreadySubmitted
                TestAttemptStatus.CREATED -> return@withTransaction TestAnswerSaveResult.NotActive
                TestAttemptStatus.IN_PROGRESS -> Unit
            }
            val timer = TestAttemptTimer.read(
                attempt = attempt,
                now = clock.instant(),
                elapsedRealtimeMillis = monotonicClock.elapsedRealtimeMillis(),
                currentBootId = monotonicClock.bootId(),
            )
            if (!timer.isValid) return@withTransaction TestAnswerSaveResult.Corrupt
            if (timer.isExpired) {
                return@withTransaction TestAnswerSaveResult.Expired
            }
            val accumulatedMillis = maxOf(
                dao.getAnswerForAttemptQuestion(attemptId, draft.attemptQuestionId)
                    ?.accumulatedTimeMillis ?: return@withTransaction TestAnswerSaveResult.MissingQuestion,
                draft.accumulatedTimeMillis,
            ).let { total ->
                attempt.timeLimitSeconds?.let { limit ->
                    minOf(total, limit * MILLIS_PER_SECOND)
                } ?: total
            }
            val updated = dao.updateAnswerDraft(
                attemptId = attemptId,
                attemptQuestionId = draft.attemptQuestionId,
                answerText = draft.answerText,
                isFlagged = draft.isFlagged,
                accumulatedTimeMillis = accumulatedMillis,
                timeSpentSeconds = accumulatedMillis / MILLIS_PER_SECOND,
                updatedAt = clock.instant(),
            )
            if (updated == 1) TestAnswerSaveResult.Saved
            else {
                when (dao.getAttempt(attemptId)?.status) {
                    TestAttemptStatus.SUBMITTED, TestAttemptStatus.SUBMITTING ->
                        TestAnswerSaveResult.AlreadySubmitted
                    else -> TestAnswerSaveResult.NotActive
                }
            }
        }
    }

    override suspend fun submitAttempt(
        attemptId: String,
        action: TestSubmitAction,
        finalDraft: TestAnswerDraft?,
    ): TestSubmissionResult = withContext(dispatchers.io) {
        require(attemptId.isNotBlank()) { "attemptId must not be blank" }
        finalDraft?.let {
            require(it.attemptQuestionId.isNotBlank()) { "attemptQuestionId must not be blank" }
            require(it.accumulatedTimeMillis >= 0L) { "accumulatedTimeMillis must not be negative" }
        }
        database.withTransaction {
            val initial = dao.getAttemptWithQuestions(attemptId)
                ?: return@withTransaction TestSubmissionResult.Missing
            val initialModel = initial.toModel()
            if (initialModel.integrityIssue() != null || !initialModel.attempt.hasValidTimingDefaults()) {
                return@withTransaction TestSubmissionResult.Corrupt
            }
            when (initialModel.attempt.status) {
                TestAttemptStatus.SUBMITTED -> return@withTransaction TestSubmissionResult.AlreadySubmitted
                TestAttemptStatus.SUBMITTING -> return@withTransaction TestSubmissionResult.InProgress
                TestAttemptStatus.CREATED -> return@withTransaction TestSubmissionResult.NotActive
                TestAttemptStatus.IN_PROGRESS -> Unit
            }
            val initialTimer = TestAttemptTimer.read(
                initialModel.attempt,
                clock.instant(),
                monotonicClock.elapsedRealtimeMillis(),
                monotonicClock.bootId(),
            )
            if (!initialTimer.isValid && !(initialModel.attempt.isTimed && initialTimer.isExpired)) {
                return@withTransaction TestSubmissionResult.Corrupt
            }

            if (finalDraft != null) {
                val row = dao.getAnswerForAttemptQuestion(attemptId, finalDraft.attemptQuestionId)
                    ?: return@withTransaction TestSubmissionResult.Corrupt
                val elapsedForQuestion = maxOf(row.accumulatedTimeMillis, finalDraft.accumulatedTimeMillis)
                    .let { total ->
                        initialModel.attempt.timeLimitSeconds?.let { limit ->
                            minOf(total, limit * MILLIS_PER_SECOND)
                        } ?: total
                    }
                val finalWrite = dao.updateAnswerDraft(
                    attemptId = attemptId,
                    attemptQuestionId = finalDraft.attemptQuestionId,
                    answerText = finalDraft.answerText,
                    isFlagged = finalDraft.isFlagged,
                    accumulatedTimeMillis = elapsedForQuestion,
                    timeSpentSeconds = elapsedForQuestion / MILLIS_PER_SECOND,
                    updatedAt = clock.instant(),
                )
                if (finalWrite != 1) return@withTransaction TestSubmissionResult.Corrupt
            }

            // Recheck at the compare-and-set boundary. Expiry never scores without an explicit action.
            val claimAt = clock.instant()
            val claimElapsed = monotonicClock.elapsedRealtimeMillis()
            val timerAtClaim = TestAttemptTimer.read(
                initialModel.attempt,
                claimAt,
                claimElapsed,
                monotonicClock.bootId(),
            )
            if (!timerAtClaim.isValid && !(initialModel.attempt.isTimed && timerAtClaim.isExpired)) {
                return@withTransaction TestSubmissionResult.Corrupt
            }
            val expiredAtClaim = timerAtClaim.isExpired ||
                (!timerAtClaim.isValid && initialModel.attempt.isTimed)
            when (action) {
                TestSubmitAction.MANUAL -> if (expiredAtClaim) {
                    return@withTransaction TestSubmissionResult.Expired
                }
                TestSubmitAction.EXPIRED_CONFIRMATION -> if (!expiredAtClaim) {
                    return@withTransaction TestSubmissionResult.NotExpired
                }
            }
            val claimed = dao.claimAttemptForSubmission(attemptId)
            if (claimed != 1) {
                return@withTransaction when (dao.getAttempt(attemptId)?.status) {
                    TestAttemptStatus.SUBMITTED -> TestSubmissionResult.AlreadySubmitted
                    TestAttemptStatus.SUBMITTING -> TestSubmissionResult.InProgress
                    else -> TestSubmissionResult.NotActive
                }
            }
            val claimedBundle = dao.getAttemptWithQuestions(attemptId)
                ?: error("claimed attempt disappeared before evaluation")
            val claimedModel = claimedBundle.toModel()
            check(claimedModel.integrityIssue() == null) { "attempt became corrupt before evaluation" }
            val evaluation = answerEvaluator.evaluate(claimedModel.questions, claimedModel.answers)
            val evaluatedById = evaluation.answers.associateBy { it.attemptQuestionId }
            check(evaluatedById.size == claimedModel.questions.size) { "evaluator omitted an answer" }
            claimedModel.questions.forEach { question ->
                val evaluated = evaluatedById[question.id]
                    ?: error("evaluator omitted attempt question ${question.id}")
                val updated = dao.updateAnswerEvaluation(
                    attemptId = attemptId,
                    attemptQuestionId = question.id,
                    normalizedAnswer = evaluated.normalizedAnswer,
                    matchedKeywordsJson = KeywordEvidenceCodec.encode(evaluated.matchedKeywords),
                    missingKeywordsJson = KeywordEvidenceCodec.encode(evaluated.missingKeywords),
                    keywordMatchPercent = evaluated.keywordMatchPercent,
                    score = evaluated.score,
                    maxScore = evaluated.maxScore,
                    requiresReview = evaluated.requiresReview,
                    updatedAt = claimAt,
                )
                check(updated == 1) { "answer evidence could not be persisted" }
            }
            val attempt = claimedModel.attempt
            val elapsedMillis = timerAtClaim.elapsedMillis ?: if (expiredAtClaim && attempt.isTimed) {
                attempt.timeLimitSeconds!! * MILLIS_PER_SECOND
            } else {
                error("attempt elapsed time is unavailable")
            }
            val elapsedSeconds = if (attempt.isTimed) {
                minOf(elapsedMillis / MILLIS_PER_SECOND, attempt.timeLimitSeconds!!)
            } else {
                elapsedMillis / MILLIS_PER_SECOND
            }
            val completed = dao.completeSubmission(
                attemptId = attemptId,
                submittedAt = claimAt,
                timeTakenSeconds = elapsedSeconds,
                score = evaluation.totalScore,
                maxScore = evaluation.maxScore,
                percentage = evaluation.percentage,
                completionReason = if (expiredAtClaim) COMPLETION_TIME_EXPIRED else COMPLETION_SUBMITTED,
                evaluatorVersion = answerEvaluator.version,
            )
            check(completed == 1) { "attempt submission could not be committed" }
            TestSubmissionResult.Submitted
        }
    }

    override suspend fun createChapterAttempt(
        request: ChapterTestAttemptRequest,
    ): TestAttemptCreationResult = withContext(dispatchers.io) {
        require(request.chapterId.isNotBlank()) { "chapterId must not be blank" }
        require(request.creationRequestId.isNotBlank()) { "creationRequestId must not be blank" }
        createAttempt(
            creationRequestId = request.creationRequestId,
            contextLoader = { dao.getChapterContext(request.chapterId) },
            candidatesLoader = { dao.getChapterCandidates(userId, request.chapterId) },
            scopeType = TestAttemptScopeType.CHAPTER,
            scopeReferenceId = request.chapterId,
            originType = TestAttemptOriginType.CHAPTER,
            originReferenceId = request.originReferenceId,
        )
    }

    override suspend fun createImportantAttempt(
        request: ImportantTestAttemptRequest,
    ): TestAttemptCreationResult = withContext(dispatchers.io) {
        require(request.courseId.isNotBlank()) { "courseId must not be blank" }
        require(request.academicPeriodId.isNotBlank()) { "academicPeriodId must not be blank" }
        require(request.creationRequestId.isNotBlank()) { "creationRequestId must not be blank" }
        createAttempt(
            creationRequestId = request.creationRequestId,
            contextLoader = { importantContext(com.studysmart.app.core.model.SavedQuestionScope(request.courseId,
                request.academicPeriodId, request.subjectId, request.chapterId)) },
            candidatesLoader = {
                dao.getImportantCandidates(userId, request.courseId, request.academicPeriodId, request.subjectId, request.chapterId)
            },
            scopeType = TestAttemptScopeType.IMPORTANT,
            scopeReferenceId = request.academicPeriodId,
            originType = TestAttemptOriginType.IMPORTANT,
            originReferenceId = request.originReferenceId,
        )
    }

    override suspend fun createNewAttemptFromResult(
        request: ResultFollowUpAttemptRequest,
    ): TestAttemptCreationResult = withContext(dispatchers.io) {
        validateFollowUpRequest(request)
        createFollowUpAttempt(request, retestWeak = false)
    }

    override suspend fun createRetestWeakAttempt(
        request: ResultFollowUpAttemptRequest,
    ): TestAttemptCreationResult = withContext(dispatchers.io) {
        validateFollowUpRequest(request)
        createFollowUpAttempt(request, retestWeak = true)
    }

    override suspend fun loadResultActionContext(attemptId: String): TestResultActionContext? =
        withContext(dispatchers.io) {
            require(attemptId.isNotBlank()) { "attemptId must not be blank" }
            database.withTransaction {
                val sourceBundle = dao.getAttemptWithQuestions(attemptId) ?: return@withTransaction null
                val source = sourceBundle.toModel()
                if (source.attempt.userId != userId || source.attempt.status != TestAttemptStatus.SUBMITTED) {
                    return@withTransaction null
                }
                val report = when (val projection = TestResultProjector.project(source)) {
                    is TestResultProjection.Available -> projection.report
                    TestResultProjection.NotSubmitted,
                    is TestResultProjection.Corrupt,
                    -> return@withTransaction null
                }
                val baseAttempt = resolveBaseAttempt(source.attempt) ?: return@withTransaction null
                val context = loadBaseContext(baseAttempt) ?: return@withTransaction null
                val origin = baseAttempt.validatedOrigin() ?: return@withTransaction null
                val reviewQuestionIds = report.questions
                    .filter { it.answer.requiresReview == true }
                    .map { it.snapshot.questionId }
                val activeReviewCount = if (reviewQuestionIds.isEmpty()) {
                    0
                } else {
                    dao.getActiveCandidatesForQuestionIds(
                        userId = userId,
                        courseId = context.courseId,
                        academicPeriodId = context.academicPeriodId,
                        chapterId = baseAttempt.chapterId,
                        subjectId = baseAttempt.subjectId,
                        questionIds = reviewQuestionIds,
                    ).size
                }
                TestResultActionContext(
                    origin = origin,
                    activeReviewQuestionCount = activeReviewCount,
                )
            }
        }

    override suspend fun loadAttemptOrigin(attemptId: String): TestResultOrigin? =
        withContext(dispatchers.io) {
            require(attemptId.isNotBlank()) { "attemptId must not be blank" }
            database.withTransaction {
                val attempt = dao.getAttempt(attemptId) ?: return@withTransaction null
                val baseAttempt = resolveNavigationBaseAttempt(attempt) ?: return@withTransaction null
                if (loadBaseContext(baseAttempt) == null) return@withTransaction null
                baseAttempt.validatedOrigin()
            }
        }

    private suspend fun createAttempt(
        creationRequestId: String,
        contextLoader: suspend () -> TestAttemptContextRow?,
        candidatesLoader: suspend () -> List<TestCandidateRow>,
        scopeType: TestAttemptScopeType,
        scopeReferenceId: String?,
        originType: TestAttemptOriginType,
        originReferenceId: String?,
    ): TestAttemptCreationResult {
        val defaults = preferencesRepository.preferences.first().testDefaults
        return database.withTransaction {
            dao.getAttemptByCreationRequestId(creationRequestId)?.let {
                val requestedContext = contextLoader() ?: return@withTransaction TestAttemptCreationResult.MissingScope
                if (it.userId != userId || it.scopeType != scopeType || it.scopeReferenceId != scopeReferenceId ||
                    it.courseId != requestedContext.courseId || it.academicPeriodId != requestedContext.academicPeriodId ||
                    it.subjectId != requestedContext.subjectId || it.chapterId != requestedContext.chapterId ||
                    it.topicId != requestedContext.topicId) return@withTransaction TestAttemptCreationResult.MissingScope
                return@withTransaction TestAttemptCreationResult.Created(it.id)
            }
            val context = contextLoader() ?: return@withTransaction TestAttemptCreationResult.MissingScope
            val candidates = candidatesLoader()
            val selectionSeed = if (defaults.questionSource == TestQuestionSource.ALL) {
                null
            } else {
                randomSource.nextLong()
            }
            val plan = selectionEngine.createPlan(
                candidates = candidates.map { it.toCandidate() },
                defaults = defaults,
                selectionSeed = selectionSeed,
            ) ?: return@withTransaction TestAttemptCreationResult.NoQuestions(defaults.questionSource)
            persistAttempt(
                creationRequestId = creationRequestId,
                context = context,
                candidates = candidates,
                plan = plan,
                defaults = defaults,
                scopeType = scopeType,
                scopeReferenceId = scopeReferenceId,
                originType = originType,
                originReferenceId = originReferenceId,
            )
        }
    }

    private suspend fun createFollowUpAttempt(
        request: ResultFollowUpAttemptRequest,
        retestWeak: Boolean,
    ): TestAttemptCreationResult {
        val currentDefaults = preferencesRepository.preferences.first().testDefaults
        return database.withTransaction {
            dao.getAttemptByCreationRequestId(request.creationRequestId)?.let { existing ->
                return@withTransaction if (existing.userId == userId) {
                    TestAttemptCreationResult.Created(existing.id)
                } else {
                    TestAttemptCreationResult.MissingSource
                }
            }

            val sourceBundle = dao.getAttemptWithQuestions(request.sourceAttemptId)
                ?: return@withTransaction TestAttemptCreationResult.MissingSource
            val source = sourceBundle.toModel()
            if (source.attempt.userId != userId || source.attempt.status != TestAttemptStatus.SUBMITTED) {
                return@withTransaction TestAttemptCreationResult.MissingSource
            }
            val sourceReport = when (val projection = TestResultProjector.project(source)) {
                is TestResultProjection.Available -> projection.report
                TestResultProjection.NotSubmitted -> return@withTransaction TestAttemptCreationResult.MissingSource
                is TestResultProjection.Corrupt -> return@withTransaction TestAttemptCreationResult.CorruptSource
            }

            val baseAttempt = resolveBaseAttempt(source.attempt)
                ?: return@withTransaction TestAttemptCreationResult.MissingSource
            val context = loadBaseContext(baseAttempt)
                ?: return@withTransaction TestAttemptCreationResult.MissingScope

            if (retestWeak) {
                val reviewQuestionIds = sourceReport.questions
                    .filter { it.answer.requiresReview == true }
                    .map { it.snapshot.questionId }
                if (reviewQuestionIds.isEmpty()) {
                    return@withTransaction TestAttemptCreationResult.NoQuestions(TestQuestionSource.ALL)
                }
                val candidates = dao.getActiveCandidatesForQuestionIds(
                    userId = userId,
                    courseId = context.courseId,
                    academicPeriodId = context.academicPeriodId,
                    chapterId = baseAttempt.chapterId,
                    subjectId = baseAttempt.subjectId,
                    questionIds = reviewQuestionIds,
                ).associateBy { it.question.id }
                val orderedCandidates = reviewQuestionIds.mapNotNull(candidates::get)
                if (orderedCandidates.isEmpty()) {
                    return@withTransaction TestAttemptCreationResult.NoQuestions(TestQuestionSource.ALL)
                }
                val retestDefaults = currentDefaults.copy(
                    questionCount = orderedCandidates.size,
                    questionSource = TestQuestionSource.ALL,
                )
                val plan = selectionEngine.createPlan(
                    candidates = orderedCandidates.map { it.toCandidate() },
                    defaults = retestDefaults,
                    selectionSeed = null,
                ) ?: return@withTransaction TestAttemptCreationResult.NoQuestions(TestQuestionSource.ALL)
                persistAttempt(
                    creationRequestId = request.creationRequestId,
                    context = context,
                    candidates = orderedCandidates,
                    plan = plan,
                    defaults = retestDefaults,
                    scopeType = TestAttemptScopeType.RETEST_WEAK,
                    scopeReferenceId = source.attempt.id,
                    originType = TestAttemptOriginType.RETEST_WEAK,
                    originReferenceId = source.attempt.id,
                )
            } else {
                val candidates = loadBaseCandidates(baseAttempt)
                val selectionSeed = if (currentDefaults.questionSource == TestQuestionSource.ALL) {
                    null
                } else {
                    randomSource.nextLong()
                }
                val plan = selectionEngine.createPlan(
                    candidates = candidates.map { it.toCandidate() },
                    defaults = currentDefaults,
                    selectionSeed = selectionSeed,
                ) ?: return@withTransaction TestAttemptCreationResult.NoQuestions(currentDefaults.questionSource)
                persistAttempt(
                    creationRequestId = request.creationRequestId,
                    context = context,
                    candidates = candidates,
                    plan = plan,
                    defaults = currentDefaults,
                    scopeType = baseAttempt.scopeType,
                    scopeReferenceId = baseAttempt.scopeReferenceId,
                    originType = TestAttemptOriginType.NEW_TEST,
                    originReferenceId = source.attempt.id,
                )
            }
        }
    }

    private suspend fun resolveBaseAttempt(source: TestAttemptEntity): TestAttemptEntity? {
        val visited = mutableSetOf<String>()
        var current = source
        while (true) {
            if (!visited.add(current.id) || current.userId != userId ||
                current.status != TestAttemptStatus.SUBMITTED
            ) {
                return null
            }
            if (current.originType != TestAttemptOriginType.NEW_TEST &&
                current.originType != TestAttemptOriginType.RETEST_WEAK
            ) {
                return current
            }
            val sourceId = current.originReferenceId?.takeIf(String::isNotBlank) ?: return null
            current = dao.getAttempt(sourceId) ?: return null
        }
    }

    private suspend fun resolveNavigationBaseAttempt(source: TestAttemptEntity): TestAttemptEntity? {
        val visited = mutableSetOf<String>()
        var current = source
        while (true) {
            if (!visited.add(current.id) || current.userId != userId) return null
            if (current.originType != TestAttemptOriginType.NEW_TEST &&
                current.originType != TestAttemptOriginType.RETEST_WEAK
            ) {
                return current
            }
            val sourceId = current.originReferenceId?.takeIf(String::isNotBlank) ?: return null
            val parent = dao.getAttempt(sourceId) ?: return null
            if (parent.userId != userId || parent.status != TestAttemptStatus.SUBMITTED) return null
            current = parent
        }
    }

    private suspend fun loadBaseContext(base: TestAttemptEntity): TestAttemptContextRow? {
        val context = when (base.scopeType) {
            TestAttemptScopeType.CHAPTER -> {
                val chapterId = base.chapterId?.takeIf { base.scopeReferenceId == it } ?: return null
                dao.getChapterContext(chapterId)
            }
            TestAttemptScopeType.IMPORTANT -> {
                val periodId = base.academicPeriodId
                    .takeIf { base.scopeReferenceId == it } ?: return null
                if (base.chapterId != null && base.subjectId == null) return null
                importantContext(com.studysmart.app.core.model.SavedQuestionScope(base.courseId, periodId, base.subjectId, base.chapterId))
            }
            TestAttemptScopeType.TOPIC,
            TestAttemptScopeType.RETEST_WEAK,
            TestAttemptScopeType.STATUS_FILTER,
            -> return null
        } ?: return null
        if (context.courseId != base.courseId || context.academicPeriodId != base.academicPeriodId ||
            context.subjectId != base.subjectId || context.chapterId != base.chapterId ||
            context.topicId != base.topicId
        ) {
            return null
        }
        return context
    }

    private fun TestAttemptEntity.validatedOrigin(): TestResultOrigin? {
        val expectedOriginType = when (scopeType) {
            TestAttemptScopeType.CHAPTER -> TestAttemptOriginType.CHAPTER
            TestAttemptScopeType.IMPORTANT -> TestAttemptOriginType.IMPORTANT
            TestAttemptScopeType.TOPIC,
            TestAttemptScopeType.RETEST_WEAK,
            TestAttemptScopeType.STATUS_FILTER,
            -> return null
        }
        if (originType != expectedOriginType) return null
        return TestResultOrigin(expectedOriginType, subjectId)
    }

    private suspend fun loadBaseCandidates(base: TestAttemptEntity): List<TestCandidateRow> =
        when (base.scopeType) {
            TestAttemptScopeType.CHAPTER -> dao.getChapterCandidates(userId, requireNotNull(base.chapterId))
            TestAttemptScopeType.IMPORTANT -> dao.getImportantCandidates(
                userId,
                base.courseId,
                base.academicPeriodId,
                base.subjectId,
                base.chapterId,
            )
            TestAttemptScopeType.TOPIC,
            TestAttemptScopeType.RETEST_WEAK,
            TestAttemptScopeType.STATUS_FILTER,
            -> emptyList()
        }

    private suspend fun importantContext(scope: com.studysmart.app.core.model.SavedQuestionScope): TestAttemptContextRow? =
        database.preparationDao().getImportantContext(scope.courseId, scope.academicPeriodId, scope.subjectId, scope.chapterId)
            ?.let { TestAttemptContextRow(it.courseId, it.academicPeriodId, it.subjectId, it.chapterId, null) }

    private suspend fun persistAttempt(
        creationRequestId: String,
        context: TestAttemptContextRow,
        candidates: List<TestCandidateRow>,
        plan: TestSelectionPlan,
        defaults: TestDefaults,
        scopeType: TestAttemptScopeType,
        scopeReferenceId: String?,
        originType: TestAttemptOriginType,
        originReferenceId: String?,
    ): TestAttemptCreationResult.Created {
        val candidateById = candidates.associateBy { it.question.id }
        check(plan.orderedQuestionIds.all { it in candidateById }) {
            "test selection references a missing active candidate"
        }
        val now = clock.instant()
        val attemptId = idGenerator.nextId("test-attempt")
        val attempt = TestAttemptEntity(
            id = attemptId,
            userId = userId,
            courseId = context.courseId,
            academicPeriodId = context.academicPeriodId,
            subjectId = context.subjectId,
            chapterId = context.chapterId,
            topicId = context.topicId,
            scopeType = scopeType,
            scopeReferenceId = scopeReferenceId,
            originType = originType,
            originReferenceId = originReferenceId,
            creationRequestId = creationRequestId,
            requestedQuestionCount = defaults.questionCount,
            actualQuestionCount = plan.orderedQuestionIds.size,
            questionSource = defaults.questionSource,
            selectionSeed = plan.selectionSeed,
            isTimed = defaults.timedMode,
            timeLimitSeconds = defaults.timeLimitSeconds,
            startedAt = null,
            submittedAt = null,
            completedAt = null,
            timeTakenSeconds = null,
            score = null,
            maxScore = null,
            percentage = null,
            status = TestAttemptStatus.CREATED,
            completionReason = null,
            evaluatorVersion = null,
        )
        val snapshots = plan.orderedQuestionIds.mapIndexed { displayOrder, questionId ->
            val candidate = candidateById.getValue(questionId)
            val keywords = dao.getQuestionKeywords(questionId).toSnapshotEntries()
            TestAttemptQuestionEntity(
                id = idGenerator.nextId("test-attempt-question"),
                testAttemptId = attemptId,
                questionId = questionId,
                displayOrder = displayOrder,
                questionTextSnapshot = candidate.question.question,
                referenceAnswerSnapshot = candidate.question.answer,
                questionTypeSnapshot = candidate.question.questionType,
                maxScoreSnapshot = candidate.question.maxScore,
                keywordSnapshotJson = KeywordSnapshotCodec.encode(keywords),
                keywordSnapshotVersion = KeywordSnapshotCodec.CURRENT_VERSION,
            )
        }
        val answers = snapshots.map { snapshot ->
            TestAnswerEntity(
                id = idGenerator.nextId("test-answer"),
                testAttemptQuestionId = snapshot.id,
                answerText = "",
                selectedOption = null,
                isFlagged = false,
                timeSpentSeconds = null,
                keywordMatchPercent = null,
                normalizedAnswer = null,
                matchedKeywordsJson = null,
                missingKeywordsJson = null,
                score = null,
                maxScore = null,
                requiresReview = null,
                updatedAt = now,
            )
        }
        dao.insertAttempt(attempt)
        dao.insertAttemptQuestions(snapshots)
        dao.insertAnswers(answers)
        return TestAttemptCreationResult.Created(attemptId)
    }

    private fun validateFollowUpRequest(request: ResultFollowUpAttemptRequest) {
        require(request.sourceAttemptId.isNotBlank()) { "sourceAttemptId must not be blank" }
        require(request.creationRequestId.isNotBlank()) { "creationRequestId must not be blank" }
    }

    override suspend fun discardActiveAttempt(attemptId: String): Boolean = withContext(dispatchers.io) {
        require(attemptId.isNotBlank()) { "attemptId must not be blank" }
        database.withTransaction { dao.deleteActiveAttempt(attemptId) == 1 }
    }

    private fun TestCandidateRow.toCandidate(): TestCandidate = TestCandidate(
        questionId = question.id,
        latestRating = latestRating,
        hasReview = hasReview,
        latestTestPercentage = latestTestPercentage,
        latestTestRequiresReview = latestTestRequiresReview,
        isImportant = isImportant,
    )

    private fun List<QuestionKeywordEntity>.toSnapshotEntries(): List<KeywordSnapshotEntry> = map {
        KeywordSnapshotEntry(
            keyword = KeywordNormalizer.normalize(it.normalizedKeyword),
            weight = it.weight,
        )
    }

    private fun TestAttemptWithQuestionRows.toModel(): TestAttemptModel {
        val rows = questionRows.sortedBy { it.question.displayOrder }
        return TestAttemptModel(
            attempt = attempt,
            questions = rows.map { it.question },
            answers = rows.flatMap { it.answerRows },
        )
    }

    private fun TestAttemptEntity.hasValidTimingDefaults(): Boolean = when {
        isTimed -> timeLimitSeconds?.let { it in MIN_TIMED_SECONDS..MAX_TIMED_SECONDS } == true
        else -> timeLimitSeconds == null
    }

    private fun attemptStatusResult(status: TestAttemptStatus?): TestAttemptStartResult = when (status) {
        TestAttemptStatus.IN_PROGRESS -> TestAttemptStartResult.AlreadyInProgress
        TestAttemptStatus.SUBMITTING, TestAttemptStatus.SUBMITTED -> TestAttemptStartResult.AlreadySubmitted
        TestAttemptStatus.CREATED -> TestAttemptStartResult.Corrupt
        null -> TestAttemptStartResult.Missing
    }

    private companion object {
        const val LOCAL_USER_ID = "local-user"
        const val MILLIS_PER_SECOND = 1_000L
        const val MIN_TIMED_SECONDS = 60L
        const val MAX_TIMED_SECONDS = 180L * 60L
        const val COMPLETION_SUBMITTED = "submitted"
        const val COMPLETION_TIME_EXPIRED = "time_expired"
    }
}
