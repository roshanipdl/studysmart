package com.studysmart.app.feature.test

import androidx.room.withTransaction
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.common.RandomSource
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.database.QuestionKeywordEntity
import com.studysmart.app.core.database.TestAttemptContextRow
import com.studysmart.app.core.database.TestAttemptDao
import com.studysmart.app.core.database.TestAttemptEntity
import com.studysmart.app.core.database.TestAttemptQuestionEntity
import com.studysmart.app.core.database.TestAnswerEntity
import com.studysmart.app.core.database.TestCandidateRow
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.model.KeywordNormalizer
import com.studysmart.app.core.model.KeywordSnapshotCodec
import com.studysmart.app.core.model.KeywordSnapshotEntry
import com.studysmart.app.core.model.TestAttemptOriginType
import com.studysmart.app.core.model.TestAttemptScopeType
import com.studysmart.app.core.model.TestAttemptStatus
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.TestQuestionSource
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed interface TestAttemptCreationResult {
    data class Created(val attemptId: String) : TestAttemptCreationResult
    data object NoQuestions : TestAttemptCreationResult
    data object MissingScope : TestAttemptCreationResult
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
)

data class TestAttemptModel(
    val attempt: TestAttemptEntity,
    val questions: List<TestAttemptQuestionEntity>,
    val answers: List<TestAnswerEntity>,
)

interface TestAttemptRepository {
    fun observeAttempt(attemptId: String): Flow<TestAttemptModel?>

    suspend fun createChapterAttempt(request: ChapterTestAttemptRequest): TestAttemptCreationResult

    suspend fun createImportantAttempt(request: ImportantTestAttemptRequest): TestAttemptCreationResult

    suspend fun discardActiveAttempt(attemptId: String): Boolean
}

class RoomTestAttemptRepository(
    private val database: StudySmartDatabase,
    private val preferencesRepository: PreferencesRepository,
    private val clock: Clock,
    private val randomSource: RandomSource,
    private val idGenerator: IdGenerator,
    private val dispatchers: DispatcherProvider,
    private val selectionEngine: TestQuestionSelectionEngine = TestQuestionSelectionEngine(),
    private val userId: String = LOCAL_USER_ID,
) : TestAttemptRepository {
    private val dao: TestAttemptDao = database.testAttemptDao()

    override fun observeAttempt(attemptId: String): Flow<TestAttemptModel?> = combine(
        dao.observeAttempt(attemptId),
        dao.observeAttemptQuestions(attemptId),
        dao.observeAttemptAnswers(attemptId),
    ) { attempt, questions, answers ->
        attempt?.let { TestAttemptModel(it, questions, answers) }
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
            contextLoader = { dao.getCoursePeriodContext(request.courseId, request.academicPeriodId) },
            candidatesLoader = {
                dao.getImportantCandidates(userId, request.courseId, request.academicPeriodId)
            },
            scopeType = TestAttemptScopeType.IMPORTANT,
            scopeReferenceId = request.academicPeriodId,
            originType = TestAttemptOriginType.IMPORTANT,
            originReferenceId = request.originReferenceId,
        )
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
            ) ?: return@withTransaction TestAttemptCreationResult.NoQuestions
            val candidateById = candidates.associateBy { it.question.id }
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
            TestAttemptCreationResult.Created(attemptId)
        }
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

    private companion object {
        const val LOCAL_USER_ID = "local-user"
    }
}
