package com.studysmart.app.feature.progress

import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.common.*
import com.studysmart.app.core.database.*
import com.studysmart.app.core.model.Rating
import com.studysmart.app.feature.test.*
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Test-owned database only; no production preferences, time-zone changes, or clearing. */
class ProgressRepositoryTest {
    private var database: StudySmartDatabase? = null
    private val time = MutableStateFlow(StudyTime(Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("UTC")))
    private fun graph() = ApplicationProvider.getApplicationContext<android.content.Context>().let { context ->
        coreAlphaTestContainer(context, createInMemoryStudySmartDatabase(context).also { database = it })
    }
    private fun repository() = RoomProgressRepository(database!!.progressDao(), database!!.readinessDao(), database!!.testAttemptDao(), object : StudyTimeSource {
        override fun observeTime() = time
        override fun refresh() = Unit
    })

    @Test fun uniqueDailyRatingsExcludeRepeatedEventsOtherPeriodsAndEndBoundary() = runBlocking {
        graph()
        val db = database!!
        val day = time.value.day
        suspend fun rate(id: String, question: String, at: Instant) = db.preparationDao().insertReviewRecord(
            ReviewRecordEntity(id, "local-user", null, question, Rating.GOOD, null, at))
        rate("start", "question-bca-s1-dbms-direct", day.start)
        rate("repeat", "question-bca-s1-dbms-direct", day.start.plusSeconds(1))
        rate("end", "question-bca-s1-dbms-topic", day.end)
        rate("other-period", "question-bca-s5-ai-01", day.start)
        val snapshot = repository().observeProgress("course-bca", "period-bca-semester-1").first()
        assertEquals(1, snapshot.metrics.dailyRatedCount)
        assertEquals(2, snapshot.metrics.studiedCount)
        assertTrue(snapshot.metrics.hasActivity)
        db.openHelper.writableDatabase.execSQL("UPDATE questions SET isActive=0 WHERE id='question-bca-s1-dbms-direct'")
        assertEquals(0, repository().observeProgress("course-bca", "period-bca-semester-1").first().metrics.dailyRatedCount)
    }

    @Test fun testOnlyHistoryIsActivityButDoesNotCountDailyCardsOrCoverage() = runBlocking {
        val graph = graph()
        val created = graph.testAttemptRepository.createChapterAttempt(ChapterTestAttemptRequest("chapter-bca-s1-dbms-01", creationRequestId = "test")) as TestAttemptCreationResult.Created
        graph.testAttemptRepository.startAttempt(created.attemptId)
        graph.testAttemptRepository.submitAttempt(created.attemptId, TestSubmitAction.MANUAL, null)
        val snapshot = repository().observeProgress("course-bca", "period-bca-semester-1").first()
        assertTrue(snapshot.metrics.hasActivity)
        assertEquals(0, snapshot.metrics.studiedCount)
        assertEquals(0, snapshot.metrics.dailyRatedCount)
        assertNotNull(snapshot.latestTest)
    }

    @Test fun activeAttemptIsNotProgressActivity() = runBlocking {
        val graph = graph()
        graph.testAttemptRepository.createChapterAttempt(ChapterTestAttemptRequest("chapter-bca-s1-dbms-01", creationRequestId = "active"))
        val snapshot = repository().observeProgress("course-bca", "period-bca-semester-1").first()
        assertFalse(snapshot.metrics.hasActivity)
        assertNull(snapshot.latestTest)
    }

    @After fun close() { database?.close() }
}
