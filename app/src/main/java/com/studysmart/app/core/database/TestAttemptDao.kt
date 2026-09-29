package com.studysmart.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.studysmart.app.core.model.Rating
import java.time.Instant
import kotlinx.coroutines.flow.Flow

data class TestCandidateRow(
    @Embedded val question: QuestionEntity,
    @ColumnInfo(name = "latest_rating") val latestRating: Rating?,
    @ColumnInfo(name = "has_review") val hasReview: Boolean,
    @ColumnInfo(name = "latest_test_percentage") val latestTestPercentage: Double?,
    @ColumnInfo(name = "latest_test_requires_review") val latestTestRequiresReview: Boolean?,
    @ColumnInfo(name = "is_important") val isImportant: Boolean,
    @ColumnInfo(name = "bookmark_created_at") val bookmarkCreatedAt: Instant?,
)

data class TestAttemptContextRow(
    @ColumnInfo(name = "course_id") val courseId: String,
    @ColumnInfo(name = "academic_period_id") val academicPeriodId: String,
    @ColumnInfo(name = "subject_id") val subjectId: String?,
    @ColumnInfo(name = "chapter_id") val chapterId: String?,
    @ColumnInfo(name = "topic_id") val topicId: String?,
)

@Dao
interface TestAttemptDao {
    @Query(
        """
        SELECT q.*,
               latest.rating AS latest_rating,
               CASE WHEN latest.id IS NULL THEN 0 ELSE 1 END AS has_review,
               latest_test_answer.keywordMatchPercent AS latest_test_percentage,
               latest_test_answer.requiresReview AS latest_test_requires_review,
               0 AS is_important,
               CAST(NULL AS INTEGER) AS bookmark_created_at
        FROM questions q
        JOIN chapters ch ON ch.id = q.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN topics t ON t.id = q.topicId
        LEFT JOIN review_records latest
          ON latest.id = (
              SELECT rr.id
              FROM review_records rr
              WHERE rr.userId = :userId AND rr.questionId = q.id
              ORDER BY rr.reviewedAt DESC, rr.id DESC
              LIMIT 1
          )
        LEFT JOIN test_answers latest_test_answer
          ON latest_test_answer.id = (
              SELECT answer.id
              FROM test_answers answer
              JOIN test_attempt_questions attempt_question
                ON attempt_question.id = answer.testAttemptQuestionId
              JOIN test_attempts attempt
                ON attempt.id = attempt_question.testAttemptId
              WHERE attempt.userId = :userId
                AND attempt.status = 'submitted'
                AND attempt_question.questionId = q.id
              ORDER BY COALESCE(attempt.submittedAt, attempt.completedAt) DESC,
                       attempt.id DESC
              LIMIT 1
          )
        WHERE q.chapterId = :chapterId
          AND q.isActive = 1
          AND ch.isActive = 1
          AND s.isActive = 1
          AND p.isActive = 1
          AND c.isActive = 1
          AND (q.topicId IS NULL OR (t.id IS NOT NULL AND t.chapterId = ch.id AND t.isActive = 1))
        ORDER BY q.sortOrder, q.id
        """,
    )
    suspend fun getChapterCandidates(userId: String, chapterId: String): List<TestCandidateRow>

    @Query(
        """
        SELECT q.*,
               latest.rating AS latest_rating,
               CASE WHEN latest.id IS NULL THEN 0 ELSE 1 END AS has_review,
               latest_test_answer.keywordMatchPercent AS latest_test_percentage,
               latest_test_answer.requiresReview AS latest_test_requires_review,
               CASE WHEN flag.questionId IS NULL THEN 0 ELSE 1 END AS is_important,
               flag.createdAt AS bookmark_created_at
        FROM questions q
        JOIN chapters ch ON ch.id = q.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN topics t ON t.id = q.topicId
        LEFT JOIN user_question_flags flag
          ON flag.userId = :userId AND flag.questionId = q.id AND flag.isImportant = 1
        LEFT JOIN review_records latest
          ON latest.id = (
              SELECT rr.id
              FROM review_records rr
              WHERE rr.userId = :userId AND rr.questionId = q.id
              ORDER BY rr.reviewedAt DESC, rr.id DESC
              LIMIT 1
          )
        LEFT JOIN test_answers latest_test_answer
          ON latest_test_answer.id = (
              SELECT answer.id
              FROM test_answers answer
              JOIN test_attempt_questions attempt_question
                ON attempt_question.id = answer.testAttemptQuestionId
              JOIN test_attempts attempt
                ON attempt.id = attempt_question.testAttemptId
              WHERE attempt.userId = :userId
                AND attempt.status = 'submitted'
                AND attempt_question.questionId = q.id
              ORDER BY COALESCE(attempt.submittedAt, attempt.completedAt) DESC,
                       attempt.id DESC
              LIMIT 1
          )
        WHERE c.id = :courseId
          AND p.id = :academicPeriodId
          AND q.isActive = 1
          AND ch.isActive = 1
          AND s.isActive = 1
          AND p.isActive = 1
          AND c.isActive = 1
          AND flag.questionId IS NOT NULL
          AND (q.topicId IS NULL OR (t.id IS NOT NULL AND t.chapterId = ch.id AND t.isActive = 1))
        ORDER BY flag.createdAt, q.id
        """,
    )
    suspend fun getImportantCandidates(
        userId: String,
        courseId: String,
        academicPeriodId: String,
    ): List<TestCandidateRow>

