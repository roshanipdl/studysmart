package com.studysmart.app.core.database

import com.studysmart.app.core.model.KeywordNormalizer
import com.studysmart.app.core.model.KeywordSnapshotCodec
import com.studysmart.app.core.model.KeywordSnapshotEntry
import com.studysmart.app.core.model.QuestionType
import com.studysmart.app.feature.test.KeywordAnswerEvaluator
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugSampleCurriculumTest {
    @Test
    fun catalogHasFiveChaptersTwentyTopicsAndTwoHundredTenQuestions() {
        val chapters = DebugSampleCurriculum.chapters
        assertEquals(5, chapters.size)
        assertEquals(20, chapters.sumOf { it.topics.size })
        assertEquals(210, chapters.sumOf { it.questions.size })
        chapters.forEach { chapter ->
            assertEquals(4, chapter.topics.size)
            assertEquals(42, chapter.questions.size)
            assertEquals(2, chapter.directQuestions.size)
            assertTrue(chapter.directQuestions.all { it.topicId == null })
            chapter.topics.forEach { topic ->
                assertEquals(10, topic.questions.size)
                assertTrue(topic.questions.all { it.topicId == topic.id })
                assertEquals((1..10).map { "${topic.id}-q${it.toString().padStart(2, '0')}" }, topic.questions.map { it.id })
            }
            assertEquals(chapter.directQuestions, chapter.questions.takeLast(2))
        }
        DebugSampleCurriculum.validate(chapters)
    }

    @Test
    fun allReferenceAnswersReceiveFullKeywordScores() {
        DebugSampleCurriculum.chapters.flatMap { it.questions }.forEach { question ->
            val result = evaluate(question, question.answer)
            assertEquals(question.id, 100.0, result.keywordMatchPercent!!, 0.0)
            assertEquals(question.id, 2.0, result.score, 0.0)
            assertTrue(question.id, result.missingKeywords.isEmpty())
        }
    }

    @Test
    fun partialAndUnrelatedAnswersProduceDistinctScores() {
        val question = DebugSampleCurriculum.chapters.first().questions.first()
        val partial = evaluate(question, question.keywords.first().text)
        assertEquals(0.67, partial.score, 0.0)
        assertTrue(partial.requiresReview)
        val unrelated = evaluate(question, "Purple umbrellas drift silently.")
        assertEquals(0.0, unrelated.score, 0.0)
        assertTrue(unrelated.requiresReview)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateIdsAreRejected() {
        val chapter = DebugSampleCurriculum.chapters.first()
        DebugSampleCurriculum.validate(listOf(chapter, chapter))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidKeywordWeightsAreRejected() {
        val chapter = DebugSampleCurriculum.chapters.first()
        val question = chapter.directQuestions.first()
        DebugSampleCurriculum.validate(listOf(chapter.copy(directQuestions = listOf(
            question.copy(keywords = question.keywords.map { it.copy(weight = Double.NaN) }),
        ))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateNormalizedKeywordsAreRejected() {
        val chapter = DebugSampleCurriculum.chapters.first()
        val question = chapter.directQuestions.first()
        DebugSampleCurriculum.validate(listOf(chapter.copy(directQuestions = listOf(
            question.copy(keywords = question.keywords + question.keywords.first().copy(
                text = question.keywords.first().text.uppercase(),
            )),
        ))))
    }

    private fun evaluate(question: SampleQuestion, text: String) = KeywordAnswerEvaluator().evaluate(
        listOf(TestAttemptQuestionEntity(
            id = "snapshot", testAttemptId = "attempt", questionId = question.id, displayOrder = 1,
            questionTextSnapshot = question.prompt, referenceAnswerSnapshot = question.answer,
            questionTypeSnapshot = QuestionType.WRITTEN, maxScoreSnapshot = 2.0,
            keywordSnapshotVersion = KeywordSnapshotCodec.CURRENT_VERSION,
            keywordSnapshotJson = KeywordSnapshotCodec.encode(question.keywords.map {
                KeywordSnapshotEntry(KeywordNormalizer.normalize(it.text), it.weight)
            }),
        )),
        listOf(TestAnswerEntity(
            id = "answer", testAttemptQuestionId = "snapshot", answerText = text,
            selectedOption = null, isFlagged = false, timeSpentSeconds = null,
            keywordMatchPercent = null, normalizedAnswer = null, matchedKeywordsJson = null,
            missingKeywordsJson = null, score = null, maxScore = null, requiresReview = null,
            updatedAt = Instant.EPOCH,
        )),
    ).answers.single()
}
