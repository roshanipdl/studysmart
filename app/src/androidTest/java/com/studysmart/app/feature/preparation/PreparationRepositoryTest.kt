package com.studysmart.app.feature.preparation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.studysmart.app.app.RoomStartupCleanupCoordinator
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.common.RandomSource
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.core.model.PreparationSessionStatus
import com.studysmart.app.core.model.Rating
import com.studysmart.app.feature.review.DeterministicReviewScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreparationRepositoryTest {
    private lateinit var database: StudySmartDatabase
    private lateinit var repository: RoomPreparationRepository
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    @Before
    fun setUp() {
        database = com.studysmart.app.testing.createInMemoryStudySmartDatabase(
            ApplicationProvider.getApplicationContext(),
        )
        repository = RoomPreparationRepository(
            database = database,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            randomSource = FixedRandomSource(42L),
            idGenerator = FixedIdGenerator(),
            dispatchers = UnconfinedDispatcherProvider,
            reviewScheduler = DeterministicReviewScheduler(Clock.fixed(now, ZoneOffset.UTC)),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun chapterCoverageUsesCommittedDistinctQuestionsAndActiveEligibility() = runBlocking {
        val chapter = "chapter-bca-s1-dbms-01"
        assertEquals(PreparationChapterCoverage(2, 0), repository.getChapterCoverage(chapter))
        val sessionId = createSession("coverage")
        var index = 0
        while (true) {
            val session = repository.observeSession(sessionId).first()!!
            val item = session.currentItem ?: break
            repository.revealCurrent(sessionId, item.questionId)
            repository.rateCurrent(PreparationRatingRequest(sessionId, item.questionId, item.presentationCount,
                Rating.GOOD, "coverage-${index++}"))
        }
        assertEquals(PreparationChapterCoverage(2, 2), repository.getChapterCoverage(chapter))
        assertNull(repository.getChapterCoverage("missing"))
        database.openHelper.writableDatabase.execSQL("UPDATE questions SET isActive = 0 WHERE chapterId = ?", arrayOf(chapter))
        assertEquals(PreparationChapterCoverage(0, 0), repository.getChapterCoverage(chapter))
    }

    @Test
    fun chapterCandidatesKeepReviewsForBothQuestionsWhenTimestampsDiffer() = runBlocking {
        val sessionId = createSession("coverage-different-times")
        val item = repository.observeSession(sessionId).first()!!.currentItem!!
        repository.revealCurrent(sessionId, item.questionId)
        repository.rateCurrent(PreparationRatingRequest(sessionId, item.questionId, item.presentationCount,
            Rating.GOOD, "coverage-earlier"))
        val earlier = database.preparationDao().getReviewRecord("coverage-earlier")!!
        val otherId = if (item.questionId == "question-bca-s1-dbms-direct")
            "question-bca-s1-dbms-topic" else "question-bca-s1-dbms-direct"
        database.preparationDao().insertReviewRecord(earlier.copy(
            id = "coverage-later", questionId = otherId, preparationSessionId = null,
            reviewedAt = earlier.reviewedAt.plusSeconds(10), rating = Rating.EASY,
        ))
        val rows = database.preparationDao().getChapterCandidates("local-user", "chapter-bca-s1-dbms-01")
        assertEquals(2, rows.count { it.hasReview })
        assertEquals(Rating.GOOD, rows.single { it.question.id == item.questionId }.latestRating)
        assertEquals(Rating.EASY, rows.single { it.question.id == otherId }.latestRating)
        assertEquals(0, database.preparationDao().getChapterCandidates("another-user", "chapter-bca-s1-dbms-01").count { it.hasReview })
    }

    @Test
    fun sessionCreationPersistsSnapshotsAndIsIdempotent() = runBlocking {
        val request = chapterRequest("request-1")
        val first = repository.createSession(request)
        val second = repository.createSession(request)

        val firstId = (first as PreparationCreationResult.Created).sessionId
        val secondId = (second as PreparationCreationResult.Created).sessionId
        assertEquals(firstId, secondId)

        val session = repository.observeSession(firstId).first()
        assertNotNull(session)
        assertEquals(2, session!!.uniqueQuestionCount)
        assertEquals(2, session.items.size)
        assertEquals(
            setOf("What is a database?", "What is a DBMS?"),
            session.items.map { it.questionSnapshot }.toSet(),
        )
    }

    @Test
    fun sessionCreationRollsBackWhenChildInsertFails() = runBlocking {
        val databaseHandle = database.openHelper.writableDatabase
        databaseHandle.execSQL(
            "CREATE TRIGGER fail_preparation_item_insert " +
                "BEFORE INSERT ON preparation_session_items BEGIN " +
                "SELECT RAISE(ABORT, 'forced child insert failure'); END",
        )

        var failed = false
        try {
            repository.createSession(chapterRequest("request-rollback"))
        } catch (_: Exception) {
            failed = true
        } finally {
            databaseHandle.execSQL("DROP TRIGGER fail_preparation_item_insert")
        }

        assertTrue(failed)
        assertNull(database.preparationDao().getSessionByCreationRequestId("request-rollback"))
    }

    @Test
    fun ratingCreatesHistoryScheduleAndCompletesTheSession() = runBlocking {
        val sessionId = createSession("request-2")
        var session = repository.observeSession(sessionId).first()!!

        repository.revealCurrent(sessionId, session.currentItem!!.questionId)
        repository.rateCurrent(
            PreparationRatingRequest(
                sessionId = sessionId,
                questionId = session.currentItem!!.questionId,
                expectedPresentationCount = 0,
                rating = Rating.GOOD,
                reviewRecordId = "review-1",
            ),
        )
        session = repository.observeSession(sessionId).first()!!
        assertEquals(1, session.reviewCount)
        assertTrue(session.currentItem != null)

        repository.revealCurrent(sessionId, session.currentItem!!.questionId)
        repository.rateCurrent(
            PreparationRatingRequest(
                sessionId = sessionId,
                questionId = session.currentItem!!.questionId,
                expectedPresentationCount = 0,
                rating = Rating.EASY,
                reviewRecordId = "review-2",
            ),
        )
        session = repository.observeSession(sessionId).first()!!
        assertEquals(PreparationSessionStatus.COMPLETED, session.status)
        assertEquals(2, session.reviewEvents.size)
        assertEquals(2, database.preparationDao().getSessionReviewRecords(sessionId).size)
        assertEquals(2, database.preparationDao().getSessionItems(sessionId).count { it.isCompleted })
        assertNotNull(database.preparationDao().getReviewSchedule("local-user", session.reviewEvents.first().questionId))
    }

    @Test
    fun repeatedPresentationsCreateSeparateHistoryRecords() = runBlocking {
        val sessionId = createSession("request-repeated")
        var session = repository.observeSession(sessionId).first()!!
        val repeatedQuestionId = session.currentItem!!.questionId

        repository.revealCurrent(sessionId, repeatedQuestionId)
        repository.rateCurrent(
            PreparationRatingRequest(
                sessionId = sessionId,
                questionId = repeatedQuestionId,
                expectedPresentationCount = 0,
                rating = Rating.AGAIN,
                reviewRecordId = "review-repeated-1",
            ),
        )

        session = repository.observeSession(sessionId).first()!!
        val secondQuestionId = session.currentItem!!.questionId
        repository.revealCurrent(sessionId, secondQuestionId)
        repository.rateCurrent(
            PreparationRatingRequest(
                sessionId = sessionId,
                questionId = secondQuestionId,
                expectedPresentationCount = 0,
                rating = Rating.GOOD,
                reviewRecordId = "review-repeated-2",
            ),
        )

        session = repository.observeSession(sessionId).first()!!
        assertEquals(repeatedQuestionId, session.currentItem!!.questionId)
        repository.revealCurrent(sessionId, repeatedQuestionId)
        repository.rateCurrent(
            PreparationRatingRequest(
                sessionId = sessionId,
                questionId = repeatedQuestionId,
                expectedPresentationCount = 1,
                rating = Rating.GOOD,
                reviewRecordId = "review-repeated-3",
            ),
        )

        assertEquals(
            2,
            database.preparationDao()
                .getSessionReviewRecords(sessionId)
                .count { it.questionId == repeatedQuestionId },
        )
    }

    @Test
    fun discardDeletesActiveRowsButRetainsCommittedHistory() = runBlocking {
        val sessionId = createSession("request-3")
        val initial = repository.observeSession(sessionId).first()!!
        val questionId = initial.currentItem!!.questionId
        repository.revealCurrent(sessionId, questionId)
        repository.rateCurrent(
            PreparationRatingRequest(
                sessionId = sessionId,
                questionId = questionId,
                expectedPresentationCount = 0,
                rating = Rating.AGAIN,
                reviewRecordId = "review-3",
            ),
        )

        assertTrue(repository.discardSession(sessionId))
        assertNull(database.preparationDao().getSession(sessionId))
        val record = database.preparationDao().getReviewRecord("review-3")
        assertNotNull(record)
        assertNull(record!!.preparationSessionId)
        assertNotNull(database.preparationDao().getReviewSchedule("local-user", questionId))
    }

    @Test
    fun startupCleanupRemovesOnlyActiveSessions() = runBlocking {
        val completedSessionId = createSession("request-completed")
        var completedSession = repository.observeSession(completedSessionId).first()!!
        repeat(2) { index ->
            val item = completedSession.currentItem!!
            repository.revealCurrent(completedSessionId, item.questionId)
            repository.rateCurrent(
                PreparationRatingRequest(
                    sessionId = completedSessionId,
                    questionId = item.questionId,
                    expectedPresentationCount = item.presentationCount,
                    rating = Rating.GOOD,
                    reviewRecordId = "review-completed-$index",
                ),
            )
            completedSession = repository.observeSession(completedSessionId).first()!!
        }
        val activeSessionId = createSession("request-active")
        RoomStartupCleanupCoordinator(database, UnconfinedDispatcherProvider).cleanup()

        assertNull(database.preparationDao().getSession(activeSessionId))
        assertNotNull(database.preparationDao().getSession(completedSessionId))
        assertEquals(PreparationSessionStatus.COMPLETED, completedSession.status)
        assertNotNull(database.preparationDao().getReviewRecord("review-completed-0"))
    }

    private suspend fun createSession(requestId: String): String =
        (repository.createSession(chapterRequest(requestId)) as PreparationCreationResult.Created).sessionId

    private fun chapterRequest(requestId: String): PreparationSessionRequest = PreparationSessionRequest(
        scopeType = PreparationScopeType.CHAPTER,
        scopeId = "chapter-bca-s1-dbms-01",
        originType = PreparationOriginType.CHAPTER,
        originReferenceId = "chapter-bca-s1-dbms-01",
        creationRequestId = requestId,
    )
}

private object UnconfinedDispatcherProvider : DispatcherProvider {
    override val io = Dispatchers.Unconfined
    override val main = Dispatchers.Unconfined
}

private class FixedRandomSource(private val value: Long) : RandomSource {
    override fun nextInt(until: Int): Int = 0

    override fun nextLong(): Long = value
}

private class FixedIdGenerator : IdGenerator {
    private var counter = 0

    override fun nextId(prefix: String): String = "$prefix-${counter++}"
}
