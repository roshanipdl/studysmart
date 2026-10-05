package com.studysmart.app.core.database

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ChapterDetailContextRow(
    val chapterId: String,
    val chapterTitle: String,
    val subjectId: String,
    val subjectCode: String,
    val courseCode: String,
    val periodName: String,
)

data class ChapterAcademicParent(
    val subjectId: String?,
    val academicPeriodId: String?,
    val courseId: String?,
)

@Dao
interface ChapterDetailDao {
    @Query("""
        SELECT CASE WHEN s.isActive = 1 AND p.isActive = 1 AND c.isActive = 1 THEN s.id END AS subjectId,
               CASE WHEN p.isActive = 1 AND c.isActive = 1 THEN p.id END AS academicPeriodId,
               CASE WHEN c.isActive = 1 THEN c.id END AS courseId
        FROM chapters ch
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        WHERE ch.id = :chapterId LIMIT 1
    """)
    suspend fun getAcademicParent(chapterId: String): ChapterAcademicParent?

    @Query("""
        SELECT ch.id AS chapterId, ch.title AS chapterTitle, s.id AS subjectId,
               s.code AS subjectCode, c.code AS courseCode, p.name AS periodName
        FROM chapters ch
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        WHERE ch.id = :chapterId AND ch.isActive = 1 AND s.isActive = 1
          AND p.isActive = 1 AND c.isActive = 1
        LIMIT 1
    """)
    fun observeContext(chapterId: String): Flow<ChapterDetailContextRow?>

    @Query("SELECT * FROM topics WHERE chapterId = :chapterId AND isActive = 1 ORDER BY sortOrder, id")
    fun observeTopics(chapterId: String): Flow<List<TopicEntity>>

    @Query("""
        SELECT q.* FROM questions q
        JOIN chapters ch ON ch.id = q.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN topics t ON t.id = q.topicId
        WHERE q.chapterId = :chapterId AND q.isActive = 1 AND ch.isActive = 1
          AND s.isActive = 1 AND p.isActive = 1 AND c.isActive = 1
          AND (q.topicId IS NULL OR (t.id IS NOT NULL AND t.chapterId = ch.id AND t.isActive = 1))
        ORDER BY q.sortOrder, q.id
    """)
    fun observeQuestions(chapterId: String): Flow<List<QuestionEntity>>
}
