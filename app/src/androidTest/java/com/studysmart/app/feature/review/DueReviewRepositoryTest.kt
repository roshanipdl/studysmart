package com.studysmart.app.feature.review

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.common.RandomSource
import com.studysmart.app.core.database.ReviewScheduleEntity
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.model.LearningState
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.feature.preparation.DueReviewSessionRequest
import com.studysmart.app.feature.preparation.PreparationCreationResult
import com.studysmart.app.feature.preparation.RoomPreparationRepository
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZoneId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DueReviewRepositoryTest {
    private lateinit var database: StudySmartDatabase
    private lateinit var repository: RoomPreparationRepository
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    @Before
    fun setUp() {
        database = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext())
        repository = RoomPreparationRepository(
            database = database,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            randomSource = FixedRandomSource(),
            idGenerator = FixedIdGenerator(),
            dispatchers = UnconfinedDispatcherProvider,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun dueCandidatesStayInSelectedContextAndUseMostOverdueOrder() = runBlocking {
        schedule("question-bca-s1-dbms-direct", now.minusSeconds(300))
        schedule("question-bca-s1-dbms-topic", now.minusSeconds(300))
        schedule("question-bca-s1-web-01", now)
        schedule("question-bca-s5-ai-01", now.minusSeconds(3_600))
        schedule("question-year1-foundations-01", now.minusSeconds(7_200))

        val rows = database.preparationDao()
            .getDueCandidates(
                userId = "local-user",
                courseId = "course-bca",
                academicPeriodId = "period-bca-semester-1",
                now = now,
            )

        assertEquals(
            listOf(
                "question-bca-s1-dbms-direct",
                "question-bca-s1-dbms-topic",
                "question-bca-s1-web-01",
            ),
            rows.map { it.question.id },
        )
        assertTrue(rows.all { it.nextReviewAt != null && it.nextReviewAt <= now })
    }

    @Test
    fun dueSummaryCountsExactDueAndOverdueQuestions() = runBlocking {
        schedule("question-bca-s1-dbms-direct", now.minusSeconds(60))
        schedule("question-bca-s1-dbms-topic", now)
        schedule("question-bca-s1-web-01", now.plusSeconds(1))

        val summary = RoomReviewRepository(database.preparationDao(), Clock.fixed(now, ZoneOffset.UTC)).observeDueReview(
            courseId = "course-bca",
            academicPeriodId = "period-bca-semester-1",
        ).first()

        assertEquals(2, summary.dueCount)
        assertEquals(1, summary.overdueCount)
    }

    @Test
    fun dueSummaryRefreshesAtExactBoundaryAndAfterBackwardClockChanges() = runBlocking {
        withContext(Dispatchers.IO) {
            schedule("question-bca-s1-dbms-direct", now.plusSeconds(1))
        }
        val clock = MutableReviewClock(now)
        val summaries = Channel<DueReviewSummary>(Channel.UNLIMITED)
        val job = launch {
            RoomReviewRepository(database.preparationDao(), clock, clockRefreshMillis = 100L)
                .observeDueReview("course-bca", "period-bca-semester-1")
                .collect { summaries.send(it) }
        }
        try {
        assertEquals(DueReviewSummary(0, 0), withTimeout(5_000L) { summaries.receive() })
        clock.current = now.plusSeconds(1)
        assertEquals(DueReviewSummary(1, 0), withTimeout(5_000L) { summaries.receive() })
        clock.current = now.plusSeconds(2)
        assertEquals(DueReviewSummary(1, 1), withTimeout(5_000L) { summaries.receive() })
        clock.current = now
        assertEquals(DueReviewSummary(0, 0), withTimeout(5_000L) { summaries.receive() })
        } finally {
            job.cancel()
        }
    }

    @Test
    fun dueSummaryRejectsNonPositiveRefreshIntervals() {
        listOf(0L, -1L).forEach { interval ->
            val failure = runCatching {
                RoomReviewRepository(database.preparationDao(), Clock.fixed(now, ZoneOffset.UTC), clockRefreshMillis = interval)
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
        }
    }

    @Test
    fun dueSessionPersistsFixedOrderedSnapshotsAndIsIdempotent() = runBlocking {
        schedule("question-bca-s1-dbms-direct", now.minusSeconds(300))
        schedule("question-bca-s1-dbms-topic", now.minusSeconds(300))
        schedule("question-bca-s1-web-01", now.plusSeconds(1))

        val request = DueReviewSessionRequest(
            courseId = "course-bca",
            academicPeriodId = "period-bca-semester-1",
            creationRequestId = "due-request-1",
        )
        val first = repository.createDueReviewSession(request)
        val second = repository.createDueReviewSession(request)
        val sessionId = (first as PreparationCreationResult.Created).sessionId

        assertEquals(first, second)
        val entity = database.preparationDao().getSession(sessionId)!!
        assertEquals(PreparationScopeType.DUE_REVIEW, entity.scopeType)
        assertEquals(PreparationOriginType.REVIEW, entity.originType)
        assertEquals("course-bca", entity.courseId)
        assertEquals("period-bca-semester-1", entity.academicPeriodId)
        assertEquals(2, entity.uniqueQuestionCount)
        assertEquals(
            listOf("question-bca-s1-dbms-direct", "question-bca-s1-dbms-topic"),
            database.preparationDao().getSessionItems(sessionId).map { it.questionId },
        )

        val session = repository.observeSession(sessionId).first()!!
        assertNull(session.context.subjectId)
        assertNull(session.context.chapterId)
        assertEquals("BCA", session.context.courseCode)
        assertEquals("Semester 1", session.context.academicPeriodName)
    }

    @Test
    fun inactiveTopicIsExcludedFromDuePool() = runBlocking {
        schedule("question-bca-s1-dbms-direct", now.minusSeconds(60))
        schedule("question-bca-s1-dbms-topic", now.minusSeconds(60))
        database.openHelper.writableDatabase.execSQL(
            "UPDATE topics SET isActive = 0 WHERE id = 'topic-bca-s1-dbms-01'",
        )

        val rows = database.preparationDao().getDueCandidates(
            userId = "local-user",
            courseId = "course-bca",
            academicPeriodId = "period-bca-semester-1",
            now = now,
        )

        assertEquals(listOf("question-bca-s1-dbms-direct"), rows.map { it.question.id })
    }

    @Test
    fun futureOnlyPoolDoesNotCreateEmptySession() = runBlocking {
        schedule("question-bca-s1-dbms-direct", now.plusSeconds(60))

        val result = repository.createDueReviewSession(
            DueReviewSessionRequest(
                courseId = "course-bca",
                academicPeriodId = "period-bca-semester-1",
                creationRequestId = "due-request-empty",
            ),
        )

        assertEquals(PreparationCreationResult.NoQuestions, result)
        assertFalse(database.preparationDao().getSessionByCreationRequestId("due-request-empty") != null)
    }

    private suspend fun schedule(questionId: String, nextReviewAt: Instant) {
        database.preparationDao().upsertReviewSchedule(
            ReviewScheduleEntity(
                userId = "local-user",
                questionId = questionId,
                learningState = LearningState.LEARNING,
                lastReviewedAt = now.minusSeconds(600),
                nextReviewAt = nextReviewAt,
                intervalSeconds = 600,
                repetitionCount = 1,
                lapseCount = 0,
            ),
        )
    }
}

private class MutableReviewClock(var current: Instant) : Clock() {
    override fun instant(): Instant = current
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
}

private object UnconfinedDispatcherProvider : DispatcherProvider {
    override val io = Dispatchers.Unconfined
    override val main = Dispatchers.Unconfined
}

private class FixedRandomSource : RandomSource {
    override fun nextInt(until: Int): Int = 0

    override fun nextLong(): Long = 42L
}

private class FixedIdGenerator : IdGenerator {
    private var counter = 0

    override fun nextId(prefix: String): String = "$prefix-${counter++}"
}
