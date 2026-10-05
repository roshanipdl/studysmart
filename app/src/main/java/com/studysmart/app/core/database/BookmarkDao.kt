package com.studysmart.app.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.time.Instant
import kotlinx.coroutines.flow.Flow

data class SavedQuestionRow(
    val questionId: String,
    val question: String,
    val courseId: String,
    val courseCode: String,
    val academicPeriodId: String,
    val periodName: String,
    val subjectId: String,
    val subjectCode: String,
    val subjectName: String,
    val chapterId: String,
    val chapterTitle: String,
    val topicId: String?,
    val bookmarkedAt: Instant,
)

@Dao
interface BookmarkDao {
    @Query("SELECT questionId FROM user_question_flags WHERE userId = :userId AND isImportant = 1")
    fun observeBookmarkedIds(userId: String): Flow<List<String>>

    @Query("SELECT * FROM user_question_flags WHERE userId = :userId AND questionId = :questionId LIMIT 1")
    suspend fun getFlag(userId: String, questionId: String): UserQuestionFlagEntity?

    @Upsert
    suspend fun upsertFlag(flag: UserQuestionFlagEntity)

    @Query("DELETE FROM user_question_flags WHERE userId = :userId AND questionId = :questionId")
    suspend fun deleteFlag(userId: String, questionId: String)

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM questions q
            JOIN chapters ch ON ch.id = q.chapterId
            JOIN subjects s ON s.id = ch.subjectId
            JOIN academic_periods p ON p.id = s.academicPeriodId
            JOIN courses c ON c.id = p.courseId
            LEFT JOIN topics t ON t.id = q.topicId
            WHERE q.id = :questionId AND q.isActive = 1 AND ch.isActive = 1
              AND s.isActive = 1 AND p.isActive = 1 AND c.isActive = 1
              AND (q.topicId IS NULL OR (t.id IS NOT NULL AND t.chapterId = ch.id AND t.isActive = 1))
        )
    """)
    suspend fun isActiveQuestion(questionId: String): Boolean

    /** Idempotent explicit writes avoid retrying a failed toggle into its opposite. */
    @Transaction
    suspend fun setBookmarked(userId: String, questionId: String, bookmarked: Boolean, now: Instant): Boolean {
        if (!bookmarked) {
            deleteFlag(userId, questionId)
            return true
        }
        if (!isActiveQuestion(questionId)) return false
        if (getFlag(userId, questionId)?.isImportant != true) {
            upsertFlag(UserQuestionFlagEntity(userId, questionId, true, now))
        }
        return true
    }

    @Query("""
        SELECT q.id AS questionId, q.question, c.id AS courseId, c.code AS courseCode,
               p.id AS academicPeriodId, p.name AS periodName, s.id AS subjectId,
               s.code AS subjectCode, s.name AS subjectName, ch.id AS chapterId,
               ch.title AS chapterTitle, q.topicId, flag.createdAt AS bookmarkedAt
        FROM user_question_flags flag
        JOIN questions q ON q.id = flag.questionId
        JOIN chapters ch ON ch.id = q.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN topics t ON t.id = q.topicId
        WHERE flag.userId = :userId AND flag.isImportant = 1
          AND c.id = :courseId AND p.id = :academicPeriodId
          AND (:subjectId IS NULL OR s.id = :subjectId)
          AND (:chapterId IS NULL OR ch.id = :chapterId)
          AND q.isActive = 1 AND ch.isActive = 1 AND s.isActive = 1
          AND p.isActive = 1 AND c.isActive = 1
          AND (q.topicId IS NULL OR (t.id IS NOT NULL AND t.chapterId = ch.id AND t.isActive = 1))
        ORDER BY flag.createdAt, q.id
    """)
    fun observeSavedQuestions(
        userId: String,
        courseId: String,
        academicPeriodId: String,
        subjectId: String?,
        chapterId: String?,
    ): Flow<List<SavedQuestionRow>>
}
