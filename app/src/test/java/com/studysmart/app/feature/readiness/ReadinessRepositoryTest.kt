package com.studysmart.app.feature.readiness

import com.studysmart.app.core.database.ReadinessDao
import com.studysmart.app.core.database.ReadinessQuestionRow
import com.studysmart.app.core.database.ReadinessTestAnswerRow
import com.studysmart.app.core.model.LearningState
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadinessRepositoryTest {
    @Test
    fun estimateRefreshesAsTheClockCrossesOverdueDayBoundariesWithoutDatabaseChanges() = runTest {
        val reviewedAt = Instant.parse("2026-09-28T12:00:00Z")
        val dueAt = Instant.parse("2026-09-29T12:00:00Z")
        val clock = MutableClock(dueAt)
        val dao = FakeReadinessDao(
            questions = listOf(
                ReadinessQuestionRow(
                    questionId = "question-1",
                    learningState = LearningState.LEARNING,
                    lastReviewedAt = reviewedAt,
                    nextReviewAt = dueAt,
                    hasCommittedRating = true,
                    latestTestSubmittedAt = null,
                    latestTestRequiresReview = null,
                ),
            ),
        )
        val repository = RoomReadinessRepository(
            dao = dao,
            clock = clock,
            clockRefreshMillis = 1_000L,
            calculationDispatcher = StandardTestDispatcher(testScheduler),
        )
        val estimates = mutableListOf<ReadinessEstimate?>()
        val job = backgroundScope.launch {
            repository.observeReadiness(ReadinessScope("course-1", "period-1"))
                .take(2)
                .toList(estimates)
        }

        runCurrent()
        assertEquals(100.0, estimates.single()?.freshnessScore ?: -1.0, 0.0)

        clock.currentInstant = dueAt.plusSeconds(86_400L)
        advanceTimeBy(1_000L)
        runCurrent()
        job.join()

        assertEquals(2, estimates.size)
        assertEquals(90.0, estimates.last()?.freshnessScore ?: -1.0, 0.0)
        assertEquals(44.0, estimates.last()?.score ?: -1.0, 0.0)
    }
}

private class FakeReadinessDao(
    questions: List<ReadinessQuestionRow>,
) : ReadinessDao {
    private val questionRows = MutableStateFlow(questions)
    private val answerRows = MutableStateFlow(emptyList<ReadinessTestAnswerRow>())

    override fun observeQuestions(
        userId: String,
        courseId: String,
        academicPeriodId: String?,
        subjectId: String?,
        chapterId: String?,
    ): Flow<List<ReadinessQuestionRow>> = questionRows

    override fun observeSubmittedAnswers(
        userId: String,
        courseId: String,
        academicPeriodId: String?,
        subjectId: String?,
        chapterId: String?,
    ): Flow<List<ReadinessTestAnswerRow>> = answerRows
}

private class MutableClock(
    var currentInstant: Instant,
) : Clock() {
    override fun instant(): Instant = currentInstant
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}
