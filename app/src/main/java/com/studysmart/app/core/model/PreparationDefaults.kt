package com.studysmart.app.core.model

/** Unique-question limit shared by new preparation and due-review sessions. */
data class PreparationDefaults(val questionCount: Int = DEFAULT_QUESTION_COUNT) {
    init {
        require(questionCount in MIN_QUESTION_COUNT..MAX_QUESTION_COUNT) {
            "preparation question count must be between $MIN_QUESTION_COUNT and $MAX_QUESTION_COUNT"
        }
    }

    companion object {
        const val DEFAULT_QUESTION_COUNT = 10
        const val MIN_QUESTION_COUNT = 1
        const val MAX_QUESTION_COUNT = 30
    }
}
