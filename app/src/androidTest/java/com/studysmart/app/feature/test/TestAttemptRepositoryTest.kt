package com.studysmart.app.feature.test

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.common.DefaultDispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.common.MonotonicClock
import com.studysmart.app.core.common.RandomSource
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.database.UserQuestionFlagEntity
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.TestQuestionSource
import com.studysmart.app.app.RoomStartupCleanupCoordinator
import com.studysmart.app.core.model.TestAttemptStatus
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TestAttemptRepositoryTest {
    private lateinit var database: com.studysmart.app.core.database.StudySmartDatabase

    @Before
    fun setUp() {
        database = createInMemoryStudySmartDatabase(
            ApplicationProvider.getApplicationContext<Context>(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun chapterAttemptPersistsFixedSnapshotsAnswersAndIsIdempotent() = runBlocking {
        val repository = repository(TestDefaults(questionSource = TestQuestionSource.ALL))
        val request = ChapterTestAttemptRequest(
            chapterId = "chapter-bca-s1-dbms-01",
            creationRequestId = "request-1",
        )

        val first = repository.createChapterAttempt(request)
        val second = repository.createChapterAttempt(request)

        assertTrue(first is TestAttemptCreationResult.Created)
        assertEquals(first, second)
        val attemptId = (first as TestAttemptCreationResult.Created).attemptId
        val attempt = database.testAttemptDao().getAttempt(attemptId)
        assertNotNull(attempt)
        assertEquals(2, attempt?.actualQuestionCount)
        assertEquals(TestQuestionSource.ALL, attempt?.questionSource)
        val snapshots = database.testAttemptDao().observeAttemptQuestions(attemptId).first()
        assertEquals(2, snapshots.size)
        assertEquals(2, database.testAttemptDao().observeAttemptAnswers(attemptId).first().size)

        database.openHelper.writableDatabase.execSQL(
            "UPDATE questions SET question = 'changed' WHERE id = 'question-bca-s1-dbms-direct'",
        )
        assertEquals(
            "What is a database?",
            database.testAttemptDao().observeAttemptQuestions(attemptId).first()
                .first { it.questionId == "question-bca-s1-dbms-direct" }
                .questionTextSnapshot,
        )
    }

    @Test
    fun importantAttemptIsBoundToTheSelectedCourseAndPeriod() = runBlocking {
        database.testAttemptDao().insertQuestionFlag(
            UserQuestionFlagEntity(
                userId = "local-user",
                questionId = "question-bca-s1-dbms-direct",
                isImportant = true,
                createdAt = Instant.EPOCH,
            ),
        )
        database.testAttemptDao().insertQuestionFlag(
            UserQuestionFlagEntity(
                userId = "local-user",
                questionId = "question-year1-foundations-01",
                isImportant = true,
                createdAt = Instant.EPOCH,
            ),
        )
        val repository = repository(
            TestDefaults(questionCount = 30, questionSource = TestQuestionSource.IMPORTANT),
        )

        val result = repository.createImportantAttempt(
            ImportantTestAttemptRequest(
                courseId = "course-bca",
                academicPeriodId = "period-bca-semester-1",
                creationRequestId = "important-request",
            ),
        ) as TestAttemptCreationResult.Created

        val questionIds = database.testAttemptDao()
            .observeAttemptQuestions(result.attemptId)
            .first()
            .map { it.questionId }
        assertEquals(listOf("question-bca-s1-dbms-direct"), questionIds)
    }

    @Test
    fun chapterImportantSourceUsesOnlyBookmarksOwnedByTheCurrentUser() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO users(id, displayName, createdAt, updatedAt) VALUES (?, ?, ?, ?)",
            arrayOf<Any>("another-user", "Another student", 0L, 0L),
        )
        database.testAttemptDao().insertQuestionFlag(
            UserQuestionFlagEntity(
                userId = "local-user",
                questionId = "question-bca-s1-dbms-direct",
                isImportant = true,
                createdAt = Instant.EPOCH,
            ),
        )
        database.testAttemptDao().insertQuestionFlag(
            UserQuestionFlagEntity(
                userId = "local-user",
                questionId = "question-bca-s1-dbms-topic",
                isImportant = false,
                createdAt = Instant.EPOCH,
            ),
        )
        database.testAttemptDao().insertQuestionFlag(
            UserQuestionFlagEntity(
                userId = "another-user",
                questionId = "question-bca-s1-dbms-topic",
                isImportant = true,
                createdAt = Instant.EPOCH,
            ),
        )
        val repository = repository(
            TestDefaults(questionCount = 30, questionSource = TestQuestionSource.IMPORTANT),
        )

        val result = repository.createChapterAttempt(
            ChapterTestAttemptRequest(
                chapterId = "chapter-bca-s1-dbms-01",
                creationRequestId = "chapter-important-request",
            ),
        ) as TestAttemptCreationResult.Created

        val questionIds = database.testAttemptDao()
            .observeAttemptQuestions(result.attemptId)
            .first()
            .map { it.questionId }
        assertEquals(listOf("question-bca-s1-dbms-direct"), questionIds)

        val otherUserCandidates = database.testAttemptDao()
            .getChapterCandidates("another-user", "chapter-bca-s1-dbms-01")
        assertEquals(
            listOf("question-bca-s1-dbms-topic"),
            otherUserCandidates.filter { it.isImportant }.map { it.question.id },
        )
    }

    @Test
    fun discardDeletesActiveAttemptChildren() = runBlocking {
        val repository = repository(TestDefaults(questionSource = TestQuestionSource.ALL))
        val created = repository.createChapterAttempt(
            ChapterTestAttemptRequest(
                chapterId = "chapter-bca-s1-dbms-01",
                creationRequestId = "discard-request",
            ),
        ) as TestAttemptCreationResult.Created

        assertTrue(repository.discardActiveAttempt(created.attemptId))
        assertEquals(null, database.testAttemptDao().getAttempt(created.attemptId))
        assertTrue(database.testAttemptDao().observeAttemptQuestions(created.attemptId).first().isEmpty())
        assertTrue(database.testAttemptDao().observeAttemptAnswers(created.attemptId).first().isEmpty())
    }

    @Test
    fun attemptStartsOnceAndDraftUpdatesOnlyItsOwnedAnswer() = runBlocking {
        val repository = repository(TestDefaults(questionSource = TestQuestionSource.ALL))
        val created = repository.createChapterAttempt(
            ChapterTestAttemptRequest(
                chapterId = "chapter-bca-s1-dbms-01",
                creationRequestId = "start-request",
            ),
        ) as TestAttemptCreationResult.Created

        assertEquals(TestAttemptStartResult.Started, repository.startAttempt(created.attemptId))
        assertEquals(
            TestAttemptStartResult.AlreadyInProgress,
            repository.startAttempt(created.attemptId),
        )
        val attempt = database.testAttemptDao().getAttempt(created.attemptId)!!
        assertEquals(TestAttemptStatus.IN_PROGRESS, attempt.status)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), attempt.startedAt)
        assertEquals(10_000L, attempt.startedAtElapsedRealtimeMillis)
        assertEquals("test-boot", attempt.startedBootId)
        assertEquals(null, attempt.deadlineAt)

        val firstQuestion = database.testAttemptDao()
            .observeAttemptQuestions(created.attemptId)
            .first()
            .first()
        assertEquals(
            TestAnswerSaveResult.Saved,
            repository.saveAnswerDraft(
                created.attemptId,
                TestAnswerDraft(
                    attemptQuestionId = firstQuestion.id,
                    answerText = "draft answer",
                    isFlagged = true,
                    accumulatedTimeMillis = 2_400L,
                ),
            ),
        )
        val saved = database.testAttemptDao().getAnswerForAttemptQuestion(
            created.attemptId,
            firstQuestion.id,
        )!!
        assertEquals("draft answer", saved.answerText)
        assertTrue(saved.isFlagged)
        assertEquals(2_400L, saved.accumulatedTimeMillis)
        assertEquals(2L, saved.timeSpentSeconds)

        database.testAttemptDao().updateAttempt(
            attempt.copy(status = TestAttemptStatus.SUBMITTED),
        )
        assertEquals(
            TestAnswerSaveResult.AlreadySubmitted,
            repository.saveAnswerDraft(
                created.attemptId,
                TestAnswerDraft(firstQuestion.id, "must not overwrite", false, 3_000L),
            ),
        )
        assertEquals(
            "draft answer",
            database.testAttemptDao().getAnswerForAttemptQuestion(
                created.attemptId,
                firstQuestion.id,
            )?.answerText,
        )
    }

    @Test
    fun submissionPersistsFinalDraftEvidenceAndRepeatedSubmitDoesNotReevaluate() = runBlocking {
        val monotonic = MutableMonotonicClock(10_000L)
        val repository = repository(
            TestDefaults(questionCount = 1, questionSource = TestQuestionSource.ALL),
            monotonicClock = monotonic,
        )
        val created = repository.createChapterAttempt(
            ChapterTestAttemptRequest("chapter-bca-s1-dbms-01", creationRequestId = "submit-request"),
        ) as TestAttemptCreationResult.Created
        assertEquals(TestAttemptStartResult.Started, repository.startAttempt(created.attemptId))
        val question = database.testAttemptDao().observeAttemptQuestions(created.attemptId).first().single()
        monotonic.now = 13_000L

        assertEquals(
            TestSubmissionResult.Submitted,
            repository.submitAttempt(
                created.attemptId,
                TestSubmitAction.MANUAL,
                TestAnswerDraft(question.id, "Database organised collection", true, 3_000L),
            ),
        )
        val submitted = database.testAttemptDao().getAttempt(created.attemptId)!!
        val answer = database.testAttemptDao().getAnswerForAttemptQuestion(created.attemptId, question.id)!!
        assertEquals(TestAttemptStatus.SUBMITTED, submitted.status)
        assertEquals("submitted", submitted.completionReason)
        assertEquals(3L, submitted.timeTakenSeconds)
        assertEquals("database organised collection", answer.normalizedAnswer)
        assertEquals(100.0, answer.keywordMatchPercent!!, 0.0)
        assertTrue(answer.isFlagged)
        assertEquals(
            listOf("database", "organised collection"),
            com.studysmart.app.core.model.KeywordEvidenceCodec.decode(answer.matchedKeywordsJson!!),
        )
        assertEquals(
            TestSubmissionResult.AlreadySubmitted,
            repository.submitAttempt(created.attemptId, TestSubmitAction.MANUAL, null),
        )
        assertEquals(100.0, database.testAttemptDao().getAttempt(created.attemptId)?.percentage!!, 0.0)
    }

    @Test
    fun expiryLocksDraftsAndOnlyExplicitExpiredSubmitScores() = runBlocking {
        val monotonic = MutableMonotonicClock(10_000L)
        val repository = repository(
            TestDefaults(
                questionCount = 1,
                timedMode = true,
                durationMinutes = 1,
                questionSource = TestQuestionSource.ALL,
            ),
            monotonicClock = monotonic,
        )
        val created = repository.createChapterAttempt(
            ChapterTestAttemptRequest("chapter-bca-s1-dbms-01", creationRequestId = "expiry-request"),
        ) as TestAttemptCreationResult.Created
        assertEquals(TestAttemptStartResult.Started, repository.startAttempt(created.attemptId))
        val question = database.testAttemptDao().observeAttemptQuestions(created.attemptId).first().single()
        monotonic.now = 70_000L

        assertEquals(
            TestAnswerSaveResult.Expired,
            repository.saveAnswerDraft(
                created.attemptId,
                TestAnswerDraft(question.id, "pre-expiry draft", false, 60_000L),
            ),
        )
        assertEquals(
            TestSubmissionResult.Expired,
            repository.submitAttempt(
                created.attemptId,
                TestSubmitAction.MANUAL,
                TestAnswerDraft(question.id, "pre-expiry draft", false, 60_000L),
            ),
        )
        assertEquals(TestAttemptStatus.IN_PROGRESS, database.testAttemptDao().getAttempt(created.attemptId)?.status)
        assertEquals(
            TestSubmissionResult.Submitted,
            repository.submitAttempt(
                created.attemptId,
                TestSubmitAction.EXPIRED_CONFIRMATION,
                TestAnswerDraft(question.id, "pre-expiry draft", false, 60_000L),
            ),
        )
        val completed = database.testAttemptDao().getAttempt(created.attemptId)!!
        assertEquals("time_expired", completed.completionReason)
        assertEquals(60L, completed.timeTakenSeconds)
    }

    @Test
    fun resetMonotonicAnchorFailsClosedAsExplicitExpiry() = runBlocking {
        val monotonic = MutableMonotonicClock(10_000L)
        val repository = repository(
            TestDefaults(
                questionCount = 1,
                timedMode = true,
                durationMinutes = 1,
                questionSource = TestQuestionSource.ALL,
            ),
            monotonicClock = monotonic,
        )
        val created = repository.createChapterAttempt(
            ChapterTestAttemptRequest("chapter-bca-s1-dbms-01", creationRequestId = "reboot-request"),
        ) as TestAttemptCreationResult.Created
        assertEquals(TestAttemptStartResult.Started, repository.startAttempt(created.attemptId))
        val question = database.testAttemptDao().observeAttemptQuestions(created.attemptId).first().single()
        monotonic.now = 30_000L
        monotonic.boot = "new-boot"

        assertEquals(
            TestSubmissionResult.Submitted,
            repository.submitAttempt(
                created.attemptId,
                TestSubmitAction.EXPIRED_CONFIRMATION,
                TestAnswerDraft(question.id, "frozen draft", false, 0L),
            ),
        )
        val completed = database.testAttemptDao().getAttempt(created.attemptId)!!
        assertEquals("time_expired", completed.completionReason)
        assertEquals(60L, completed.timeTakenSeconds)
    }

    @Test
    fun evaluatorFailureRollsBackSubmissionClaimAndEvidence() = runBlocking {
        val base = repository(TestDefaults(questionCount = 1, questionSource = TestQuestionSource.ALL))
        val created = base.createChapterAttempt(
            ChapterTestAttemptRequest("chapter-bca-s1-dbms-01", creationRequestId = "rollback-submit"),
        ) as TestAttemptCreationResult.Created
        assertEquals(TestAttemptStartResult.Started, base.startAttempt(created.attemptId))
        val question = database.testAttemptDao().observeAttemptQuestions(created.attemptId).first().single()
        val failingRepository = repository(
            TestDefaults(questionCount = 1, questionSource = TestQuestionSource.ALL),
            answerEvaluator = object : AnswerEvaluator {
                override val version = "test-failure"
                override fun evaluate(
                    questions: List<com.studysmart.app.core.database.TestAttemptQuestionEntity>,
                    answers: List<com.studysmart.app.core.database.TestAnswerEntity>,
                ): TestEvaluation = error("evaluator failure")
            },
        )

        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                failingRepository.submitAttempt(
                    created.attemptId,
                    TestSubmitAction.MANUAL,
                    TestAnswerDraft(question.id, "draft", false, 1_000L),
                )
            }
        }
        val unchanged = database.testAttemptDao().getAttempt(created.attemptId)!!
        assertEquals(TestAttemptStatus.IN_PROGRESS, unchanged.status)
        assertEquals(null, database.testAttemptDao().getAnswerForAttemptQuestion(created.attemptId, question.id)?.normalizedAnswer)
    }

    @Test
    fun emptySourcePoolCreatesNoAttempt() = runBlocking {
        val repository = repository(TestDefaults(questionSource = TestQuestionSource.WEAK))

        val result = repository.createChapterAttempt(
            ChapterTestAttemptRequest(
                chapterId = "chapter-bca-s1-dbms-01",
                creationRequestId = "empty-source-request",
            ),
        )

        assertEquals(TestAttemptCreationResult.NoQuestions(TestQuestionSource.WEAK), result)
        assertEquals(
            0,
            database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM test_attempts").use {
                it.moveToFirst()
                it.getInt(0)
            },
        )
    }

    @Test
    fun childInsertFailureRollsBackTheAttempt() = runBlocking {
        val repository = RoomTestAttemptRepository(
            database = database,
            preferencesRepository = FixedPreferencesRepository(
                TestDefaults(questionSource = TestQuestionSource.ALL),
            ),
            clock = Clock.systemUTC(),
            randomSource = FixedRandomSource,
            idGenerator = CollidingIdGenerator,
            dispatchers = DefaultDispatcherProvider,
        )

        org.junit.Assert.assertThrows(RuntimeException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.createChapterAttempt(
                    ChapterTestAttemptRequest(
                        chapterId = "chapter-bca-s1-dbms-01",
                        creationRequestId = "rollback-request",
                    ),
                )
            }
        }
        assertEquals(
            0,
            database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM test_attempts").use {
                it.moveToFirst()
                it.getInt(0)
            },
        )
    }

    @Test
    fun startupCleanupRemovesTransientAttemptsButRetainsSubmittedAttempts() = runBlocking {
        val repository = repository(TestDefaults(questionSource = TestQuestionSource.ALL))
        val active = repository.createChapterAttempt(
            ChapterTestAttemptRequest("chapter-bca-s1-dbms-01", creationRequestId = "active-request"),
        ) as TestAttemptCreationResult.Created
        val submitted = repository.createChapterAttempt(
            ChapterTestAttemptRequest("chapter-bca-s1-dbms-01", creationRequestId = "submitted-request"),
        ) as TestAttemptCreationResult.Created
        val submittedEntity = database.testAttemptDao().getAttempt(submitted.attemptId)!!
        database.testAttemptDao().updateAttempt(
            submittedEntity.copy(
                status = TestAttemptStatus.SUBMITTED,
                submittedAt = Instant.EPOCH,
                completedAt = Instant.EPOCH,
                score = 0.0,
                maxScore = 2.0,
                percentage = 0.0,
            ),
        )

        RoomStartupCleanupCoordinator(database, DefaultDispatcherProvider).cleanup()

        assertEquals(null, database.testAttemptDao().getAttempt(active.attemptId))
        assertNotNull(database.testAttemptDao().getAttempt(submitted.attemptId))
    }

    private fun repository(
        defaults: TestDefaults,
        monotonicClock: MonotonicClock = FixedMonotonicClock,
        answerEvaluator: AnswerEvaluator = KeywordAnswerEvaluator(),
    ): RoomTestAttemptRepository = RoomTestAttemptRepository(
        database = database,
        preferencesRepository = FixedPreferencesRepository(defaults),
        clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
        randomSource = FixedRandomSource,
        idGenerator = FixedIdGenerator(),
        dispatchers = DefaultDispatcherProvider,
        monotonicClock = monotonicClock,
        answerEvaluator = answerEvaluator,
    )
}

private object FixedMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = 10_000L
    override fun bootId(): String = "test-boot"
}

private class MutableMonotonicClock(var now: Long, var boot: String = "test-boot") : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = now
    override fun bootId(): String = boot
}

private object FixedRandomSource : RandomSource {
    override fun nextInt(until: Int): Int = 0

    override fun nextLong(): Long = 17L
}

private object CollidingIdGenerator : IdGenerator {
    override fun nextId(prefix: String): String = "same-id"
}

private class FixedIdGenerator : IdGenerator {
    private var sequence = 0

    override fun nextId(prefix: String): String = "$prefix-${sequence++}"
}

private class FixedPreferencesRepository(defaults: TestDefaults) : PreferencesRepository {
    private val state = MutableStateFlow(StudyPreferences(testDefaults = defaults))
    override val preferences: Flow<StudyPreferences> = state

    override suspend fun selectCourse(courseId: String) = Unit

    override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) = Unit

    override suspend fun clearSelectedCourse() = Unit

    override suspend fun clearSelectedPeriod() = Unit

    override suspend fun clearSelectedContext() = Unit

    override suspend fun updateTestDefaults(defaults: TestDefaults) {
        state.value = state.value.copy(testDefaults = defaults)
    }
}
