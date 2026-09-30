package com.studysmart.app.feature.test

import com.studysmart.app.core.database.TestAnswerEntity
import com.studysmart.app.core.database.TestAttemptQuestionEntity

enum class AnswerEvaluationStatus {
    EVALUATED,
    NOT_EVALUABLE,
}

data class EvaluatedTestAnswer(
    val attemptQuestionId: String,
    val normalizedAnswer: String,
    val matchedKeywords: List<String>,
    val missingKeywords: List<String>,
    val keywordMatchPercent: Double?,
    val score: Double,
    val maxScore: Double,
    val requiresReview: Boolean,
    val status: AnswerEvaluationStatus,
)

data class TestEvaluation(
    val answers: List<EvaluatedTestAnswer>,
    val totalScore: Double,
    val maxScore: Double,
    val percentage: Double,
    val aggregateKeywordMatchPercent: Double,
    val evaluableQuestionCount: Int,
    val requiresReviewCount: Int,
)

/** Replaceable evaluator boundary; implementations must use only fixed attempt snapshots. */
interface AnswerEvaluator {
    val version: String

    fun evaluate(
        questions: List<TestAttemptQuestionEntity>,
        answers: List<TestAnswerEntity>,
    ): TestEvaluation
}
