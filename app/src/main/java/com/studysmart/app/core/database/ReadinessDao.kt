package com.studysmart.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.studysmart.app.core.model.LearningState
import java.time.Instant
import kotlinx.coroutines.flow.Flow

data class ReadinessQuestionRow(
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "learning_state") val learningState: LearningState?,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Instant?,
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Instant?,
    @ColumnInfo(name = "has_committed_rating") val hasCommittedRating: Boolean,
    @ColumnInfo(name = "latest_test_submitted_at") val latestTestSubmittedAt: Instant?,
    @ColumnInfo(name = "latest_test_requires_review") val latestTestRequiresReview: Boolean?,
)

data class ReadinessTestAnswerRow(
    @ColumnInfo(name = "attempt_id") val attemptId: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "submitted_at") val submittedAt: Instant,
    val score: Double?,
    @ColumnInfo(name = "max_score") val maxScore: Double?,
)

@Dao
interface ReadinessDao {
    @Query(
        """
        SELECT q.id AS question_id,
               schedule.learningState AS learning_state,
               schedule.lastReviewedAt AS last_reviewed_at,
               schedule.nextReviewAt AS next_review_at,
               CASE WHEN EXISTS (
                   SELECT 1 FROM review_records rr
                   WHERE rr.userId = :userId AND rr.questionId = q.id
               ) THEN 1 ELSE 0 END AS has_committed_rating,
               latest_attempt.submittedAt AS latest_test_submitted_at,
               latest_answer.requiresReview AS latest_test_requires_review
        FROM questions q
        JOIN chapters ch ON ch.id = q.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN topics t ON t.id = q.topicId
        LEFT JOIN review_schedules schedule
          ON schedule.userId = :userId AND schedule.questionId = q.id
        LEFT JOIN test_attempt_questions latest_question
          ON latest_question.id = (
              SELECT aq.id
              FROM test_attempt_questions aq
              JOIN test_attempts a ON a.id = aq.testAttemptId
              WHERE a.userId = :userId
                AND a.status = 'submitted'
                AND aq.questionId = q.id
              ORDER BY a.submittedAt DESC, a.id DESC
              LIMIT 1
          )
        LEFT JOIN test_answers latest_answer
          ON latest_answer.testAttemptQuestionId = latest_question.id
        LEFT JOIN test_attempts latest_attempt
          ON latest_attempt.id = latest_question.testAttemptId
        WHERE c.id = :courseId
          AND (:academicPeriodId IS NULL OR p.id = :academicPeriodId)
          AND (:subjectId IS NULL OR s.id = :subjectId)
          AND (:chapterId IS NULL OR ch.id = :chapterId)
          AND q.isActive = 1
          AND ch.isActive = 1
          AND s.isActive = 1
          AND p.isActive = 1
          AND c.isActive = 1
          AND (q.topicId IS NULL OR (t.id IS NOT NULL AND t.chapterId = ch.id AND t.isActive = 1))
        ORDER BY c.sortOrder, p.sortOrder, s.sortOrder, ch.sortOrder,
                 CASE WHEN q.topicId IS NULL THEN 0 ELSE 1 END,
                 COALESCE(t.sortOrder, -1), q.sortOrder, q.id
        """,
    )
    fun observeQuestions(
        userId: String,
        courseId: String,
        academicPeriodId: String?,
        subjectId: String?,
        chapterId: String?,
    ): Flow<List<ReadinessQuestionRow>>

    @Query(
        """
        SELECT a.id AS attempt_id,
               aq.questionId AS question_id,
               a.submittedAt AS submitted_at,
               answer.score AS score,
               aq.maxScoreSnapshot AS max_score
        FROM test_attempts a
        JOIN test_attempt_questions aq ON aq.testAttemptId = a.id
        JOIN test_answers answer ON answer.testAttemptQuestionId = aq.id
        JOIN questions q ON q.id = aq.questionId
        JOIN chapters ch ON ch.id = q.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN topics t ON t.id = q.topicId
        WHERE a.userId = :userId
          AND a.status = 'submitted'
          AND a.submittedAt IS NOT NULL
          AND c.id = :courseId
          AND (:academicPeriodId IS NULL OR p.id = :academicPeriodId)
          AND (:subjectId IS NULL OR s.id = :subjectId)
          AND (:chapterId IS NULL OR ch.id = :chapterId)
          AND q.isActive = 1
          AND ch.isActive = 1
          AND s.isActive = 1
          AND p.isActive = 1
          AND c.isActive = 1
          AND (q.topicId IS NULL OR (t.id IS NOT NULL AND t.chapterId = ch.id AND t.isActive = 1))
        ORDER BY a.submittedAt DESC, a.id DESC, aq.displayOrder
        """,
    )
    fun observeSubmittedAnswers(
        userId: String,
        courseId: String,
        academicPeriodId: String?,
        subjectId: String?,
        chapterId: String?,
    ): Flow<List<ReadinessTestAnswerRow>>
}
