package com.studysmart.app.feature.test

import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.TestQuestionSource

private const val WEAK_TEST_THRESHOLD = 50.0

data class TestCandidate(
    val questionId: String,
    val latestRating: Rating?,
    val hasReview: Boolean,
    val latestTestPercentage: Double?,
    val latestTestRequiresReview: Boolean?,
    val isImportant: Boolean,
) {
    val isWeak: Boolean
        get() = latestRating == Rating.AGAIN ||
            latestRating == Rating.HARD ||
            latestTestPercentage?.let { it < WEAK_TEST_THRESHOLD } == true ||
            latestTestRequiresReview == true

    val isUnseen: Boolean
        get() = !hasReview
}

data class TestSelectionPlan(
    val selectionSeed: Long?,
    val orderedQuestionIds: List<String>,
) {
    init {
        require(orderedQuestionIds.size <= TestDefaults.MAX_QUESTION_COUNT) {
            "a test plan cannot contain more than ${TestDefaults.MAX_QUESTION_COUNT} questions"
        }
        require(orderedQuestionIds.distinct().size == orderedQuestionIds.size) {
            "a test plan cannot contain duplicate questions"
        }
    }
}

class TestQuestionSelectionEngine {
    fun createPlan(
        candidates: List<TestCandidate>,
        defaults: TestDefaults,
        selectionSeed: Long?,
    ): TestSelectionPlan? {
        val uniqueCandidates = candidates.distinctBy(TestCandidate::questionId)
        val filtered = when (defaults.questionSource) {
            TestQuestionSource.ALL,
            TestQuestionSource.RANDOM,
            -> uniqueCandidates
            TestQuestionSource.WEAK -> uniqueCandidates.filter(TestCandidate::isWeak)
            TestQuestionSource.UNSEEN -> uniqueCandidates.filter(TestCandidate::isUnseen)
            TestQuestionSource.IMPORTANT -> uniqueCandidates.filter(TestCandidate::isImportant)
        }
        if (filtered.isEmpty()) return null

        val ordered = if (defaults.questionSource == TestQuestionSource.ALL) {
            filtered.take(defaults.questionCount)
        } else {
            val seed = selectionSeed ?: error("sampled test sources require a selection seed")
            filtered
                .sortedWith(compareBy<TestCandidate> { seededTieBreak(it.questionId, seed) }.thenBy { it.questionId })
                .take(defaults.questionCount)
        }
        return TestSelectionPlan(
            selectionSeed = selectionSeed.takeIf { defaults.questionSource != TestQuestionSource.ALL },
            orderedQuestionIds = ordered.map(TestCandidate::questionId),
        )
    }

    private fun seededTieBreak(questionId: String, selectionSeed: Long): Long {
        var hash = FNV_OFFSET_BASIS xor selectionSeed
        questionId.toByteArray(Charsets.UTF_8).forEach { byte ->
            hash = (hash xor (byte.toInt() and 0xFF).toLong()) * FNV_PRIME
        }
        return hash
    }

    private companion object {
        const val FNV_OFFSET_BASIS = -3750763034362895579L
        const val FNV_PRIME = 1099511628211L
    }
}
