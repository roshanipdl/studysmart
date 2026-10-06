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

data class PreparationCandidateRow(
    @Embedded val question: QuestionEntity,
    @ColumnInfo(name = "latest_rating") val latestRating: Rating?,
    @ColumnInfo(name = "has_review") val hasReview: Boolean,
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Instant?,
)

data class PreparationContextRow(
    @ColumnInfo(name = "course_id") val courseId: String,
    @ColumnInfo(name = "course_code") val courseCode: String,
    @ColumnInfo(name = "course_name") val courseName: String,
    @ColumnInfo(name = "academic_period_id") val academicPeriodId: String,
    @ColumnInfo(name = "academic_period_name") val academicPeriodName: String,
    @ColumnInfo(name = "subject_id") val subjectId: String?,
    @ColumnInfo(name = "subject_code") val subjectCode: String?,
    @ColumnInfo(name = "subject_name") val subjectName: String?,
    @ColumnInfo(name = "chapter_id") val chapterId: String?,
    @ColumnInfo(name = "chapter_title") val chapterTitle: String?,
    @ColumnInfo(name = "topic_id") val topicId: String?,
    @ColumnInfo(name = "topic_title") val topicTitle: String?,
)

@Dao
interface PreparationDao {
    @Query(
        """
        SELECT q.*,
               latest.rating AS latest_rating,
               EXISTS(SELECT 1 FROM review_records rr
                      WHERE rr.userId = :userId AND rr.questionId = q.id) AS has_review,
               schedule.nextReviewAt AS next_review_at
        FROM questions q
        JOIN chapters ch ON ch.id = q.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN topics t ON t.id = q.topicId
        LEFT JOIN review_schedules schedule
          ON schedule.questionId = q.id AND schedule.userId = :userId
        LEFT JOIN review_records latest
          ON latest.questionId = q.id AND latest.userId = :userId
         AND NOT EXISTS (
             SELECT 1 FROM review_records newer
             WHERE newer.questionId = latest.questionId AND newer.userId = latest.userId
               AND (newer.reviewedAt > latest.reviewedAt OR
                    (newer.reviewedAt = latest.reviewedAt AND newer.id > latest.id))
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
    suspend fun getChapterCandidates(userId: String, chapterId: String): List<PreparationCandidateRow>

    @Query(
        """
        SELECT q.*,
               latest.rating AS latest_rating,
               CASE WHEN latest.id IS NULL THEN 0 ELSE 1 END AS has_review,
               schedule.nextReviewAt AS next_review_at
        FROM questions q
        JOIN topics t ON t.id = q.topicId
        JOIN chapters ch ON ch.id = q.chapterId AND ch.id = t.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN review_schedules schedule
          ON schedule.questionId = q.id AND schedule.userId = :userId
        LEFT JOIN review_records latest
          ON latest.id = (
              SELECT rr.id
              FROM review_records rr
              WHERE rr.userId = :userId AND rr.questionId = q.id
              ORDER BY rr.reviewedAt DESC, rr.id DESC
              LIMIT 1
          )
        WHERE q.topicId = :topicId
          AND q.isActive = 1
          AND t.isActive = 1
          AND ch.isActive = 1
          AND s.isActive = 1
          AND p.isActive = 1
          AND c.isActive = 1
        ORDER BY q.sortOrder, q.id
        """,
    )
    suspend fun getTopicCandidates(userId: String, topicId: String): List<PreparationCandidateRow>

    @Query(
        """
        SELECT q.*,
               latest.rating AS latest_rating,
               CASE WHEN latest.id IS NULL THEN 0 ELSE 1 END AS has_review,
               schedule.nextReviewAt AS next_review_at
        FROM questions q
        JOIN chapters ch ON ch.id = q.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN topics t ON t.id = q.topicId
        JOIN review_schedules schedule
          ON schedule.questionId = q.id AND schedule.userId = :userId
        LEFT JOIN review_records latest
          ON latest.id = (
              SELECT rr.id
              FROM review_records rr
              WHERE rr.userId = :userId AND rr.questionId = q.id
              ORDER BY rr.reviewedAt DESC, rr.id DESC
              LIMIT 1
          )
        WHERE c.id = :courseId
          AND p.id = :academicPeriodId
          AND schedule.nextReviewAt IS NOT NULL
          AND schedule.nextReviewAt <= :now
          AND q.isActive = 1
          AND ch.isActive = 1
          AND s.isActive = 1
          AND p.isActive = 1
          AND c.isActive = 1
          AND (q.topicId IS NULL OR (t.id IS NOT NULL AND t.chapterId = ch.id AND t.isActive = 1))
        ORDER BY schedule.nextReviewAt,
                 c.sortOrder,
                 p.sortOrder,
                 s.sortOrder,
                 ch.sortOrder,
                 CASE WHEN q.topicId IS NULL THEN 0 ELSE 1 END,
                 COALESCE(t.sortOrder, -1),
                 q.sortOrder,
                 q.id
        """,
    )
    fun observeDueCandidates(
        userId: String,
        courseId: String,
        academicPeriodId: String,
        now: Instant,
    ): Flow<List<PreparationCandidateRow>>

    @Query(
        """
        SELECT q.*,
               latest.rating AS latest_rating,
               CASE WHEN latest.id IS NULL THEN 0 ELSE 1 END AS has_review,
               schedule.nextReviewAt AS next_review_at
        FROM questions q
        JOIN chapters ch ON ch.id = q.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN topics t ON t.id = q.topicId
        JOIN review_schedules schedule
          ON schedule.questionId = q.id AND schedule.userId = :userId
        LEFT JOIN review_records latest
          ON latest.id = (
              SELECT rr.id
              FROM review_records rr
              WHERE rr.userId = :userId AND rr.questionId = q.id
              ORDER BY rr.reviewedAt DESC, rr.id DESC
              LIMIT 1
          )
        WHERE c.id = :courseId
          AND p.id = :academicPeriodId
          AND schedule.nextReviewAt IS NOT NULL
          AND schedule.nextReviewAt <= :now
          AND q.isActive = 1
          AND ch.isActive = 1
          AND s.isActive = 1
          AND p.isActive = 1
          AND c.isActive = 1
          AND (q.topicId IS NULL OR (t.id IS NOT NULL AND t.chapterId = ch.id AND t.isActive = 1))
        ORDER BY schedule.nextReviewAt,
                 c.sortOrder,
                 p.sortOrder,
                 s.sortOrder,
                 ch.sortOrder,
                 CASE WHEN q.topicId IS NULL THEN 0 ELSE 1 END,
                 COALESCE(t.sortOrder, -1),
                 q.sortOrder,
                 q.id
        """,
    )
    suspend fun getDueCandidates(
        userId: String,
        courseId: String,
        academicPeriodId: String,
        now: Instant,
    ): List<PreparationCandidateRow>

    @Query(
        """
        SELECT c.id AS course_id,
               c.code AS course_code,
               c.name AS course_name,
               p.id AS academic_period_id,
               p.name AS academic_period_name,
               s.id AS subject_id,
               s.code AS subject_code,
               s.name AS subject_name,
               ch.id AS chapter_id,
               ch.title AS chapter_title,
               CAST(NULL AS TEXT) AS topic_id,
               CAST(NULL AS TEXT) AS topic_title
        FROM chapters ch
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        WHERE ch.id = :chapterId
          AND ch.isActive = 1 AND s.isActive = 1 AND p.isActive = 1 AND c.isActive = 1
        LIMIT 1
        """,
    )
    suspend fun getChapterContext(chapterId: String): PreparationContextRow?

    @Query(
        """
        SELECT c.id AS course_id,
               c.code AS course_code,
               c.name AS course_name,
               p.id AS academic_period_id,
               p.name AS academic_period_name,
               s.id AS subject_id,
               s.code AS subject_code,
               s.name AS subject_name,
               ch.id AS chapter_id,
               ch.title AS chapter_title,
               t.id AS topic_id,
               t.title AS topic_title
        FROM topics t
        JOIN chapters ch ON ch.id = t.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        WHERE t.id = :topicId
          AND t.isActive = 1 AND ch.isActive = 1 AND s.isActive = 1
          AND p.isActive = 1 AND c.isActive = 1
        LIMIT 1
        """,
    )
    suspend fun getTopicContext(topicId: String): PreparationContextRow?

    @Query(
        """
        SELECT c.id AS course_id,
               c.code AS course_code,
               c.name AS course_name,
               p.id AS academic_period_id,
               p.name AS academic_period_name,
               CAST(NULL AS TEXT) AS subject_id,
               CAST(NULL AS TEXT) AS subject_code,
               CAST(NULL AS TEXT) AS subject_name,
               CAST(NULL AS TEXT) AS chapter_id,
               CAST(NULL AS TEXT) AS chapter_title,
               CAST(NULL AS TEXT) AS topic_id,
               CAST(NULL AS TEXT) AS topic_title
        FROM academic_periods p
        JOIN courses c ON c.id = p.courseId
        WHERE c.id = :courseId
          AND p.id = :academicPeriodId
          AND p.isActive = 1
          AND c.isActive = 1
        LIMIT 1
        """,
    )
    suspend fun getCoursePeriodContext(courseId: String, academicPeriodId: String): PreparationContextRow?

    @Query("""
        SELECT c.id AS course_id, c.code AS course_code, c.name AS course_name,
               p.id AS academic_period_id, p.name AS academic_period_name,
               s.id AS subject_id, s.code AS subject_code, s.name AS subject_name,
               ch.id AS chapter_id, ch.title AS chapter_title,
               CAST(NULL AS TEXT) AS topic_id, CAST(NULL AS TEXT) AS topic_title
        FROM academic_periods p JOIN courses c ON c.id = p.courseId
        LEFT JOIN subjects s ON s.id = :subjectId AND s.academicPeriodId = p.id AND s.isActive = 1
        LEFT JOIN chapters ch ON ch.id = :chapterId AND ch.subjectId = s.id AND ch.isActive = 1
        WHERE c.id = :courseId AND p.id = :academicPeriodId AND c.isActive = 1 AND p.isActive = 1
          AND (:subjectId IS NULL OR s.id IS NOT NULL)
          AND (:chapterId IS NULL OR ch.id IS NOT NULL)
        LIMIT 1
    """)
    suspend fun getImportantContext(courseId: String, academicPeriodId: String, subjectId: String?, chapterId: String?): PreparationContextRow?

    @Query("SELECT * FROM preparation_sessions WHERE creationRequestId = :requestId LIMIT 1")
    suspend fun getSessionByCreationRequestId(requestId: String): PreparationSessionEntity?

    @Query("SELECT * FROM preparation_sessions WHERE id = :sessionId LIMIT 1")
    fun observeSession(sessionId: String): Flow<PreparationSessionEntity?>

    @Query("SELECT * FROM preparation_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): PreparationSessionEntity?

    @Query(
        "SELECT * FROM preparation_session_items " +
            "WHERE sessionId = :sessionId ORDER BY queueSequence, originalOrder, id",
    )
    fun observeSessionItems(sessionId: String): Flow<List<PreparationSessionItemEntity>>

    @Query(
        "SELECT * FROM preparation_session_items " +
            "WHERE sessionId = :sessionId ORDER BY queueSequence, originalOrder, id",
    )
    suspend fun getSessionItems(sessionId: String): List<PreparationSessionItemEntity>

    @Query(
        "SELECT * FROM review_records " +
            "WHERE preparationSessionId = :sessionId ORDER BY reviewedAt, id",
    )
    fun observeSessionReviewRecords(sessionId: String): Flow<List<ReviewRecordEntity>>

    @Query(
        "SELECT * FROM review_records " +
            "WHERE preparationSessionId = :sessionId ORDER BY reviewedAt, id",
    )
    suspend fun getSessionReviewRecords(sessionId: String): List<ReviewRecordEntity>

    @Query(
        """
        SELECT c.id AS course_id,
               c.code AS course_code,
               c.name AS course_name,
               p.id AS academic_period_id,
               p.name AS academic_period_name,
               s.id AS subject_id,
               s.code AS subject_code,
               s.name AS subject_name,
               ch.id AS chapter_id,
               ch.title AS chapter_title,
               t.id AS topic_id,
               t.title AS topic_title
        FROM preparation_sessions ps
        JOIN courses c ON c.id = ps.courseId
        JOIN academic_periods p ON p.id = ps.academicPeriodId
         LEFT JOIN subjects s ON s.id = ps.subjectId
         LEFT JOIN chapters ch ON ch.id = ps.chapterId
         LEFT JOIN topics t ON t.id = ps.topicId
        WHERE ps.id = :sessionId
        LIMIT 1
        """,
    )
    fun observeSessionContext(sessionId: String): Flow<PreparationContextRow?>

    @Query("SELECT * FROM review_records WHERE id = :recordId LIMIT 1")
    suspend fun getReviewRecord(recordId: String): ReviewRecordEntity?

    @Query(
        "SELECT * FROM review_schedules WHERE userId = :userId AND questionId = :questionId LIMIT 1",
    )
    suspend fun getReviewSchedule(userId: String, questionId: String): ReviewScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: PreparationSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessionItems(items: List<PreparationSessionItemEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReviewRecord(record: ReviewRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReviewSchedule(schedule: ReviewScheduleEntity)

    @Update
    suspend fun updateSession(session: PreparationSessionEntity)

    @Update
    suspend fun updateSessionItems(items: List<PreparationSessionItemEntity>)

    @Query("UPDATE preparation_session_items SET isRevealed = 1 WHERE id = :itemId AND isCompleted = 0")
    suspend fun revealItem(itemId: String): Int

    @Query("DELETE FROM preparation_sessions WHERE id = :sessionId AND status = 'active'")
    suspend fun deleteActiveSession(sessionId: String): Int

    @Query("DELETE FROM preparation_sessions WHERE status = 'active'")
    suspend fun deleteActiveSessions(): Int
}
