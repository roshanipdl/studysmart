package com.studysmart.app.feature.test

import com.studysmart.app.core.database.TestAnswerEntity
import com.studysmart.app.core.database.TestAttemptEntity
import com.studysmart.app.core.database.TestAttemptQuestionEntity
import com.studysmart.app.core.model.KeywordEvidenceCodec
import com.studysmart.app.core.model.KeywordNormalizer
import com.studysmart.app.core.model.KeywordSnapshotCodec
import com.studysmart.app.core.model.KeywordSnapshotEntry
import com.studysmart.app.core.model.QuestionType
import com.studysmart.app.core.model.TestAttemptOriginType
import com.studysmart.app.core.model.TestAttemptScopeType
import com.studysmart.app.core.model.TestAttemptStatus
import com.studysmart.app.core.model.TestQuestionSource
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestResultProjectionTest {
    @Test
    fun projectsFrozenEvidenceAndReconstructsWeightedKeywordMatch() {
        val result = TestResultProjector.project(completedModel())

        assertTrue(result is TestResultProjection.Available)
        val report = (result as TestResultProjection.Available).report
        assertEquals(listOf("attempt-question-0", "attempt-question-1", "attempt-question-2"),
            report.questions.map { it.snapshot.id })
        assertEquals(50.0, report.weightedKeywordMatchPercent, 0.000001)
        assertEquals(2, report.evaluableQuestionCount)
        assertEquals(1, report.needsReviewCount)
        assertEquals(3.33, report.score, 0.0)
        assertEquals(6.0, report.maxScore, 0.0)
        assertEquals(55.5, report.percentage, 0.0)
        assertEquals(17L, report.timeTakenSeconds)
        assertEquals("Historical question 0", report.questions[0].snapshot.questionTextSnapshot)
        assertEquals("alpha response", report.questions[0].answer.answerText)
        assertEquals(listOf("alpha"), report.questions[0].matchedKeywords)
        assertEquals(listOf("beta"), report.questions[0].missingKeywords)
    }

    @Test
    fun zeroKeywordQuestionIsNotEvaluableAndContributesZeroToAggregate() {
        val report = (TestResultProjector.project(completedModel(allZeroKeywords = true)) as
            TestResultProjection.Available).report

        assertEquals(0, report.evaluableQuestionCount)
        assertEquals(0.0, report.weightedKeywordMatchPercent, 0.0)
        assertTrue(report.questions.all { it.status == TestQuestionReportStatus.NOT_EVALUABLE })
        assertTrue(report.questions.all { it.matchedKeywords.isEmpty() && it.missingKeywords.isEmpty() })
    }

    @Test
    fun draftOrActiveAttemptIsNotReportedAsCompleted() {
        val active = completedModel().copy(
            attempt = completedModel().attempt.copy(status = TestAttemptStatus.IN_PROGRESS),
        )

        assertEquals(TestResultProjection.NotSubmitted, TestResultProjector.project(active))
    }

    @Test
    fun inconsistentStoredTotalsAreReportedAsCorrupt() {
        val model = completedModel()
        val corrupt = model.copy(attempt = model.attempt.copy(score = 100.0))

        val result = TestResultProjector.project(corrupt)

        assertTrue(result is TestResultProjection.Corrupt)
        assertFalse((result as TestResultProjection.Corrupt).reason.isBlank())
    }

    @Test
    fun malformedEvidenceIsReportedAsCorruptWithoutFallingBackToCurrentContent() {
        val model = completedModel()
        val first = model.answers.first()
        val corrupt = model.copy(
            answers = model.answers.map {
                if (it.id == first.id) it.copy(matchedKeywordsJson = "{}") else it
            },
        )

        assertTrue(TestResultProjector.project(corrupt) is TestResultProjection.Corrupt)
        assertEquals("Historical question 0", model.questions.first().questionTextSnapshot)
    }

    @Test
    fun contradictoryFrozenKeywordEvidenceIsReportedAsCorrupt() {
        val model = completedModel()
        val first = model.answers.first()
        val corrupt = model.copy(
            answers = model.answers.map {
                if (it.id == first.id) {
                    it.copy(
                        matchedKeywordsJson = KeywordEvidenceCodec.encode(listOf("beta")),
                        missingKeywordsJson = KeywordEvidenceCodec.encode(listOf("alpha")),
                    )
                } else {
                    it
                }
            },
        )

        assertTrue(TestResultProjector.project(corrupt) is TestResultProjection.Corrupt)
    }

    @Test
    fun projectsIndependentlyFrozenLegacyAndCaseFoldedUnicodeEvidence() {
        val question = snapshot(0, listOf("straße" to 1.0))
        val original = answer(question, "STRASSE", 0)
        val baseline = completedModel().attempt
        listOf("keyword-v1" to false, "keyword-v2" to true).forEach { (version, matched) ->
            val score = if (matched) 2.0 else 0.0
            val evidence = original.copy(
                normalizedAnswer = "strasse",
                matchedKeywordsJson = if (matched) {
                    "{\"version\":1,\"keywords\":[\"straße\"]}"
                } else {
                    "{\"version\":1,\"keywords\":[]}"
                },
                missingKeywordsJson = if (matched) {
                    "{\"version\":1,\"keywords\":[]}"
                } else {
                    "{\"version\":1,\"keywords\":[\"straße\"]}"
                },
                keywordMatchPercent = if (matched) 100.0 else 0.0,
                score = score, maxScore = 2.0, requiresReview = !matched,
            )
            val model = TestAttemptModel(
                attempt = baseline.copy(
                    requestedQuestionCount = 1,
                    actualQuestionCount = 1,
                    score = score,
                    maxScore = 2.0,
                    percentage = if (matched) 100.0 else 0.0,
                    evaluatorVersion = version,
                ),
                questions = listOf(question),
                answers = listOf(evidence),
            )
            val projection = TestResultProjector.project(model)
            assertTrue(projection is TestResultProjection.Available)
            assertEquals(score, (projection as TestResultProjection.Available).report.score, 0.0)
            assertTrue(
                TestResultProjector.project(
                    model.copy(attempt = model.attempt.copy(evaluatorVersion = "unsupported")),
                ) is TestResultProjection.Corrupt,
            )
            assertTrue(
                TestResultProjector.project(
                    model.copy(attempt = model.attempt.copy(evaluatorVersion = null)),
                ) is TestResultProjection.Corrupt,
            )
        }
    }

    private fun completedModel(allZeroKeywords: Boolean = false): TestAttemptModel {
        val questions = listOf(
            snapshot(0, if (allZeroKeywords) emptyList() else listOf("alpha" to 2.0, "beta" to 1.0)),
            snapshot(1, if (allZeroKeywords) emptyList() else listOf("gamma" to 1.0)),
            snapshot(2, if (allZeroKeywords) emptyList() else emptyList()),
        )
        val answerText = listOf("alpha response", "gamma response", "ungraded text")
        val evaluated = KeywordAnswerEvaluator(KeywordAnswerEvaluator.LEGACY_VERSION).evaluate(
            questions,
            questions.mapIndexed { index, question ->
                answer(question, answerText[index], index)
            },
        )
        val answers = evaluated.answers.mapIndexed { index, value ->
            val original = answer(questions[index], answerText[index], index)
            original.copy(
                normalizedAnswer = value.normalizedAnswer,
                matchedKeywordsJson = KeywordEvidenceCodec.encode(value.matchedKeywords),
                missingKeywordsJson = KeywordEvidenceCodec.encode(value.missingKeywords),
                keywordMatchPercent = value.keywordMatchPercent,
                score = value.score,
                maxScore = value.maxScore,
                requiresReview = value.requiresReview,
            )
        }
        val at = Instant.parse("2026-09-30T10:00:00Z")
        val attempt = TestAttemptEntity(
            id = "attempt-1",
            userId = "local-user",
            courseId = "course-1",
            academicPeriodId = "period-1",
            subjectId = "subject-1",
            chapterId = "chapter-1",
            topicId = null,
            scopeType = TestAttemptScopeType.CHAPTER,
            scopeReferenceId = "chapter-1",
            originType = TestAttemptOriginType.CHAPTER,
            originReferenceId = "chapter-1",
            creationRequestId = "request-1",
            requestedQuestionCount = questions.size,
            actualQuestionCount = questions.size,
            questionSource = TestQuestionSource.ALL,
            selectionSeed = null,
            isTimed = false,
            timeLimitSeconds = null,
            startedAt = at.minusSeconds(17),
            startedAtElapsedRealtimeMillis = 1_000L,
            startedBootId = "boot-1",
            deadlineAt = null,
            submittedAt = at,
            completedAt = at,
            timeTakenSeconds = 17L,
            score = evaluated.totalScore,
            maxScore = evaluated.maxScore,
            percentage = evaluated.percentage,
            status = TestAttemptStatus.SUBMITTED,
            completionReason = "submitted",
            evaluatorVersion = "keyword-v1",
        )
        return TestAttemptModel(attempt, questions, answers)
    }

    private fun snapshot(index: Int, keywords: List<Pair<String, Double>>) =
        TestAttemptQuestionEntity(
            id = "attempt-question-$index",
            testAttemptId = "attempt-1",
            questionId = "question-$index",
            displayOrder = index,
            questionTextSnapshot = "Historical question $index",
            referenceAnswerSnapshot = "Frozen reference answer $index",
            questionTypeSnapshot = QuestionType.WRITTEN,
            maxScoreSnapshot = 2.0,
            keywordSnapshotJson = KeywordSnapshotCodec.encode(
                keywords.map { (keyword, weight) ->
                    KeywordSnapshotEntry(KeywordNormalizer.normalize(keyword), weight)
                },
            ),
            keywordSnapshotVersion = KeywordSnapshotCodec.CURRENT_VERSION,
        )

    private fun answer(
        question: TestAttemptQuestionEntity,
        answerText: String,
        index: Int,
    ) = TestAnswerEntity(
        id = "answer-$index",
        testAttemptQuestionId = question.id,
        answerText = answerText,
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
        updatedAt = Instant.parse("2026-09-30T10:00:00Z"),
    )
}
