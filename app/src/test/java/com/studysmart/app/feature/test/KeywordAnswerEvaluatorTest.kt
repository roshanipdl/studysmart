package com.studysmart.app.feature.test

import com.studysmart.app.core.database.TestAnswerEntity
import com.studysmart.app.core.database.TestAttemptQuestionEntity
import com.studysmart.app.core.model.KeywordEvidenceCodec
import com.studysmart.app.core.model.KeywordNormalizer
import com.studysmart.app.core.model.KeywordSnapshotCodec
import com.studysmart.app.core.model.KeywordSnapshotEntry
import com.studysmart.app.core.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordAnswerEvaluatorTest {
    private val evaluator = KeywordAnswerEvaluator()

    @Test
    fun normalizesUnicodeAndMatchesSingleWordsOnlyAtBoundaries() {
        val result = evaluate(
            listOf("ＣＡＴ" to 1.0),
            "The cat appears in a concatenated catalog.",
        )

        assertEquals(100.0, result.answers.single().keywordMatchPercent!!, 0.0)
        assertEquals(listOf("cat"), result.answers.single().matchedKeywords)

        val substringOnly = evaluate(listOf("cat" to 1.0), "concatenated")
        assertEquals(0.0, substringOnly.answers.single().keywordMatchPercent!!, 0.0)
    }

    @Test
    fun matchesNormalizedPhrasesAsContiguousTextAndCountsEachKeywordOnce() {
        val result = evaluate(
            listOf("data base" to 2.0, "database" to 1.0),
            "DATABASE data   base",
        )

        assertEquals(100.0, result.answers.single().keywordMatchPercent!!, 0.0)
        assertEquals(listOf("data base", "database"), result.answers.single().matchedKeywords)
    }

    @Test
    fun appliesPositiveWeightsThresholdAndHalfUpScoreRounding() {
        val result = evaluate(
            listOf("alpha" to 1.0, "beta" to 1.0),
            "alpha",
            maxScore = 1.01,
        )

        val answer = result.answers.single()
        assertEquals(50.0, answer.keywordMatchPercent!!, 0.0)
        assertEquals(0.51, answer.score, 0.0)
        assertFalse(answer.requiresReview)
        assertEquals(listOf("beta"), answer.missingKeywords)
    }

    @Test
    fun blankAnswerIsZeroAndRequiresReview() {
        val answer = evaluate(listOf("alpha" to 1.0), "  \n ").answers.single()

        assertEquals("", answer.normalizedAnswer)
        assertEquals(0.0, answer.keywordMatchPercent!!, 0.0)
        assertEquals(0.0, answer.score, 0.0)
        assertTrue(answer.requiresReview)
    }

    @Test
    fun zeroKeywordQuestionIsNotEvaluableAndCountsAsZeroInAggregate() {
        val result = evaluateMany(
            questions = listOf(listOf("alpha" to 3.0, "beta" to 1.0), emptyList()),
            answers = listOf("alpha", "some text"),
        )

        assertEquals(37.5, result.aggregateKeywordMatchPercent, 0.000001)
        assertEquals(1, result.evaluableQuestionCount)
        val notEvaluable = result.answers[1]
        assertEquals(AnswerEvaluationStatus.NOT_EVALUABLE, notEvaluable.status)
        assertNull(notEvaluable.keywordMatchPercent)
        assertEquals(0.0, notEvaluable.score, 0.0)
        assertTrue(notEvaluable.requiresReview)
        assertEquals(1, result.requiresReviewCount)
    }

    @Test
    fun allZeroKeywordQuestionsHaveZeroAggregateMatchAndBoundedAttemptPercent() {
        val result = evaluateMany(
            questions = listOf(emptyList(), emptyList()),
            answers = listOf("a", "b"),
        )

        assertEquals(0.0, result.aggregateKeywordMatchPercent, 0.0)
        assertEquals(0.0, result.percentage, 0.0)
        assertEquals(0, result.evaluableQuestionCount)
    }

    @Test
    fun keywordEvidenceCodecRoundTripsEscapedUnicode() {
        val evidence = listOf("a \"quoted\" term", "café", "slash\\term")
            .map(KeywordNormalizer::normalize)

        assertEquals(evidence, KeywordEvidenceCodec.decode(KeywordEvidenceCodec.encode(evidence)))
    }

    private fun evaluate(
        keywords: List<Pair<String, Double>>,
        answerText: String,
        maxScore: Double = 2.0,
    ): TestEvaluation = evaluateMany(listOf(keywords), listOf(answerText), maxScore)

    private fun evaluateMany(
        questions: List<List<Pair<String, Double>>>,
        answers: List<String>,
        maxScore: Double = 2.0,
    ): TestEvaluation {
        val questionEntities = questions.mapIndexed { index, keywords ->
            TestAttemptQuestionEntity(
                id = "attempt-question-$index",
                testAttemptId = "attempt-1",
                questionId = "question-$index",
                displayOrder = index,
                questionTextSnapshot = "Question $index",
                referenceAnswerSnapshot = "Reference $index",
                questionTypeSnapshot = QuestionType.WRITTEN,
                maxScoreSnapshot = maxScore,
                keywordSnapshotJson = KeywordSnapshotCodec.encode(
                    keywords.map { (keyword, weight) ->
                        KeywordSnapshotEntry(KeywordNormalizer.normalize(keyword), weight)
                    },
                ),
                keywordSnapshotVersion = KeywordSnapshotCodec.CURRENT_VERSION,
            )
        }
        val answerEntities = questionEntities.mapIndexed { index, question ->
            TestAnswerEntity(
                id = "answer-$index",
                testAttemptQuestionId = question.id,
                answerText = answers[index],
                selectedOption = null,
                isFlagged = false,
                timeSpentSeconds = null,
                keywordMatchPercent = null,
                normalizedAnswer = null,
                matchedKeywordsJson = null,
                missingKeywordsJson = null,
                score = null,
                maxScore = null,
                requiresReview = null,
                updatedAt = java.time.Instant.EPOCH,
            )
        }
        return evaluator.evaluate(questionEntities, answerEntities)
    }
}
