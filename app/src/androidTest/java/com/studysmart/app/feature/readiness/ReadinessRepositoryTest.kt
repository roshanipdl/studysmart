package com.studysmart.app.feature.readiness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.common.DefaultDispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.common.MonotonicClock
import com.studysmart.app.core.common.RandomSource
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.database.ReviewRecordEntity
import com.studysmart.app.core.database.ReviewScheduleEntity
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.model.LearningState
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.TestQuestionSource
import com.studysmart.app.feature.test.ChapterTestAttemptRequest
import com.studysmart.app.feature.test.KeywordAnswerEvaluator
import com.studysmart.app.feature.test.RoomTestAttemptRepository
import com.studysmart.app.feature.test.TestAnswerDraft
import com.studysmart.app.feature.test.TestAttemptCreationResult
import com.studysmart.app.feature.test.TestSubmitAction
import com.studysmart.app.feature.test.TestSubmissionResult
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

class ReadinessRepositoryTest {
    private lateinit var database: StudySmartDatabase
    private val now = Instant.parse("2026-09-30T12:00:00Z")

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
    fun readinessQueryCountsActiveScopeQuestionsAndObservesSubmittedTestOverlay() = runBlocking {
        val scope = ReadinessScope(
            courseId = "course-bca",
            academicPeriodId = "period-bca-semester-1",
            chapterId = "chapter-bca-s1-dbms-01",
        )
        val readinessRepository = RoomReadinessRepository(
            dao = database.readinessDao(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val courseEstimate = readinessRepository.observeReadiness(
            ReadinessScope(courseId = "course-bca"),
        ).first()
        assertEquals(5, courseEstimate?.activeQuestionCount)
        val initial = readinessRepository.observeReadiness(scope).first()
        assertNotNull(initial)
        assertEquals(2, initial?.activeQuestionCount)
        assertEquals(0.0, initial?.score ?: -1.0, 0.0)

        database.preparationDao().insertReviewRecord(
            ReviewRecordEntity(
                id = "rating-record-1",
                userId = "local-user",
                preparationSessionId = null,
                questionId = "question-bca-s1-dbms-direct",
                rating = com.studysmart.app.core.model.Rating.EASY,
                responseTimeMs = null,
                reviewedAt = now.minusSeconds(100),
            ),
        )
        database.preparationDao().upsertReviewSchedule(
            ReviewScheduleEntity(
                userId = "local-user",
                questionId = "question-bca-s1-dbms-direct",
                learningState = LearningState.MASTERED,
                lastReviewedAt = now.minusSeconds(100),
                nextReviewAt = now.plusSeconds(300),
                intervalSeconds = 604_800,
                repetitionCount = 3,
                lapseCount = 0,
            ),
        )

        val attemptRepository = RoomTestAttemptRepository(
            database = database,
            preferencesRepository = FixedPreferencesRepository(
                TestDefaults(questionCount = 1, questionSource = TestQuestionSource.ALL),
            ),
            clock = Clock.fixed(now, ZoneOffset.UTC),
            randomSource = FixedRandomSource,
            idGenerator = FixedIdGenerator(),
            dispatchers = DefaultDispatcherProvider,
            monotonicClock = FixedMonotonicClock,
            answerEvaluator = KeywordAnswerEvaluator(),
        )
        val created = attemptRepository.createChapterAttempt(
            ChapterTestAttemptRequest(
                chapterId = "chapter-bca-s1-dbms-01",
                creationRequestId = "readiness-attempt-request",
            ),
        ) as TestAttemptCreationResult.Created
        assertEquals(com.studysmart.app.feature.test.TestAttemptStartResult.Started,
            attemptRepository.startAttempt(created.attemptId))
        val question = database.testAttemptDao()
            .observeAttemptQuestions(created.attemptId).first().single()
        assertEquals(
            TestSubmissionResult.Submitted,
            attemptRepository.submitAttempt(
                created.attemptId,
                TestSubmitAction.MANUAL,
                TestAnswerDraft(question.id, "", false, 0L),
            ),
        )

        val afterTest = readinessRepository.observeReadiness(scope).first { it?.recentAttemptCount == 1 }
        assertNotNull(afterTest)
        assertEquals(1, afterTest?.ratedQuestionCount)
        assertEquals(50.0, afterTest?.coverageScore ?: -1.0, 0.0)
        assertEquals(12.5, afterTest?.masteryScore ?: -1.0, 0.0)
        assertEquals(0.0, afterTest?.recentTestScore ?: -1.0, 0.0)
    }
}

private class FixedPreferencesRepository(defaults: TestDefaults) : PreferencesRepository {
    private val state = MutableStateFlow(StudyPreferences(testDefaults = defaults))
    override val preferences: Flow<StudyPreferences> = state

    override suspend fun selectCourse(courseId: String) = Unit
    override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) = Unit
    override suspend fun clearSelectedCourse() = Unit
    override suspend fun clearSelectedPeriod() = Unit
    override suspend fun clearSelectedContext() = Unit
    override suspend fun clearSelectedCourseIfUnchanged(courseId: String, academicPeriodId: String?) = false
    override suspend fun clearSelectedPeriodIfUnchanged(courseId: String, academicPeriodId: String) = false
    override suspend fun updateTestDefaults(defaults: TestDefaults) {
        state.value = state.value.copy(testDefaults = defaults)
    }
}

private object FixedRandomSource : RandomSource {
    override fun nextInt(until: Int): Int = 0
    override fun nextLong(): Long = 1L
}

private object FixedMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = 10_000L
    override fun bootId(): String = "readiness-test-boot"
}

private class FixedIdGenerator : IdGenerator {
    private var next = 0
    override fun nextId(prefix: String): String = "$prefix-${next++}"
}
