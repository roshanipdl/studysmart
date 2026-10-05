package com.studysmart.app.feature.progress

import com.studysmart.app.core.common.StudyTimeSource

import com.studysmart.app.core.database.ProgressDao
import com.studysmart.app.core.database.ReadinessDao
import com.studysmart.app.core.database.ReadinessQuestionRow
import com.studysmart.app.core.database.TestAttemptDao
import com.studysmart.app.core.model.LearningState
import com.studysmart.app.feature.review.effectiveLearningState
import com.studysmart.app.feature.test.TestAttemptModel
import com.studysmart.app.feature.test.TestResultProjection
import com.studysmart.app.feature.test.TestResultProjector
import com.studysmart.app.feature.test.TestResultReport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

data class ProgressMetrics(val activeCount: Int, val studiedCount: Int, val learningCount: Int, val dailyRatedCount: Int,
    val hasActivity: Boolean)

fun projectProgress(questions: List<ReadinessQuestionRow>, dailyRatedCount: Int, hasSubmittedTest: Boolean): ProgressMetrics {
    val unique = questions.distinctBy { it.questionId }
    val studied = unique.count { it.hasCommittedRating }
    return ProgressMetrics(unique.size, studied, unique.count {
        effectiveLearningState(it.learningState, it.lastReviewedAt, it.latestTestSubmittedAt, it.latestTestRequiresReview) == LearningState.LEARNING
    }, dailyRatedCount.coerceAtLeast(0), studied > 0 || hasSubmittedTest || unique.any { it.latestTestSubmittedAt != null })
}

data class ProgressSnapshot(val metrics: ProgressMetrics, val latestTest: TestResultReport?)

interface ProgressRepository {
    fun observeProgress(courseId: String, periodId: String): Flow<ProgressSnapshot>
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoomProgressRepository(
    private val dao: ProgressDao,
    private val readiness: ReadinessDao,
    private val tests: TestAttemptDao,
    private val time: StudyTimeSource,
    private val userId: String = "local-user",
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ProgressRepository {
    override fun observeProgress(courseId: String, periodId: String): Flow<ProgressSnapshot> {
        val daily = time.observeTime().map { it.day }.distinctUntilChanged().flatMapLatest { day ->
            dao.observeDailyRatedCount(userId, courseId, periodId, day.start, day.end)
        }
        val latestTest = dao.observeLatestSubmittedAttemptId(userId, courseId, periodId).flatMapLatest { id ->
            if (id == null) flowOf(null) else tests.observeAttemptWithQuestions(id).map { bundle ->
                requireNotNull(bundle) { "Submitted test is unavailable" }
                val model = TestAttemptModel(bundle.attempt, bundle.questionRows.map { it.question }.sortedBy { it.displayOrder },
                    bundle.questionRows.flatMap { it.answerRows })
                when (val result = TestResultProjector.project(model)) {
                    is TestResultProjection.Available -> result.report
                    else -> error("Submitted test evidence is unavailable")
                }
            }
        }
        return combine(readiness.observeQuestions(userId, courseId, periodId, null, null), daily, latestTest) { questions, count, latest ->
            ProgressSnapshot(projectProgress(questions, count, latest != null), latest)
        }.flowOn(dispatcher)
    }
}
