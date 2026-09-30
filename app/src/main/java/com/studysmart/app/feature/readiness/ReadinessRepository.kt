package com.studysmart.app.feature.readiness

import com.studysmart.app.core.database.ReadinessDao
import com.studysmart.app.core.database.ReadinessQuestionRow
import com.studysmart.app.core.database.ReadinessTestAnswerRow
import java.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

data class ReadinessScope(
    val courseId: String,
    val academicPeriodId: String? = null,
    val subjectId: String? = null,
    val chapterId: String? = null,
) {
    init {
        require(courseId.isNotBlank()) { "courseId must not be blank" }
        require(academicPeriodId == null || academicPeriodId.isNotBlank()) {
            "academicPeriodId must not be blank"
        }
        require(subjectId == null || subjectId.isNotBlank()) { "subjectId must not be blank" }
        require(chapterId == null || chapterId.isNotBlank()) { "chapterId must not be blank" }
    }
}

interface ReadinessRepository {
    fun observeReadiness(scope: ReadinessScope): Flow<ReadinessEstimate?>
}

class RoomReadinessRepository(
    private val dao: ReadinessDao,
    private val clock: Clock,
    private val calculator: ReadinessCalculator = ReadinessCalculator(),
    private val userId: String = LOCAL_USER_ID,
    private val clockRefreshMillis: Long = DEFAULT_CLOCK_REFRESH_MILLIS,
) : ReadinessRepository {
    init {
        require(clockRefreshMillis > 0L) { "clockRefreshMillis must be positive" }
    }

    override fun observeReadiness(scope: ReadinessScope): Flow<ReadinessEstimate?> {
        val clockTicks = flow {
            while (true) {
                emit(clock.instant())
                delay(clockRefreshMillis)
            }
        }
        return combine(
            dao.observeQuestions(
                userId = userId,
                courseId = scope.courseId,
                academicPeriodId = scope.academicPeriodId,
                subjectId = scope.subjectId,
                chapterId = scope.chapterId,
            ),
            dao.observeSubmittedAnswers(
                userId = userId,
                courseId = scope.courseId,
                academicPeriodId = scope.academicPeriodId,
                subjectId = scope.subjectId,
                chapterId = scope.chapterId,
            ),
            clockTicks,
        ) { questions, answers, now ->
            calculator.calculate(
                questions = questions.map { it.toInput() },
                testAnswers = answers.map { it.toInput() },
                now = now,
            )
        }
    }

    private fun ReadinessQuestionRow.toInput() = ReadinessQuestionInput(
        questionId = questionId,
        learningState = learningState,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        hasCommittedRating = hasCommittedRating,
        latestTestSubmittedAt = latestTestSubmittedAt,
        latestTestRequiresReview = latestTestRequiresReview,
    )

    private fun ReadinessTestAnswerRow.toInput() = ReadinessTestAnswerInput(
        attemptId = attemptId,
        questionId = questionId,
        submittedAt = submittedAt,
        score = score,
        maxScore = maxScore,
    )

    private companion object {
        const val LOCAL_USER_ID = "local-user"
        const val DEFAULT_CLOCK_REFRESH_MILLIS = 60_000L
    }
}
