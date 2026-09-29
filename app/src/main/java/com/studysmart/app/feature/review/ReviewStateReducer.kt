package com.studysmart.app.feature.review

import com.studysmart.app.core.model.LearningState
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.model.ReviewScheduleDecision
import com.studysmart.app.core.model.ReviewScheduleSnapshot

/** Applies the deterministic MVP review-state rules to one committed rating. */
class ReviewStateReducer {
    fun reduce(
        questionId: String,
        current: ReviewScheduleSnapshot?,
        decision: ReviewScheduleDecision,
    ): ReviewScheduleSnapshot {
        require(questionId.isNotBlank()) { "questionId must not be blank" }
        val previous = current ?: ReviewScheduleSnapshot(questionId = questionId)
        require(previous.questionId == questionId) {
            "review schedule question does not match the rated question"
        }
        val successfulRepetitions = previous.repetitionCount +
            if (decision.rating.isSuccessfulRepetition()) 1 else 0
        val nextState = when (decision.rating) {
            Rating.AGAIN -> LearningState.NEEDS_IMPROVEMENT
            Rating.HARD -> LearningState.LEARNING
            Rating.GOOD -> if (successfulRepetitions >= 2) {
                LearningState.GOOD
            } else {
                LearningState.LEARNING
            }
            Rating.EASY -> if (successfulRepetitions >= 3) {
                LearningState.MASTERED
            } else if (successfulRepetitions >= 2) {
                LearningState.GOOD
            } else {
                LearningState.LEARNING
            }
        }

        return ReviewScheduleSnapshot(
            questionId = questionId,
            learningState = nextState,
            lastReviewedAt = decision.reviewedAt,
            nextReviewAt = decision.nextReviewAt,
            intervalSeconds = decision.intervalSeconds,
            repetitionCount = successfulRepetitions,
            lapseCount = previous.lapseCount + if (decision.rating == Rating.AGAIN) 1 else 0,
        )
    }
}

private fun Rating.isSuccessfulRepetition(): Boolean = this == Rating.GOOD || this == Rating.EASY
