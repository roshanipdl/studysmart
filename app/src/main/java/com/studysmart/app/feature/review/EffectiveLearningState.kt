package com.studysmart.app.feature.review

import com.studysmart.app.core.model.LearningState
import java.time.Instant

/** Tests can warn about weak answers, but never add successful card repetitions. */
fun effectiveLearningState(
    ratingState: LearningState?,
    lastReviewedAt: Instant?,
    latestTestSubmittedAt: Instant?,
    latestTestRequiresReview: Boolean?,
): LearningState {
    val testIsLater = latestTestSubmittedAt?.let { lastReviewedAt == null || it.isAfter(lastReviewedAt) } == true
    return if (testIsLater && latestTestRequiresReview == true) {
        LearningState.NEEDS_IMPROVEMENT
    } else {
        ratingState ?: LearningState.NOT_STUDIED
    }
}
