package com.studysmart.app.feature.review

import com.studysmart.app.core.database.ReadinessQuestionRow
import com.studysmart.app.core.model.LearningState
import java.time.Instant

enum class ReviewFilter(val routeValue: String, val title: String) {
    NEEDS_IMPROVEMENT("needs_improvement", "Needs Improvement"),
    NOT_STUDIED("not_studied", "Not Studied"),
    GOOD("good", "Good"),
    MASTERED("mastered", "Mastered");

    fun select(rows: List<ReadinessQuestionRow>): List<ReadinessQuestionRow> {
        val matching = rows.filter { row ->
            val state = effectiveLearningState(row.learningState.takeIf { row.hasCommittedRating },
                row.lastReviewedAt.takeIf { row.hasCommittedRating },
                row.latestTestSubmittedAt, row.latestTestRequiresReview)
            when (this) {
                NEEDS_IMPROVEMENT -> state == LearningState.NEEDS_IMPROVEMENT
                NOT_STUDIED -> !row.hasCommittedRating && row.latestTestSubmittedAt == null
                GOOD -> state == LearningState.GOOD
                MASTERED -> state == LearningState.MASTERED
            }
        }
        // Stable sorting retains the DAO's canonical curriculum order for ties.
        return when (this) {
            NOT_STUDIED -> matching
            NEEDS_IMPROVEMENT -> matching.sortedWith(compareBy<ReadinessQuestionRow> {
                if (it.latestTestRequiresReview == true && (it.lastReviewedAt == null || it.latestTestSubmittedAt?.isAfter(it.lastReviewedAt) == true)) 0
                else if (it.latestRating == com.studysmart.app.core.model.Rating.AGAIN) 0 else 1
            }.thenBy {
                if (it.latestTestRequiresReview == true && (it.lastReviewedAt == null || it.latestTestSubmittedAt?.isAfter(it.lastReviewedAt) == true))
                    it.latestTestSubmittedAt ?: Instant.MIN else it.lastReviewedAt ?: Instant.MIN
            })
            GOOD, MASTERED -> matching.sortedByDescending { it.lastReviewedAt ?: Instant.MIN }
        }
    }

    companion object {
        fun fromRoute(value: String?): ReviewFilter? = entries.firstOrNull { it.routeValue == value }
    }
}