    @Query(
        """
        SELECT c.id AS course_id,
               p.id AS academic_period_id,
               s.id AS subject_id,
               ch.id AS chapter_id,
               CAST(NULL AS TEXT) AS topic_id
        FROM chapters ch
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        WHERE ch.id = :chapterId
          AND ch.isActive = 1 AND s.isActive = 1
          AND p.isActive = 1 AND c.isActive = 1
        LIMIT 1
        """,
    )
    suspend fun getChapterContext(chapterId: String): TestAttemptContextRow?

    @Query(
        """
        SELECT c.id AS course_id,
               p.id AS academic_period_id,
               CAST(NULL AS TEXT) AS subject_id,
               CAST(NULL AS TEXT) AS chapter_id,
               CAST(NULL AS TEXT) AS topic_id
        FROM academic_periods p
        JOIN courses c ON c.id = p.courseId
        WHERE c.id = :courseId AND p.id = :academicPeriodId
          AND c.isActive = 1 AND p.isActive = 1
        LIMIT 1
        """,
    )
    suspend fun getCoursePeriodContext(
        courseId: String,
        academicPeriodId: String,
    ): TestAttemptContextRow?

    @Query(
        "SELECT * FROM question_keywords WHERE questionId = :questionId ORDER BY sortOrder, normalizedKeyword",
    )
    suspend fun getQuestionKeywords(questionId: String): List<QuestionKeywordEntity>

    @Query("SELECT * FROM test_attempts WHERE creationRequestId = :requestId LIMIT 1")
    suspend fun getAttemptByCreationRequestId(requestId: String): TestAttemptEntity?

    @Query("SELECT * FROM test_attempts WHERE id = :attemptId LIMIT 1")
    fun observeAttempt(attemptId: String): Flow<TestAttemptEntity?>

    @Query("SELECT * FROM test_attempts WHERE id = :attemptId LIMIT 1")
    suspend fun getAttempt(attemptId: String): TestAttemptEntity?

    @Query(
        "SELECT * FROM test_attempt_questions WHERE testAttemptId = :attemptId ORDER BY displayOrder",
    )
    fun observeAttemptQuestions(attemptId: String): Flow<List<TestAttemptQuestionEntity>>

    @Query(
        "SELECT * FROM test_answers WHERE testAttemptQuestionId IN " +
            "(SELECT id FROM test_attempt_questions WHERE testAttemptId = :attemptId)",
    )
    fun observeAttemptAnswers(attemptId: String): Flow<List<TestAnswerEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttempt(attempt: TestAttemptEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttemptQuestions(questions: List<TestAttemptQuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAnswers(answers: List<TestAnswerEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuestionKeywords(keywords: List<QuestionKeywordEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuestionFlag(flag: UserQuestionFlagEntity)

    @Update
    suspend fun updateAttempt(attempt: TestAttemptEntity)

    @Query(
        "DELETE FROM test_attempts WHERE id = :attemptId " +
            "AND status IN ('created', 'in_progress', 'submitting')",
    )
    suspend fun deleteActiveAttempt(attemptId: String): Int

    @Query("DELETE FROM test_attempts WHERE status IN ('created', 'in_progress', 'submitting')")
    suspend fun deleteActiveAttempts(): Int
}
