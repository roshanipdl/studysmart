package com.studysmart.app.feature.test

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.common.DefaultDispatcherProvider
import com.studysmart.app.core.common.IdGenerator
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
    fun emptySourcePoolCreatesNoAttempt() = runBlocking {
        val repository = repository(TestDefaults(questionSource = TestQuestionSource.WEAK))

        val result = repository.createChapterAttempt(
            ChapterTestAttemptRequest(
                chapterId = "chapter-bca-s1-dbms-01",
                creationRequestId = "empty-source-request",
            ),
        )

        assertEquals(TestAttemptCreationResult.NoQuestions, result)
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

    private fun repository(defaults: TestDefaults): RoomTestAttemptRepository = RoomTestAttemptRepository(
        database = database,
        preferencesRepository = FixedPreferencesRepository(defaults),
        clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
        randomSource = FixedRandomSource,
        idGenerator = FixedIdGenerator(),
        dispatchers = DefaultDispatcherProvider,
    )
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
