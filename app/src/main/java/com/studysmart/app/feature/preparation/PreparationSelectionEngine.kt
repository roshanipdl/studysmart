package com.studysmart.app.feature.preparation

import java.time.Instant

const val MAX_UNIQUE_QUESTIONS = 30

data class PreparationCandidate(
    val questionId: String,
    val topicId: String?,
    val curriculumOrder: Long,
    val isWeak: Boolean,
    val isUnseen: Boolean,
    val nextReviewAt: Instant?,
    val bookmarkedAt: Instant? = null,
) {
    init {
        require(questionId.isNotBlank()) { "questionId must not be blank" }
        require(topicId == null || topicId.isNotBlank()) { "topicId must not be blank" }
        require(curriculumOrder >= 0) { "curriculumOrder must not be negative" }
        require(!(isWeak && isUnseen)) { "a candidate cannot be both weak and unseen" }
    }
}

enum class PreparationSelectionPolicy {
    ADAPTIVE,
    DUE_REVIEW,
    IMPORTANT,
}

data class PreparationPlan(
    val selectionSeed: Long,
    val orderedQuestionIds: List<String>,
) {
    init {
        require(orderedQuestionIds.size <= MAX_UNIQUE_QUESTIONS) {
            "a preparation plan cannot contain more than $MAX_UNIQUE_QUESTIONS questions"
        }
        require(orderedQuestionIds.distinct().size == orderedQuestionIds.size) {
            "a preparation plan cannot contain duplicate question IDs"
        }
    }

    val uniqueQuestionCount: Int
        get() = orderedQuestionIds.size
}

class PreparationSelectionEngine {
    fun createPlan(
        candidates: List<PreparationCandidate>,
        policy: PreparationSelectionPolicy,
        selectedAt: Instant,
        selectionSeed: Long,
        uniqueQuestionLimit: Int = MAX_UNIQUE_QUESTIONS,
    ): PreparationPlan? {
        require(uniqueQuestionLimit in 1..MAX_UNIQUE_QUESTIONS) { "invalid unique question limit" }
        val uniqueCandidates = candidates.uniqueByQuestionId()
        val orderedCandidates = when (policy) {
            PreparationSelectionPolicy.ADAPTIVE -> uniqueCandidates
                .sortedWith(adaptiveComparator(selectedAt, selectionSeed))

            PreparationSelectionPolicy.DUE_REVIEW -> uniqueCandidates
                .asSequence()
                .filter { it.nextReviewAt?.let { nextReviewAt -> nextReviewAt <= selectedAt } == true }
                .sortedWith(dueReviewComparator())
                .toList()
            PreparationSelectionPolicy.IMPORTANT -> uniqueCandidates
                .filter { it.bookmarkedAt != null }
                .sortedWith(compareBy<PreparationCandidate> { it.bookmarkedAt }.thenBy { it.questionId })
        }

        if (orderedCandidates.isEmpty()) return null

        return PreparationPlan(
            selectionSeed = selectionSeed,
            orderedQuestionIds = orderedCandidates
                .take(uniqueQuestionLimit)
                .map(PreparationCandidate::questionId),
        )
    }

    private fun adaptiveComparator(
        selectedAt: Instant,
        selectionSeed: Long,
    ): Comparator<PreparationCandidate> = compareBy<PreparationCandidate> {
        it.priorityBucket(selectedAt)
    }.thenBy { seededTieBreak(it.questionId, selectionSeed) }
        .thenBy { it.curriculumOrder }
        .thenBy { it.questionId }

    private fun dueReviewComparator(): Comparator<PreparationCandidate> = compareBy<PreparationCandidate> {
        it.nextReviewAt
    }.thenBy { it.curriculumOrder }
        .thenBy { it.questionId }
}

private enum class PriorityBucket {
    WEAK,
    DUE,
    UNSEEN,
    STRONG,
}

private fun PreparationCandidate.priorityBucket(selectedAt: Instant): PriorityBucket = when {
    isWeak -> PriorityBucket.WEAK
    nextReviewAt?.let { it <= selectedAt } == true -> PriorityBucket.DUE
    isUnseen -> PriorityBucket.UNSEEN
    else -> PriorityBucket.STRONG
}

private fun List<PreparationCandidate>.uniqueByQuestionId(): List<PreparationCandidate> =
    groupBy(PreparationCandidate::questionId).map { (questionId, duplicates) ->
        require(duplicates.distinct().size == 1) {
            "conflicting candidates for questionId=$questionId"
        }
        duplicates.first()
    }

/**
 * Uses a fixed 64-bit hash so persisted selection seeds do not depend on input
 * list order, object identity, or the implementation of a random collection.
 */
private fun seededTieBreak(questionId: String, selectionSeed: Long): Long {
    var hash = FNV_OFFSET_BASIS xor selectionSeed
    questionId.toByteArray(Charsets.UTF_8).forEach { byte ->
        hash = (hash xor (byte.toInt() and 0xFF).toLong()) * FNV_PRIME
    }
    return hash
}

private const val FNV_OFFSET_BASIS = -3750763034362895579L
private const val FNV_PRIME = 1099511628211L
