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
import com.studysmart.app.core.database.QuestionKeywordEntity
import com.studysmart.app.core.model.KeywordSnapshotCodec
import com.studysmart.app.core.model.TestAttemptOriginType
import com.studysmart.app.core.model.TestAttemptScopeType
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
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun newTestUsesCurrentDefaultsAndPreservesBaseChapterScopeAcrossConcurrentRetries() = runBlocking {
        val defaults = TestDefaults(questionCount = 2, questionSource = TestQuestionSource.ALL)
        val preferences = FixedPreferencesRepository(defaults)
        val repository = repository(defaults, preferencesRepository = preferences)
        val sourceAttemptId = createSubmittedChapterAttempt(repository, "new-test-source")
        val sourceBefore = database.testAttemptDao().getAttemptWithQuestions(sourceAttemptId)
        preferences.updateTestDefaults(
            TestDefaults(
                questionCount = 2,
                timedMode = true,
                durationMinutes = 12,
                questionSource = TestQuestionSource.WEAK,
            ),
        )
        val request = ResultFollowUpAttemptRequest(sourceAttemptId, "new-test-follow-up")

        val results = kotlinx.coroutines.coroutineScope {
            listOf(
                async { repository.createNewAttemptFromResult(request) },
                async { repository.createNewAttemptFromResult(request) },
            ).map { it.await() }
        }

        assertTrue(results[0] is TestAttemptCreationResult.Created)
        assertEquals(results[0], results[1])
        val followUpId = (results[0] as TestAttemptCreationResult.Created).attemptId
        val followUp = database.testAttemptDao().getAttempt(followUpId)!!
        assertEquals(TestAttemptScopeType.CHAPTER, followUp.scopeType)
        assertEquals("chapter-bca-s1-dbms-01", followUp.scopeReferenceId)
        assertEquals(TestAttemptOriginType.NEW_TEST, followUp.originType)
        assertEquals(sourceAttemptId, followUp.originReferenceId)
        assertEquals(2, followUp.requestedQuestionCount)
        assertEquals(1, followUp.actualQuestionCount)
        assertEquals(TestQuestionSource.WEAK, followUp.questionSource)
        assertEquals(720L, followUp.timeLimitSeconds)
        assertEquals(
            listOf("question-bca-s1-dbms-topic"),
            database.testAttemptDao().observeAttemptQuestions(followUpId).first().map { it.questionId },
        )
        assertEquals(sourceBefore, database.testAttemptDao().getAttemptWithQuestions(sourceAttemptId))
        assertEquals(2, attemptCount())
    }

    @Test
    fun retestWeakUsesOnlyFrozenReviewSubsetFreshActiveSnapshotsAndCurrentTimingDefaults() = runBlocking {
        val initialDefaults = TestDefaults(questionCount = 2, questionSource = TestQuestionSource.ALL)
        val preferences = FixedPreferencesRepository(initialDefaults)
        val repository = repository(initialDefaults, preferencesRepository = preferences)
        val sourceAttemptId = createSubmittedChapterAttempt(
            repository,
            "retest-source",
            directAnswer = "",
        )
        val sourceBefore = database.testAttemptDao().getAttemptWithQuestions(sourceAttemptId)!!
        val sourceQuestions = sourceBefore.questionRows.map { it.question }
        val sourceTopicQuestion = sourceQuestions.single { it.questionId == "question-bca-s1-dbms-topic" }
        database.openHelper.writableDatabase.execSQL(
            "UPDATE questions SET question = ?, answer = ? WHERE id = ?",
            arrayOf("Updated DBMS question", "Updated reference answer", sourceTopicQuestion.questionId),
        )
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM question_keywords WHERE questionId = ?",
            arrayOf(sourceTopicQuestion.questionId),
        )
        database.testAttemptDao().insertQuestionKeywords(
            listOf(QuestionKeywordEntity(sourceTopicQuestion.questionId, "updated keyword", 2.0, 1)),
        )
        preferences.updateTestDefaults(
            TestDefaults(
                questionCount = 1,
                timedMode = true,
                durationMinutes = 15,
                questionSource = TestQuestionSource.IMPORTANT,
            ),
        )
        val request = ResultFollowUpAttemptRequest(sourceAttemptId, "retest-follow-up")

        val first = repository.createRetestWeakAttempt(request) as TestAttemptCreationResult.Created
        val second = repository.createRetestWeakAttempt(request)
        val retest = database.testAttemptDao().getAttempt(first.attemptId)!!
        val retestQuestions = database.testAttemptDao()
            .observeAttemptQuestions(first.attemptId)
            .first()
        val retestQuestionIds = retestQuestions.map { it.questionId }
        val retestQuestion = retestQuestions.single { it.questionId == sourceTopicQuestion.questionId }

        assertEquals(first, second)
        assertEquals(TestAttemptScopeType.RETEST_WEAK, retest.scopeType)
        assertEquals(sourceAttemptId, retest.scopeReferenceId)
        assertEquals(TestAttemptOriginType.RETEST_WEAK, retest.originType)
        assertEquals(sourceAttemptId, retest.originReferenceId)
        assertEquals(2, retest.requestedQuestionCount)
        assertEquals(2, retest.actualQuestionCount)
        assertEquals(TestQuestionSource.ALL, retest.questionSource)
        assertEquals(null, retest.selectionSeed)
        assertEquals(900L, retest.timeLimitSeconds)
        assertEquals(
            listOf("question-bca-s1-dbms-direct", "question-bca-s1-dbms-topic"),
            retestQuestionIds,
        )
        assertEquals("Updated DBMS question", retestQuestion.questionTextSnapshot)
        assertEquals("Updated reference answer", retestQuestion.referenceAnswerSnapshot)
        assertEquals(
            listOf("updated keyword"),
            KeywordSnapshotCodec.decode(retestQuestion.keywordSnapshotJson).map { it.keyword },
        )
        assertEquals(sourceBefore, database.testAttemptDao().getAttemptWithQuestions(sourceAttemptId))
        assertEquals(2, attemptCount())

        assertEquals(TestAttemptStartResult.Started, repository.startAttempt(first.attemptId))
        val retestSubmit = repository.submitAttempt(
            first.attemptId,
            TestSubmitAction.MANUAL,
            TestAnswerDraft(retestQuestions.first().id, "", false, 0L),
        )
        assertEquals(TestSubmissionResult.Submitted, retestSubmit)
        val retestOfRetest = repository.createRetestWeakAttempt(
            ResultFollowUpAttemptRequest(first.attemptId, "retest-of-retest"),
        ) as TestAttemptCreationResult.Created
        val chained = database.testAttemptDao().getAttempt(retestOfRetest.attemptId)!!
        assertEquals(TestAttemptScopeType.RETEST_WEAK, chained.scopeType)
        assertEquals(first.attemptId, chained.scopeReferenceId)
        assertEquals(first.attemptId, chained.originReferenceId)
        assertEquals("chapter-bca-s1-dbms-01", chained.chapterId)

        preferences.updateTestDefaults(
            TestDefaults(questionCount = 2, questionSource = TestQuestionSource.ALL),
        )
        val newTestFromRetest = repository.createNewAttemptFromResult(
            ResultFollowUpAttemptRequest(first.attemptId, "new-test-from-retest"),
        ) as TestAttemptCreationResult.Created
        val chainedNewTest = database.testAttemptDao().getAttempt(newTestFromRetest.attemptId)!!
        assertEquals(TestAttemptScopeType.CHAPTER, chainedNewTest.scopeType)
        assertEquals("chapter-bca-s1-dbms-01", chainedNewTest.scopeReferenceId)
        assertEquals(TestAttemptOriginType.NEW_TEST, chainedNewTest.originType)
        assertEquals(first.attemptId, chainedNewTest.originReferenceId)
    }

    @Test
    fun newTestFromImportantRetainsPeriodScopeAndUsesCurrentDefaults() = runBlocking {
        val initialDefaults = TestDefaults(questionCount = 1, questionSource = TestQuestionSource.IMPORTANT)
        val preferences = FixedPreferencesRepository(initialDefaults)
        val repository = repository(initialDefaults, preferencesRepository = preferences)
        database.testAttemptDao().insertQuestionFlag(
            UserQuestionFlagEntity("local-user", "question-bca-s1-dbms-direct", true, Instant.EPOCH),
        )
        database.testAttemptDao().insertQuestionFlag(
            UserQuestionFlagEntity("local-user", "question-bca-s1-dbms-topic", true, Instant.EPOCH),
        )
        val created = repository.createImportantAttempt(
            ImportantTestAttemptRequest(
                "course-bca",
                "period-bca-semester-1",
                creationRequestId = "important-follow-up-source",
            ),
        ) as TestAttemptCreationResult.Created
        assertEquals(TestAttemptStartResult.Started, repository.startAttempt(created.attemptId))
        val sourceQuestion = database.testAttemptDao()
            .observeAttemptQuestions(created.attemptId).first().single()
        assertEquals(
            TestSubmissionResult.Submitted,
            repository.submitAttempt(
                created.attemptId,
                TestSubmitAction.MANUAL,
                TestAnswerDraft(sourceQuestion.id, "", false, 0L),
            ),
        )
        preferences.updateTestDefaults(TestDefaults(questionCount = 2, questionSource = TestQuestionSource.ALL))

        val newTest = repository.createNewAttemptFromResult(
            ResultFollowUpAttemptRequest(created.attemptId, "new-important-scope"),
        ) as TestAttemptCreationResult.Created
        val attempt = database.testAttemptDao().getAttempt(newTest.attemptId)!!

        assertEquals(TestAttemptScopeType.IMPORTANT, attempt.scopeType)
        assertEquals("period-bca-semester-1", attempt.scopeReferenceId)
        assertEquals("course-bca", attempt.courseId)
        assertEquals("period-bca-semester-1", attempt.academicPeriodId)
        assertEquals(2, attempt.requestedQuestionCount)
        assertEquals(2, attempt.actualQuestionCount)
        assertEquals(TestQuestionSource.ALL, attempt.questionSource)
        assertEquals(
            TestResultOrigin(TestAttemptOriginType.IMPORTANT, null),
            repository.loadAttemptOrigin(newTest.attemptId),
        )
        val resultContext = repository.loadResultActionContext(created.attemptId)!!
        assertEquals(TestAttemptOriginType.IMPORTANT, resultContext.origin.originType)
        assertEquals(null, resultContext.origin.subjectId)
        assertEquals(1, resultContext.activeReviewQuestionCount)
    }

    @Test
    fun resultActionContextFollowsRetestLinksToTheBaseOrigin() = runBlocking {
        val defaults = TestDefaults(questionCount = 2, questionSource = TestQuestionSource.ALL)
        val repository = repository(defaults)
        val source = createSubmittedChapterAttempt(repository, "result-origin-source")

        val sourceContext = repository.loadResultActionContext(source)!!
        assertEquals(TestAttemptOriginType.CHAPTER, sourceContext.origin.originType)
        assertEquals("subject-bca-s1-dbms", sourceContext.origin.subjectId)
        assertEquals(1, sourceContext.activeReviewQuestionCount)

        val retest = repository.createRetestWeakAttempt(
            ResultFollowUpAttemptRequest(source, "result-origin-retest"),
        ) as TestAttemptCreationResult.Created
        assertEquals(
            TestResultOrigin(TestAttemptOriginType.CHAPTER, "subject-bca-s1-dbms"),
            repository.loadAttemptOrigin(retest.attemptId),
        )
        assertEquals(TestAttemptStartResult.Started, repository.startAttempt(retest.attemptId))
        val retestQuestion = database.testAttemptDao()
            .observeAttemptQuestions(retest.attemptId).first().single()
        assertEquals(
            TestSubmissionResult.Submitted,
            repository.submitAttempt(
                retest.attemptId,
                TestSubmitAction.MANUAL,
                TestAnswerDraft(retestQuestion.id, "", false, 0L),
            ),
        )

        val retestContext = repository.loadResultActionContext(retest.attemptId)!!
        assertEquals(TestAttemptOriginType.CHAPTER, retestContext.origin.originType)
        assertEquals("subject-bca-s1-dbms", retestContext.origin.subjectId)
        assertEquals(1, retestContext.activeReviewQuestionCount)
    }

    @Test
    fun retestWeakDoesNotCreateAnAttemptWhenFrozenReviewSubsetIsEmpty() = runBlocking {
        val defaults = TestDefaults(questionCount = 1, questionSource = TestQuestionSource.ALL)
        val repository = repository(defaults)
        val created = repository.createChapterAttempt(
            ChapterTestAttemptRequest("chapter-bca-s1-dbms-01", creationRequestId = "strong-source"),
        ) as TestAttemptCreationResult.Created
        assertEquals(TestAttemptStartResult.Started, repository.startAttempt(created.attemptId))
        val question = database.testAttemptDao().observeAttemptQuestions(created.attemptId).first().single()
        assertEquals(
            TestSubmissionResult.Submitted,
            repository.submitAttempt(
                created.attemptId,
                TestSubmitAction.MANUAL,
                TestAnswerDraft(question.id, "database organised collection", false, 0L),
            ),
        )
        val before = attemptCount()

        val result = repository.createRetestWeakAttempt(
            ResultFollowUpAttemptRequest(created.attemptId, "empty-retest"),
        )

        assertEquals(TestAttemptCreationResult.NoQuestions(TestQuestionSource.ALL), result)
        assertEquals(before, attemptCount())
    }

    @Test
    fun followUpRejectsMissingForeignNonSubmittedCorruptAndCyclicSources() = runBlocking {
        val defaults = TestDefaults(questionCount = 1, questionSource = TestQuestionSource.ALL)
        val repository = repository(defaults)
        assertEquals(
            TestAttemptCreationResult.MissingSource,
            repository.createNewAttemptFromResult(ResultFollowUpAttemptRequest("absent", "missing-source")),
        )
        assertNull(repository.loadAttemptOrigin("absent"))

        val notSubmitted = repository.createChapterAttempt(
            ChapterTestAttemptRequest("chapter-bca-s1-dbms-01", creationRequestId = "not-submitted-source"),
        ) as TestAttemptCreationResult.Created
        assertEquals(
            TestAttemptCreationResult.MissingSource,
            repository.createNewAttemptFromResult(
                ResultFollowUpAttemptRequest(notSubmitted.attemptId, "non-submitted-follow-up"),
            ),
        )

        val foreign = createSubmittedChapterAttempt(repository, "foreign-source")
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO users(id, displayName, createdAt, updatedAt) VALUES (?, ?, ?, ?)",
            arrayOf<Any>("foreign-user", "Foreign", 0L, 0L),
        )
        val foreignAttempt = database.testAttemptDao().getAttempt(foreign)!!
        database.testAttemptDao().updateAttempt(foreignAttempt.copy(userId = "foreign-user"))
        assertNull(repository.loadAttemptOrigin(foreign))
        assertEquals(
            TestAttemptCreationResult.MissingSource,
            repository.createNewAttemptFromResult(ResultFollowUpAttemptRequest(foreign, "foreign-follow-up")),
        )
        val localParent = createSubmittedChapterAttempt(repository, "local-parent-with-foreign-link")
        val localChild = repository.createNewAttemptFromResult(
            ResultFollowUpAttemptRequest(localParent, "child-with-foreign-link"),
        ) as TestAttemptCreationResult.Created
        val localChildAttempt = database.testAttemptDao().getAttempt(localChild.attemptId)!!
        database.testAttemptDao().updateAttempt(localChildAttempt.copy(originReferenceId = foreign))
        assertNull(repository.loadAttemptOrigin(localChild.attemptId))

        val brokenLink = createSubmittedChapterAttempt(repository, "broken-link-source")
        val brokenLinkAttempt = database.testAttemptDao().getAttempt(brokenLink)!!
        database.testAttemptDao().updateAttempt(
            brokenLinkAttempt.copy(
                originType = TestAttemptOriginType.NEW_TEST,
                originReferenceId = "missing-linked-attempt",
            ),
        )
        assertNull(repository.loadAttemptOrigin(brokenLink))
        assertEquals(
            TestAttemptCreationResult.MissingSource,
            repository.createNewAttemptFromResult(
                ResultFollowUpAttemptRequest(brokenLink, "broken-link-follow-up"),
            ),
        )

        val nonSubmittedParent = createSubmittedChapterAttempt(repository, "navigation-parent")
        val child = repository.createNewAttemptFromResult(
            ResultFollowUpAttemptRequest(nonSubmittedParent, "navigation-child"),
        ) as TestAttemptCreationResult.Created
        val parentAttempt = database.testAttemptDao().getAttempt(nonSubmittedParent)!!
        database.testAttemptDao().updateAttempt(parentAttempt.copy(status = TestAttemptStatus.IN_PROGRESS))
        assertNull(repository.loadAttemptOrigin(child.attemptId))

        val corrupt = createSubmittedChapterAttempt(repository, "corrupt-source")
        database.openHelper.writableDatabase.execSQL(
            "UPDATE test_answers SET matchedKeywordsJson = '{}' WHERE testAttemptQuestionId IN " +
                "(SELECT id FROM test_attempt_questions WHERE testAttemptId = ? LIMIT 1)",
            arrayOf(corrupt),
        )
        assertEquals(
            TestAttemptCreationResult.CorruptSource,
            repository.createRetestWeakAttempt(ResultFollowUpAttemptRequest(corrupt, "corrupt-follow-up")),
        )

        val cyclic = createSubmittedChapterAttempt(repository, "cyclic-source")
        val cyclicAttempt = database.testAttemptDao().getAttempt(cyclic)!!
        database.testAttemptDao().updateAttempt(
            cyclicAttempt.copy(
                originType = TestAttemptOriginType.NEW_TEST,
                originReferenceId = cyclic,
            ),
        )
        assertNull(repository.loadAttemptOrigin(cyclic))
        assertEquals(
            TestAttemptCreationResult.MissingSource,
            repository.createNewAttemptFromResult(ResultFollowUpAttemptRequest(cyclic, "cyclic-follow-up")),
        )
    }

    @Test
    fun followUpSnapshotInsertFailureRollsBackAndSameRequestCanRetry() = runBlocking {
        val defaults = TestDefaults(questionCount = 2, questionSource = TestQuestionSource.ALL)
        val repository = repository(defaults)
        val source = createSubmittedChapterAttempt(repository, "rollback-source")
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_follow_up_snapshot BEFORE INSERT ON test_attempt_questions " +
                "WHEN (SELECT creationRequestId FROM test_attempts WHERE id = NEW.testAttemptId) = " +
                "'rollback-follow-up' BEGIN SELECT RAISE(ABORT, 'snapshot insert failure'); END",
        )
        var failed = false
        try {
            repository.createNewAttemptFromResult(
                ResultFollowUpAttemptRequest(source, "rollback-follow-up"),
            )
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(null, database.testAttemptDao().getAttemptByCreationRequestId("rollback-follow-up"))
        assertEquals(1, attemptCount())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_follow_up_snapshot")

        val retried = repository.createNewAttemptFromResult(
            ResultFollowUpAttemptRequest(source, "rollback-follow-up"),
        ) as TestAttemptCreationResult.Created
        assertEquals(
            retried,
            repository.createNewAttemptFromResult(ResultFollowUpAttemptRequest(source, "rollback-follow-up")),
        )
        assertEquals(2, attemptCount())
    }

    @Test
    fun retestWeakExcludesQuestionsWhoseCurrentCurriculumIsInactive() = runBlocking {
        val defaults = TestDefaults(questionCount = 2, questionSource = TestQuestionSource.ALL)
        val repository = repository(defaults)
        val source = createSubmittedChapterAttempt(repository, "inactive-retest-source")
        database.openHelper.writableDatabase.execSQL(
            "UPDATE questions SET isActive = 0 WHERE id = ?",
            arrayOf("question-bca-s1-dbms-topic"),
        )
        val before = attemptCount()

        val result = repository.createRetestWeakAttempt(
            ResultFollowUpAttemptRequest(source, "inactive-retest"),
        )

        assertEquals(TestAttemptCreationResult.NoQuestions(TestQuestionSource.ALL), result)
        assertEquals(before, attemptCount())
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

    private suspend fun createSubmittedChapterAttempt(
        repository: RoomTestAttemptRepository,
        creationRequestId: String,
        directAnswer: String = "Database is an organised collection",
    ): String {
        val created = repository.createChapterAttempt(
            ChapterTestAttemptRequest(
                chapterId = "chapter-bca-s1-dbms-01",
                creationRequestId = creationRequestId,
            ),
        ) as TestAttemptCreationResult.Created
        assertEquals(TestAttemptStartResult.Started, repository.startAttempt(created.attemptId))
        val questions = database.testAttemptDao().observeAttemptQuestions(created.attemptId).first()
        val directQuestion = questions.single { it.questionId == "question-bca-s1-dbms-direct" }
        assertEquals(
            TestSubmissionResult.Submitted,
            repository.submitAttempt(
                created.attemptId,
                TestSubmitAction.MANUAL,
                TestAnswerDraft(directQuestion.id, directAnswer, false, 0L),
            ),
        )
        return created.attemptId
    }

    private fun attemptCount(): Int = database.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM test_attempts")
        .use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun repository(
        defaults: TestDefaults,
        monotonicClock: MonotonicClock = FixedMonotonicClock,
        answerEvaluator: AnswerEvaluator = KeywordAnswerEvaluator(),
        preferencesRepository: PreferencesRepository = FixedPreferencesRepository(defaults),
    ): RoomTestAttemptRepository = RoomTestAttemptRepository(
        database = database,
        preferencesRepository = preferencesRepository,
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
