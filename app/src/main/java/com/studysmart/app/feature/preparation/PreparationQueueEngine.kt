package com.studysmart.app.feature.preparation

import com.studysmart.app.core.model.Rating

data class PreparationQueueItem(
    val questionId: String,
    val originalOrder: Int,
    val presentationCount: Int = 0,
    val hardRequeueUsed: Boolean = false,
    val isCompleted: Boolean = false,
    val lastRating: Rating? = null,
)

data class PreparationQueueState(
    val itemsByQuestionId: Map<String, PreparationQueueItem>,
    val pendingQuestionIds: List<String>,
    val totalPresentationCount: Int = 0,
) {
    init {
        require(itemsByQuestionId.size <= MAX_UNIQUE_QUESTIONS) {
            "a preparation queue cannot contain more than $MAX_UNIQUE_QUESTIONS questions"
        }
        require(itemsByQuestionId.keys.all(String::isNotBlank)) {
            "queue question IDs must not be blank"
        }
        require(itemsByQuestionId.all { (questionId, item) ->
            questionId == item.questionId && item.originalOrder >= 0 && item.presentationCount >= 0
        }) {
            "queue items must have valid IDs, order, and presentation counts"
        }
        require(itemsByQuestionId.values.map(PreparationQueueItem::originalOrder).distinct().size == itemsByQuestionId.size) {
            "queue item original orders must be unique"
        }
        require(pendingQuestionIds.distinct().size == pendingQuestionIds.size) {
            "pending queue IDs must be unique"
        }
        require(pendingQuestionIds.all { questionId ->
            itemsByQuestionId[questionId]?.isCompleted == false
        }) {
            "pending queue IDs must refer to incomplete items"
        }
        require(itemsByQuestionId.values.all { item ->
            item.isCompleted == (item.questionId !in pendingQuestionIds)
        }) {
            "every queue item must be either pending or completed"
        }
        require(totalPresentationCount >= 0) {
            "totalPresentationCount must not be negative"
        }
        require(totalPresentationCount == itemsByQuestionId.values.sumOf { it.presentationCount }) {
            "totalPresentationCount must equal item presentation counts"
        }
    }

    val uniqueQuestionCount: Int
        get() = itemsByQuestionId.size

    val isComplete: Boolean
        get() = pendingQuestionIds.isEmpty()
}

data class PreparationQueueTransition(
    val previousQuestionId: String,
    val rating: Rating,
    val state: PreparationQueueState,
    val wasRequeued: Boolean,
    val sessionCompleted: Boolean,
)

class PreparationQueueEngine {
    fun createInitialState(plan: PreparationPlan): PreparationQueueState {
        require(plan.orderedQuestionIds.isNotEmpty()) { "a queue plan must not be empty" }

        val items = plan.orderedQuestionIds.mapIndexed { index, questionId ->
            questionId to PreparationQueueItem(
                questionId = questionId,
                originalOrder = index,
            )
        }.toMap()

        return PreparationQueueState(
            itemsByQuestionId = items,
            pendingQuestionIds = plan.orderedQuestionIds.toList(),
        )
    }

    fun rateCurrent(
        state: PreparationQueueState,
        rating: Rating,
    ): PreparationQueueTransition {
        require(state.pendingQuestionIds.isNotEmpty()) { "cannot rate an empty queue" }

        val currentQuestionId = state.pendingQuestionIds.first()
        val currentItem = state.itemsByQuestionId.getValue(currentQuestionId)
        val remainingPending = state.pendingQuestionIds.drop(1)
        val firstHardRequeue = rating == Rating.HARD && !currentItem.hardRequeueUsed
        val shouldRequeue = rating == Rating.AGAIN || firstHardRequeue
        val updatedItem = currentItem.copy(
            presentationCount = currentItem.presentationCount + 1,
            hardRequeueUsed = currentItem.hardRequeueUsed || firstHardRequeue,
            isCompleted = !shouldRequeue,
            lastRating = rating,
        )
        val nextPending = if (shouldRequeue) {
            val spacing = if (rating == Rating.AGAIN) AGAIN_PENDING_SPACING else HARD_PENDING_SPACING
            remainingPending.toMutableList().apply {
                add(minOf(spacing, size), currentQuestionId)
            }
        } else {
            remainingPending
        }
        val nextState = PreparationQueueState(
            itemsByQuestionId = state.itemsByQuestionId + (currentQuestionId to updatedItem),
            pendingQuestionIds = nextPending,
            totalPresentationCount = state.totalPresentationCount + 1,
        )

        return PreparationQueueTransition(
            previousQuestionId = currentQuestionId,
            rating = rating,
            state = nextState,
            wasRequeued = shouldRequeue,
            sessionCompleted = nextState.isComplete,
        )
    }

    private companion object {
        const val AGAIN_PENDING_SPACING = 3
        const val HARD_PENDING_SPACING = 6
    }
}
