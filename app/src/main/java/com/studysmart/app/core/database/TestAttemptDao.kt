package com.studysmart.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
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

data class TestAttemptWithQuestionRows(
    @Embedded val attempt: TestAttemptEntity,
    @Relation(
        entity = TestAttemptQuestionEntity::class,
        parentColumn = "id",
        entityColumn = "testAttemptId",
    )
    val questionRows: List<TestAttemptQuestionWithAnswerRows>,
)

data class TestAttemptQuestionWithAnswerRows(
    @Embedded val question: TestAttemptQuestionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "testAttemptQuestionId",
    )
    val answerRows: List<TestAnswerEntity>,
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
               CASE WHEN flag.questionId IS NULL THEN 0 ELSE 1 END AS is_important,
               CAST(NULL AS INTEGER) AS bookmark_created_at
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
          AND (:subjectId IS NULL OR s.id = :subjectId)
          AND (:chapterId IS NULL OR ch.id = :chapterId)
          AND (q.topicId IS NULL OR (t.id IS NOT NULL AND t.chapterId = ch.id AND t.isActive = 1))
        ORDER BY flag.createdAt, q.id
        """,
    )
    suspend fun getImportantCandidates(
        userId: String,
        courseId: String,
        academicPeriodId: String,
        subjectId: String? = null,
        chapterId: String? = null,
    ): List<TestCandidateRow>

    @Query(
        """
        SELECT q.*,
               CAST(NULL AS TEXT) AS latest_rating,
               0 AS has_review,
               CAST(NULL AS REAL) AS latest_test_percentage,
               CAST(NULL AS INTEGER) AS latest_test_requires_review,
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
        WHERE q.id IN (:questionIds)
          AND c.id = :courseId AND p.id = :academicPeriodId
          AND (:chapterId IS NULL OR ch.id = :chapterId)
          AND q.isActive = 1 AND ch.isActive = 1 AND s.isActive = 1
          AND (:subjectId IS NULL OR s.id = :subjectId)
          AND p.isActive = 1 AND c.isActive = 1
          AND (q.topicId IS NULL OR (t.id IS NOT NULL AND t.chapterId = ch.id AND t.isActive = 1))
        ORDER BY q.sortOrder, q.id
        """,
    )
    suspend fun getActiveCandidatesForQuestionIds(
        userId: String,
        courseId: String,
        academicPeriodId: String,
        chapterId: String?,
        questionIds: List<String>,
        subjectId: String? = null,
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

    @Transaction
    @Query("SELECT * FROM test_attempts WHERE id = :attemptId LIMIT 1")
    fun observeAttemptWithQuestions(attemptId: String): Flow<TestAttemptWithQuestionRows?>

    @Transaction
    @Query("SELECT * FROM test_attempts WHERE id = :attemptId LIMIT 1")
    suspend fun getAttemptWithQuestions(attemptId: String): TestAttemptWithQuestionRows?

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

    @Query(
        "SELECT answer.* FROM test_answers answer JOIN test_attempt_questions question " +
            "ON question.id = answer.testAttemptQuestionId " +
            "WHERE question.testAttemptId = :attemptId AND question.id = :attemptQuestionId LIMIT 1",
    )
    suspend fun getAnswerForAttemptQuestion(
        attemptId: String,
        attemptQuestionId: String,
    ): TestAnswerEntity?

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
        "UPDATE test_attempts SET status = 'in_progress', startedAt = :startedAt, " +
            "startedAtElapsedRealtimeMillis = :startedAtElapsedRealtimeMillis, " +
            "startedBootId = :startedBootId, deadlineAt = :deadlineAt " +
            "WHERE id = :attemptId AND status = 'created'",
    )
    suspend fun startAttempt(
        attemptId: String,
        startedAt: Instant,
        startedAtElapsedRealtimeMillis: Long,
        startedBootId: String?,
        deadlineAt: Instant?,
    ): Int

    @Query(
        "UPDATE test_answers SET answerText = :answerText, isFlagged = :isFlagged, " +
            "accumulatedTimeMillis = :accumulatedTimeMillis, " +
            "timeSpentSeconds = :timeSpentSeconds, updatedAt = :updatedAt " +
            "WHERE testAttemptQuestionId = :attemptQuestionId AND EXISTS (" +
            "SELECT 1 FROM test_attempt_questions q JOIN test_attempts a " +
            "ON a.id = q.testAttemptId WHERE q.id = :attemptQuestionId " +
            "AND a.id = :attemptId AND a.status = 'in_progress')",
    )
    suspend fun updateAnswerDraft(
        attemptId: String,
        attemptQuestionId: String,
        answerText: String,
        isFlagged: Boolean,
        accumulatedTimeMillis: Long,
        timeSpentSeconds: Long,
        updatedAt: Instant,
    ): Int

    @Query(
        "UPDATE test_attempts SET status = 'submitting' " +
            "WHERE id = :attemptId AND status = 'in_progress'",
    )
    suspend fun claimAttemptForSubmission(attemptId: String): Int

    @Query(
        "UPDATE test_answers SET normalizedAnswer = :normalizedAnswer, " +
            "matchedKeywordsJson = :matchedKeywordsJson, missingKeywordsJson = :missingKeywordsJson, " +
            "keywordMatchPercent = :keywordMatchPercent, score = :score, maxScore = :maxScore, " +
            "requiresReview = :requiresReview, updatedAt = :updatedAt " +
            "WHERE testAttemptQuestionId = :attemptQuestionId AND EXISTS (" +
            "SELECT 1 FROM test_attempt_questions q JOIN test_attempts a " +
            "ON a.id = q.testAttemptId WHERE q.id = :attemptQuestionId " +
            "AND a.id = :attemptId AND a.status = 'submitting')",
    )
    suspend fun updateAnswerEvaluation(
        attemptId: String,
        attemptQuestionId: String,
        normalizedAnswer: String,
        matchedKeywordsJson: String,
        missingKeywordsJson: String,
        keywordMatchPercent: Double?,
        score: Double,
        maxScore: Double,
        requiresReview: Boolean,
        updatedAt: Instant,
    ): Int

    @Query(
        "UPDATE test_attempts SET submittedAt = :submittedAt, completedAt = :submittedAt, " +
            "timeTakenSeconds = :timeTakenSeconds, score = :score, maxScore = :maxScore, " +
            "percentage = :percentage, completionReason = :completionReason, " +
            "evaluatorVersion = :evaluatorVersion, status = 'submitted' " +
            "WHERE id = :attemptId AND status = 'submitting'",
    )
    suspend fun completeSubmission(
        attemptId: String,
        submittedAt: Instant,
        timeTakenSeconds: Long,
        score: Double,
        maxScore: Double,
        percentage: Double,
        completionReason: String,
        evaluatorVersion: String,
    ): Int

    @Query(
        "DELETE FROM test_attempts WHERE id = :attemptId " +
            "AND status IN ('created', 'in_progress', 'submitting')",
    )
    suspend fun deleteActiveAttempt(attemptId: String): Int

    @Query("DELETE FROM test_attempts WHERE status IN ('created', 'in_progress', 'submitting')")
    suspend fun deleteActiveAttempts(): Int
}
