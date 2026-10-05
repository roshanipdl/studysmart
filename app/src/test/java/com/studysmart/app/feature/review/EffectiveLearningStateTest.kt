package com.studysmart.app.feature.review

import com.studysmart.app.core.model.LearningState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveLearningStateTest {
    private val ratingAt = Instant.parse("2026-10-04T12:00:00Z")

    @Test fun missingHistoryIsNotStudied() {
        assertEquals(LearningState.NOT_STUDIED, effectiveLearningState(null, null, null, null))
    }

    @Test fun laterWeakTestDemotesEveryRatingState() {
        LearningState.entries.forEach { state ->
            assertEquals(LearningState.NEEDS_IMPROVEMENT,
                effectiveLearningState(state, ratingAt, ratingAt.plusSeconds(1), true))
        }
    }

    @Test fun laterStrongTestExposesRatingStateWithoutPromotion() {
        LearningState.entries.forEach { state ->
            assertEquals(state, effectiveLearningState(state, ratingAt, ratingAt.plusSeconds(1), false))
        }
    }

    @Test fun laterRatingClearsTestWarning() {
        assertEquals(LearningState.GOOD,
            effectiveLearningState(LearningState.GOOD, ratingAt, ratingAt.minusSeconds(1), true))
    }

    @Test fun equalTimestampsRetainExistingRatingPrecedence() {
        assertEquals(LearningState.MASTERED,
            effectiveLearningState(LearningState.MASTERED, ratingAt, ratingAt, true))
    }

    @Test fun testOnlyWeakAnswerNeedsImprovement() {
        assertEquals(LearningState.NEEDS_IMPROVEMENT, effectiveLearningState(null, null, ratingAt, true))
    }

    @Test fun testOnlyStrongAnswerDoesNotPromoteMastery() {
        assertEquals(LearningState.NOT_STUDIED, effectiveLearningState(null, null, ratingAt, false))
    }

    @Test fun missingEvaluationDoesNotCreateWarning() {
        assertEquals(LearningState.LEARNING,
            effectiveLearningState(LearningState.LEARNING, ratingAt, ratingAt.plusSeconds(1), null))
    }
}
