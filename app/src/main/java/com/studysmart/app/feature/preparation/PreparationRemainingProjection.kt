package com.studysmart.app.feature.preparation

/** Counts currently queued first presentations separately from queued repeats. */
data class PreparationRemainingProjection(
    val uniqueLeft: Int,
    val repeatsLeft: Int,
) {
    init {
        require(uniqueLeft >= 0) { "unique questions left cannot be negative" }
        require(repeatsLeft >= 0) { "repeats left cannot be negative" }
    }

    /** Concise copy for the question and answer card headers. */
    val label: String
        get() = when {
            uniqueLeft > 0 && repeatsLeft > 0 ->
                "$uniqueLeft unique · $repeatsLeft ${repeatNoun()} left"
            uniqueLeft > 0 -> "$uniqueLeft unique left"
            repeatsLeft > 0 -> "$repeatsLeft ${repeatNoun()} left"
            else -> "No questions left"
        }

    private fun repeatNoun(): String = if (repeatsLeft == 1) "repeat" else "repeats"
}

/**
 * Uses the persisted queue snapshot, not rating-event totals or a prediction of
 * which future ratings may add repeats. The current pending presentation counts.
 */
fun projectPreparationRemaining(session: PreparationSessionModel): PreparationRemainingProjection {
    val pending = session.items.filterNot { it.isCompleted }
    return PreparationRemainingProjection(
        uniqueLeft = pending.count { it.presentationCount == 0 },
        repeatsLeft = pending.count { it.presentationCount > 0 },
    )
}
