package com.studysmart.app.feature.preparation

import androidx.room.withTransaction
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.common.RandomSource
import com.studysmart.app.core.database.PreparationCandidateRow
import com.studysmart.app.core.database.PreparationContextRow
import com.studysmart.app.core.database.PreparationDao
import com.studysmart.app.core.database.PreparationSessionEntity
import com.studysmart.app.core.database.PreparationSessionItemEntity
import com.studysmart.app.core.database.ReviewRecordEntity
import com.studysmart.app.core.database.ReviewScheduleEntity
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.core.model.PreparationSessionStatus
import com.studysmart.app.core.model.QuestionType
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.model.ReviewScheduleSnapshot
import com.studysmart.app.feature.review.DeterministicReviewScheduler
import com.studysmart.app.feature.review.ReviewScheduler
import com.studysmart.app.feature.review.ReviewStateReducer
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class PreparationContextModel(
    val courseId: String,
    val courseCode: String,
    val courseName: String,
    val academicPeriodId: String,
    val academicPeriodName: String,
    val subjectId: String?,
    val subjectCode: String?,
    val subjectName: String?,
    val chapterId: String?,
    val chapterTitle: String?,
    val topicId: String?,
    val topicTitle: String?,
)

data class PreparationItemModel(
    val id: String,
    val questionId: String,
    val questionSnapshot: String,
    val answerSnapshot: String,
    val questionTypeSnapshot: QuestionType,
    val originalOrder: Int,
    val queueSequence: Int,
    val presentationCount: Int,
    val hardRequeueUsed: Boolean,
    val isRevealed: Boolean,
    val isCompleted: Boolean,
    val lastRating: Rating?,
    val lastPresentedAt: Instant?,
    val answerFormatSnapshot: com.studysmart.app.core.model.AnswerFormat = com.studysmart.app.core.model.AnswerFormat.PLAIN_TEXT,
)

data class PreparationReviewEventModel(
    val id: String,
    val questionId: String,
    val rating: Rating,
    val reviewedAt: Instant,
)

data class PreparationSessionModel(
    val id: String,
    val courseId: String,
    val academicPeriodId: String,
    val subjectId: String?,
    val chapterId: String?,
    val topicId: String?,
    val scopeType: PreparationScopeType,
    val originType: PreparationOriginType,
    val originReferenceId: String?,
    val status: PreparationSessionStatus,
    val startedAt: Instant,
    val completedAt: Instant?,
    val uniqueQuestionCount: Int,
    val reviewCount: Int,
    val context: PreparationContextModel,
    val items: List<PreparationItemModel>,
    val reviewEvents: List<PreparationReviewEventModel>,
) {
    val currentItem: PreparationItemModel?
        get() = items.firstOrNull { !it.isCompleted }

    val isComplete: Boolean
        get() = status == PreparationSessionStatus.COMPLETED

    val completionProgress: Float
        get() = if (uniqueQuestionCount == 0) {
            0f
        } else {
            (items.count { it.isCompleted }.toFloat() / uniqueQuestionCount).coerceIn(0f, 1f)
        }
}

data class PreparationSessionRequest(
    val scopeType: PreparationScopeType,
    val scopeId: String,
    val originType: PreparationOriginType,
    val originReferenceId: String?,
    val creationRequestId: String,
    val questionCount: Int = com.studysmart.app.core.model.PreparationDefaults.DEFAULT_QUESTION_COUNT,
)

data class DueReviewSessionRequest(
    val courseId: String,
    val academicPeriodId: String,
    val creationRequestId: String,
    val questionCount: Int = com.studysmart.app.core.model.PreparationDefaults.DEFAULT_QUESTION_COUNT,
)

data class StatusFilterSessionRequest(
    val courseId: String,
    val academicPeriodId: String,
    val filter: com.studysmart.app.feature.review.ReviewFilter,
    val creationRequestId: String,
    val questionCount: Int,
)

data class ImportantPreparationRequest(
    val scope: com.studysmart.app.core.model.SavedQuestionScope,
    val creationRequestId: String,
    val questionCount: Int = com.studysmart.app.core.model.PreparationDefaults.DEFAULT_QUESTION_COUNT,
)

sealed interface PreparationCreationResult {
    data class Created(val sessionId: String) : PreparationCreationResult
    data object NoQuestions : PreparationCreationResult
    data object MissingScope : PreparationCreationResult
}

