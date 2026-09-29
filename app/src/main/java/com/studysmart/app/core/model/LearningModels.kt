package com.studysmart.app.core.model

import java.time.Instant

enum class Rating {
    AGAIN,
    HARD,
    GOOD,
    EASY,
}

enum class LearningState {
    NOT_STUDIED,
    LEARNING,
    NEEDS_IMPROVEMENT,
    GOOD,
    MASTERED,
}

enum class PreparationScopeType {
    CHAPTER,
    TOPIC,
    DUE_REVIEW,
}

enum class PreparationOriginType {
    CHAPTER,
    TOPIC,
    REVIEW,
    IMPORTANT,
    HOME_CONTINUE,
}

enum class PreparationSessionStatus {
    ACTIVE,
    COMPLETED,
}

data class ReviewScheduleDecision(
    val rating: Rating,
    val reviewedAt: Instant,
    val nextReviewAt: Instant,
    val intervalSeconds: Long,
)

data class ReviewScheduleSnapshot(
    val questionId: String,
    val learningState: LearningState = LearningState.NOT_STUDIED,
    val lastReviewedAt: Instant? = null,
    val nextReviewAt: Instant? = null,
    val intervalSeconds: Long = 0L,
    val repetitionCount: Int = 0,
    val lapseCount: Int = 0,
) {
    init {
        require(questionId.isNotBlank()) { "questionId must not be blank" }
        require(intervalSeconds >= 0) { "intervalSeconds must not be negative" }
        require(repetitionCount >= 0) { "repetitionCount must not be negative" }
        require(lapseCount >= 0) { "lapseCount must not be negative" }
    }
}