data class PreparationRatingRequest(
    val sessionId: String,
    val questionId: String,
    val expectedPresentationCount: Int,
    val rating: Rating,
    val reviewRecordId: String,
    val responseTimeMs: Long? = null,
)

data class PreparationRatingResult(
    val sessionId: String,
    val completed: Boolean,
)

interface PreparationRepository {
    suspend fun createStatusFilterSession(request: StatusFilterSessionRequest): PreparationCreationResult = PreparationCreationResult.MissingScope
    suspend fun getChapterCoverage(chapterId: String): PreparationChapterCoverage?
    fun observeSession(sessionId: String): Flow<PreparationSessionModel?>

    suspend fun createSession(request: PreparationSessionRequest): PreparationCreationResult

    suspend fun createDueReviewSession(request: DueReviewSessionRequest): PreparationCreationResult

    suspend fun createImportantSession(request: ImportantPreparationRequest): PreparationCreationResult = PreparationCreationResult.MissingScope

    suspend fun revealCurrent(sessionId: String, questionId: String): Boolean

    suspend fun rateCurrent(request: PreparationRatingRequest): PreparationRatingResult

    suspend fun discardSession(sessionId: String): Boolean
}

class RoomPreparationRepository(
    private val database: StudySmartDatabase,
    private val dao: PreparationDao = database.preparationDao(),
    private val clock: Clock,
    private val randomSource: RandomSource,
    private val idGenerator: IdGenerator,
    private val dispatchers: DispatcherProvider,
    private val selectionEngine: PreparationSelectionEngine = PreparationSelectionEngine(),
    private val queueEngine: PreparationQueueEngine = PreparationQueueEngine(),
    private val reviewScheduler: ReviewScheduler = DeterministicReviewScheduler(clock),
    private val reviewStateReducer: ReviewStateReducer = ReviewStateReducer(),
    private val userId: String = LOCAL_USER_ID,
) : PreparationRepository {
    override suspend fun getChapterCoverage(chapterId: String): PreparationChapterCoverage? =
        withContext(dispatchers.io) {
            require(chapterId.isNotBlank())
            database.withTransaction {
                if (dao.getChapterContext(chapterId) == null) null
                else projectChapterCoverage(dao.getChapterCandidates(userId, chapterId).map { it.hasReview })
            }
        }

    override fun observeSession(sessionId: String): Flow<PreparationSessionModel?> = combine(
        dao.observeSession(sessionId),
        dao.observeSessionItems(sessionId),
        dao.observeSessionReviewRecords(sessionId),
        dao.observeSessionContext(sessionId),
    ) { session, items, reviewEvents, context ->
        if (session == null || context == null) {
            null
        } else {
            PreparationSessionModel(
                id = session.id,
                courseId = session.courseId,
                academicPeriodId = session.academicPeriodId,
                subjectId = session.subjectId,
                chapterId = session.chapterId,
                topicId = session.topicId,
                scopeType = session.scopeType,
                originType = session.originType,
                originReferenceId = session.originReferenceId,
                status = session.status,
                startedAt = session.startedAt,
                completedAt = session.completedAt,
                uniqueQuestionCount = session.uniqueQuestionCount,
                reviewCount = session.reviewCount,
                context = contextModel(context),
                items = items.map { itemModel(it) },
                reviewEvents = reviewEvents.map { reviewEventModel(it) },
            )
        }
    }

    override suspend fun createSession(request: PreparationSessionRequest): PreparationCreationResult =
        withContext(dispatchers.io) {
            require(request.scopeId.isNotBlank()) { "scopeId must not be blank" }
            require(request.creationRequestId.isNotBlank()) { "creationRequestId must not be blank" }
            require(request.questionCount in 1..MAX_UNIQUE_QUESTIONS) { "invalid preparation question count" }
            database.withTransaction {
                dao.getSessionByCreationRequestId(request.creationRequestId)?.let {
                    return@withTransaction PreparationCreationResult.Created(it.id)
                }

                val context = when (request.scopeType) {
                    PreparationScopeType.CHAPTER -> dao.getChapterContext(request.scopeId)
                    PreparationScopeType.TOPIC -> dao.getTopicContext(request.scopeId)
                    PreparationScopeType.DUE_REVIEW, PreparationScopeType.IMPORTANT, PreparationScopeType.STATUS_FILTER -> return@withTransaction PreparationCreationResult.MissingScope
                } ?: return@withTransaction PreparationCreationResult.MissingScope

                val candidateRows = when (request.scopeType) {
                    PreparationScopeType.CHAPTER -> dao.getChapterCandidates(userId, request.scopeId)
                    PreparationScopeType.TOPIC -> dao.getTopicCandidates(userId, request.scopeId)
                    PreparationScopeType.DUE_REVIEW, PreparationScopeType.IMPORTANT, PreparationScopeType.STATUS_FILTER -> return@withTransaction PreparationCreationResult.MissingScope
                }
                val selectedAt = clock.instant()
                val plan = selectionEngine.createPlan(
                    candidates = candidateRows.map { it.toCandidate() },
                    policy = PreparationSelectionPolicy.ADAPTIVE,
                    selectedAt = selectedAt,
                    selectionSeed = randomSource.nextLong(),
                    uniqueQuestionLimit = request.questionCount,
                ) ?: return@withTransaction PreparationCreationResult.NoQuestions

                val questionById = candidateRows.associateBy { it.question.id }
                PreparationCreationResult.Created(
                    insertSession(
                        context = context,
                        scopeType = request.scopeType,
                        scopeReferenceId = request.scopeId,
                        originType = request.originType,
                        originReferenceId = request.originReferenceId,
                        creationRequestId = request.creationRequestId,
                        plan = plan,
                        questionById = questionById,
                        now = selectedAt,
                    ),
                )
            }
        }

    override suspend fun createDueReviewSession(request: DueReviewSessionRequest): PreparationCreationResult =
        withContext(dispatchers.io) {
            require(request.courseId.isNotBlank()) { "courseId must not be blank" }
            require(request.academicPeriodId.isNotBlank()) { "academicPeriodId must not be blank" }
            require(request.creationRequestId.isNotBlank()) { "creationRequestId must not be blank" }
            require(request.questionCount in 1..MAX_UNIQUE_QUESTIONS) { "invalid review question count" }
            database.withTransaction {
                dao.getSessionByCreationRequestId(request.creationRequestId)?.let {
                    return@withTransaction PreparationCreationResult.Created(it.id)
                }

                val context = dao.getCoursePeriodContext(request.courseId, request.academicPeriodId)
                    ?: return@withTransaction PreparationCreationResult.MissingScope
                val selectedAt = clock.instant()
                val candidateRows = dao.getDueCandidates(
                    userId = userId,
                    courseId = request.courseId,
                    academicPeriodId = request.academicPeriodId,
                    now = selectedAt,
                )
                val plan = selectionEngine.createPlan(
                    candidates = candidateRows.mapIndexed { index, row ->
                        row.toCandidate(curriculumOrder = index.toLong())
                    },
                    policy = PreparationSelectionPolicy.DUE_REVIEW,
                    selectedAt = selectedAt,
                    selectionSeed = randomSource.nextLong(),
                    uniqueQuestionLimit = request.questionCount,
                ) ?: return@withTransaction PreparationCreationResult.NoQuestions

                PreparationCreationResult.Created(
                    insertSession(
                        context = context,
                        scopeType = PreparationScopeType.DUE_REVIEW,
                        scopeReferenceId = request.academicPeriodId,
                        originType = PreparationOriginType.REVIEW,
                        originReferenceId = null,
                        creationRequestId = request.creationRequestId,
                        plan = plan,
                        questionById = candidateRows.associateBy { it.question.id },
                        now = selectedAt,
                    ),
                )
            }
        }

    override suspend fun createImportantSession(request: ImportantPreparationRequest): PreparationCreationResult = withContext(dispatchers.io) {
        require(request.creationRequestId.isNotBlank())
        require(request.questionCount in 1..MAX_UNIQUE_QUESTIONS)
        val scope = request.scope
        database.withTransaction {
            dao.getSessionByCreationRequestId(request.creationRequestId)?.let { existing ->
                return@withTransaction if (existing.userId == userId && existing.scopeType == PreparationScopeType.IMPORTANT &&
                    existing.courseId == scope.courseId && existing.academicPeriodId == scope.academicPeriodId &&
                    existing.subjectId == scope.subjectId && existing.chapterId == scope.chapterId)
                    PreparationCreationResult.Created(existing.id) else PreparationCreationResult.MissingScope
            }
            val context = dao.getImportantContext(scope.courseId, scope.academicPeriodId, scope.subjectId, scope.chapterId)
                ?: return@withTransaction PreparationCreationResult.MissingScope
            val candidates = database.testAttemptDao().getImportantCandidates(userId, scope.courseId, scope.academicPeriodId, scope.subjectId, scope.chapterId)
            val now = clock.instant()
            val plan = selectionEngine.createPlan(candidates.mapIndexed { index, row ->
                PreparationCandidate(row.question.id, row.question.topicId, index.toLong(), false, !row.hasReview, null, row.bookmarkCreatedAt)
            }, PreparationSelectionPolicy.IMPORTANT, now, randomSource.nextLong(), request.questionCount)
                ?: return@withTransaction PreparationCreationResult.NoQuestions
            PreparationCreationResult.Created(insertSession(context, PreparationScopeType.IMPORTANT, scope.academicPeriodId,
                PreparationOriginType.IMPORTANT, null, request.creationRequestId, plan,
                candidates.associate { it.question.id to PreparationCandidateRow(it.question, it.latestRating, it.hasReview, null) }, now))
        }
    }

    override suspend fun createStatusFilterSession(request: StatusFilterSessionRequest): PreparationCreationResult = withContext(dispatchers.io) {
        require(request.questionCount in 1..MAX_UNIQUE_QUESTIONS)
        require(request.creationRequestId.isNotBlank())
        database.withTransaction {
            val context = dao.getCoursePeriodContext(request.courseId, request.academicPeriodId)
                ?: return@withTransaction PreparationCreationResult.MissingScope
            dao.getSessionByCreationRequestId(request.creationRequestId)?.let { existing ->
                return@withTransaction if (existing.userId == userId && existing.scopeType == PreparationScopeType.STATUS_FILTER &&
                    existing.courseId == request.courseId && existing.academicPeriodId == request.academicPeriodId &&
                    existing.scopeReferenceId == request.filter.routeValue)
                    PreparationCreationResult.Created(existing.id) else PreparationCreationResult.MissingScope
            }
            val rows = database.readinessDao().observeQuestions(userId, request.courseId, request.academicPeriodId, null, null).first()
            val selected = request.filter.select(rows).take(request.questionCount)
            if (selected.isEmpty()) return@withTransaction PreparationCreationResult.NoQuestions
            val candidates = selected.map { it.chapterId }.distinct().flatMap { dao.getChapterCandidates(userId, it) }.associateBy { it.question.id }
            val now = clock.instant()
            val plan = PreparationPlan(randomSource.nextLong(), selected.map { it.questionId })
            PreparationCreationResult.Created(insertSession(context, PreparationScopeType.STATUS_FILTER,
                request.filter.routeValue, PreparationOriginType.REVIEW, null, request.creationRequestId,
                plan, candidates, now))
        }
    }

    private suspend fun insertSession(
        context: PreparationContextRow,
        scopeType: PreparationScopeType,
        scopeReferenceId: String?,
        originType: PreparationOriginType,
        originReferenceId: String?,
        creationRequestId: String,
        plan: PreparationPlan,
        questionById: Map<String, PreparationCandidateRow>,
        now: Instant,
    ): String {
        val sessionId = idGenerator.nextId("session")
        dao.insertSession(
            PreparationSessionEntity(
                id = sessionId,
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
                selectionSeed = plan.selectionSeed,
                status = PreparationSessionStatus.ACTIVE,
                startedAt = now,
                completedAt = null,
                updatedAt = now,
                uniqueQuestionCount = plan.uniqueQuestionCount,
                reviewCount = 0,
            ),
        )
        dao.insertSessionItems(
            plan.orderedQuestionIds.mapIndexed { index, questionId ->
                val question = questionById.getValue(questionId).question
                PreparationSessionItemEntity(
                    id = idGenerator.nextId("session-item"),
                    sessionId = sessionId,
                    questionId = question.id,
                    questionSnapshot = question.question,
                    answerSnapshot = question.answer,
                    answerFormatSnapshot = question.answerFormat,
                    questionTypeSnapshot = question.questionType,
                    originalOrder = index,
                    queueSequence = index,
                    presentationCount = 0,
                    hardRequeueUsed = false,
                    isRevealed = false,
                    isCompleted = false,
                    lastRating = null,
                    lastPresentedAt = null,
                )
            },
        )
        return sessionId
    }

    override suspend fun revealCurrent(sessionId: String, questionId: String): Boolean =
        withContext(dispatchers.io) {
            database.withTransaction {
                val session = dao.getSession(sessionId) ?: return@withTransaction false
                if (session.status != PreparationSessionStatus.ACTIVE) return@withTransaction false
                val item = dao.getSessionItems(sessionId)
                    .firstOrNull { !it.isCompleted }
                    ?: return@withTransaction false
                if (item.questionId != questionId) return@withTransaction false
                item.isRevealed || dao.revealItem(item.id) == 1
            }
        }

    override suspend fun rateCurrent(request: PreparationRatingRequest): PreparationRatingResult =
        withContext(dispatchers.io) {
            database.withTransaction {
                val existingRecord = dao.getReviewRecord(request.reviewRecordId)
                if (existingRecord != null) {
                    require(existingRecord.userId == userId) {
                        "rating request belongs to another user"
                    }
                    require(existingRecord.questionId == request.questionId) {
                        "rating request does not match the committed question"
                    }
                    require(existingRecord.rating == request.rating) {
                        "rating request does not match the committed rating"
                    }
                    val existingSession = dao.getSession(request.sessionId)
                        ?: return@withTransaction PreparationRatingResult(request.sessionId, completed = true)
                    return@withTransaction PreparationRatingResult(
                        sessionId = existingSession.id,
                        completed = existingSession.status == PreparationSessionStatus.COMPLETED,
                    )
                }

                val session = dao.getSession(request.sessionId)
                    ?: throw IllegalStateException("Preparation session is no longer available.")
                require(session.status == PreparationSessionStatus.ACTIVE) {
                    "only active preparation sessions can be rated"
                }
                val persistedItems = dao.getSessionItems(request.sessionId)
                val currentItem = persistedItems.firstOrNull { !it.isCompleted }
                    ?: throw IllegalStateException("Preparation session has no pending item.")
                require(currentItem.questionId == request.questionId) {
                    "rating request does not match the current question"
                }
                require(currentItem.presentationCount == request.expectedPresentationCount) {
                    "rating request is stale"
                }
                require(currentItem.isRevealed) {
                    "answer must be revealed before rating"
                }

                val queueState = persistedItems.toQueueState()
                val transition = queueEngine.rateCurrent(queueState, request.rating)
                val reviewDecision = reviewScheduler.nextReview(request.rating)
                val currentSchedule = dao.getReviewSchedule(userId, request.questionId)
                val scheduleSnapshot = currentSchedule?.toSnapshot(request.questionId)
                val nextSchedule = reviewStateReducer.reduce(
                    questionId = request.questionId,
                    current = scheduleSnapshot,
                    decision = reviewDecision,
                )
                dao.insertReviewRecord(
                    ReviewRecordEntity(
                        id = request.reviewRecordId,
                        userId = userId,
                        preparationSessionId = request.sessionId,
                        questionId = request.questionId,
                        rating = request.rating,
                        responseTimeMs = request.responseTimeMs,
                        reviewedAt = reviewDecision.reviewedAt,
                    ),
                )
                dao.upsertReviewSchedule(nextSchedule.toEntity(userId))
                val pendingIndex = transition.state.pendingQuestionIds.withIndex()
                    .associate { indexed -> indexed.value to indexed.index }
                dao.updateSessionItems(
                    persistedItems.map { item ->
                        val updated = transition.state.itemsByQuestionId.getValue(item.questionId)
                        item.copy(
                            queueSequence = pendingIndex[item.questionId] ?: item.queueSequence,
                            presentationCount = updated.presentationCount,
                            hardRequeueUsed = updated.hardRequeueUsed,
                            isRevealed = if (item.questionId == request.questionId) false else item.isRevealed,
                            isCompleted = updated.isCompleted,
                            lastRating = updated.lastRating,
                            lastPresentedAt = if (item.questionId == request.questionId) {
                                reviewDecision.reviewedAt
                            } else {
                                item.lastPresentedAt
                            },
                        )
                    },
                )
                dao.updateSession(
                    session.copy(
                        status = if (transition.sessionCompleted) {
                            PreparationSessionStatus.COMPLETED
                        } else {
                            PreparationSessionStatus.ACTIVE
                        },
                        completedAt = reviewDecision.reviewedAt.takeIf { transition.sessionCompleted },
                        updatedAt = reviewDecision.reviewedAt,
                        reviewCount = session.reviewCount + 1,
                    ),
                )
                PreparationRatingResult(
                    sessionId = session.id,
                    completed = transition.sessionCompleted,
                )
            }
        }

    override suspend fun discardSession(sessionId: String): Boolean = withContext(dispatchers.io) {
        database.withTransaction {
            dao.deleteActiveSession(sessionId) == 1
        }
    }

    private fun PreparationCandidateRow.toCandidate(
        curriculumOrder: Long = question.sortOrder.toLong(),
    ): PreparationCandidate = PreparationCandidate(
        questionId = question.id,
        topicId = question.topicId,
        curriculumOrder = curriculumOrder,
        isWeak = latestRating == Rating.AGAIN || latestRating == Rating.HARD,
        isUnseen = !hasReview,
        nextReviewAt = nextReviewAt,
    )

    private fun itemModel(item: PreparationSessionItemEntity): PreparationItemModel = PreparationItemModel(
        id = item.id,
        questionId = item.questionId,
        questionSnapshot = item.questionSnapshot,
        answerSnapshot = item.answerSnapshot,
        answerFormatSnapshot = item.answerFormatSnapshot,
        questionTypeSnapshot = item.questionTypeSnapshot,
        originalOrder = item.originalOrder,
        queueSequence = item.queueSequence,
        presentationCount = item.presentationCount,
        hardRequeueUsed = item.hardRequeueUsed,
        isRevealed = item.isRevealed,
        isCompleted = item.isCompleted,
        lastRating = item.lastRating,
        lastPresentedAt = item.lastPresentedAt,
    )

    private fun reviewEventModel(record: ReviewRecordEntity): PreparationReviewEventModel =
        PreparationReviewEventModel(
            id = record.id,
            questionId = record.questionId,
            rating = record.rating,
            reviewedAt = record.reviewedAt,
        )

    private fun contextModel(row: PreparationContextRow): PreparationContextModel = PreparationContextModel(
        courseId = row.courseId,
        courseCode = row.courseCode,
        courseName = row.courseName,
        academicPeriodId = row.academicPeriodId,
        academicPeriodName = row.academicPeriodName,
        subjectId = row.subjectId,
        subjectCode = row.subjectCode,
        subjectName = row.subjectName,
        chapterId = row.chapterId,
        chapterTitle = row.chapterTitle,
        topicId = row.topicId,
        topicTitle = row.topicTitle,
    )

    private fun List<PreparationSessionItemEntity>.toQueueState(): PreparationQueueState {
        require(isNotEmpty()) { "a persisted queue must contain items" }
        val itemsByQuestionId = associate { item ->
            item.questionId to PreparationQueueItem(
                questionId = item.questionId,
                originalOrder = item.originalOrder,
                presentationCount = item.presentationCount,
                hardRequeueUsed = item.hardRequeueUsed,
                isCompleted = item.isCompleted,
                lastRating = item.lastRating,
            )
        }
        return PreparationQueueState(
            itemsByQuestionId = itemsByQuestionId,
            pendingQuestionIds = asSequence()
                .filterNot(PreparationSessionItemEntity::isCompleted)
                .sortedWith(compareBy(PreparationSessionItemEntity::queueSequence).thenBy(PreparationSessionItemEntity::originalOrder))
                .map(PreparationSessionItemEntity::questionId)
                .toList(),
            totalPresentationCount = sumOf(PreparationSessionItemEntity::presentationCount),
        )
    }

    private fun ReviewScheduleEntity.toSnapshot(questionId: String): ReviewScheduleSnapshot = ReviewScheduleSnapshot(
        questionId = questionId,
        learningState = learningState,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        intervalSeconds = intervalSeconds,
        repetitionCount = repetitionCount,
        lapseCount = lapseCount,
    )

    private fun ReviewScheduleSnapshot.toEntity(userId: String): ReviewScheduleEntity = ReviewScheduleEntity(
        userId = userId,
        questionId = questionId,
        learningState = learningState,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        intervalSeconds = intervalSeconds,
        repetitionCount = repetitionCount,
        lapseCount = lapseCount,
    )

    private companion object {
        const val LOCAL_USER_ID = "local-user"
    }
}
