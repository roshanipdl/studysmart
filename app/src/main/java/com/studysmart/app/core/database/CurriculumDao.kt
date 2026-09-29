package com.studysmart.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CourseBrowseRow(
    @Embedded val course: CourseEntity,
    @ColumnInfo(name = "period_count") val periodCount: Int,
)

data class AcademicPeriodBrowseRow(
    @Embedded val period: AcademicPeriodEntity,
    @ColumnInfo(name = "subject_count") val subjectCount: Int,
)

data class SubjectBrowseRow(
    @Embedded val subject: SubjectEntity,
    @ColumnInfo(name = "chapter_count") val chapterCount: Int,
)

data class ChapterBrowseRow(
    @Embedded val chapter: ChapterEntity,
    @ColumnInfo(name = "question_count") val questionCount: Int,
)

data class AcademicPeriodContextRow(
    @Embedded val period: AcademicPeriodEntity,
    @ColumnInfo(name = "course_code") val courseCode: String,
    @ColumnInfo(name = "course_name") val courseName: String,
    @ColumnInfo(name = "course_short_name") val courseShortName: String,
    @ColumnInfo(name = "course_structure_type") val courseStructureType: com.studysmart.app.core.model.AcademicPeriodType,
    @ColumnInfo(name = "course_period_count") val coursePeriodCount: Int,
    @ColumnInfo(name = "subject_count") val subjectCount: Int,
)

data class SubjectContextRow(
    @Embedded val subject: SubjectEntity,
    @ColumnInfo(name = "course_id") val courseId: String,
    @ColumnInfo(name = "period_name") val periodName: String,
    @ColumnInfo(name = "period_short_name") val periodShortName: String,
    @ColumnInfo(name = "period_number") val periodNumber: Int,
    @ColumnInfo(name = "period_type") val periodType: com.studysmart.app.core.model.AcademicPeriodType,
    @ColumnInfo(name = "course_code") val courseCode: String,
    @ColumnInfo(name = "course_name") val courseName: String,
    @ColumnInfo(name = "course_short_name") val courseShortName: String,
    @ColumnInfo(name = "course_structure_type") val courseStructureType: com.studysmart.app.core.model.AcademicPeriodType,
    @ColumnInfo(name = "course_period_count") val coursePeriodCount: Int,
    @ColumnInfo(name = "subject_count") val subjectCount: Int,
    @ColumnInfo(name = "chapter_count") val chapterCount: Int,
)

@Dao
interface CurriculumDao {
    @Query(
        """
        SELECT c.*, COUNT(p.id) AS period_count
        FROM courses c
        LEFT JOIN academic_periods p
          ON p.courseId = c.id AND p.isActive = 1
        WHERE c.isActive = 1
        GROUP BY c.id
        ORDER BY c.sortOrder, c.id
        """,
    )
    fun observeActiveCourses(): Flow<List<CourseBrowseRow>>

    @Query("SELECT * FROM courses WHERE id = :courseId AND isActive = 1 LIMIT 1")
    fun observeActiveCourse(courseId: String): Flow<CourseEntity?>

    @Query(
        """
        SELECT p.*, COUNT(s.id) AS subject_count
        FROM academic_periods p
        LEFT JOIN subjects s
          ON s.academicPeriodId = p.id AND s.isActive = 1
        JOIN courses c ON c.id = p.courseId
        WHERE p.courseId = :courseId AND p.isActive = 1 AND c.isActive = 1
        GROUP BY p.id
        ORDER BY p.sortOrder, p.id
        """,
    )
    fun observeActivePeriods(courseId: String): Flow<List<AcademicPeriodBrowseRow>>

    @Query(
        """
        SELECT p.*,
               c.code AS course_code,
               c.name AS course_name,
               c.shortName AS course_short_name,
                c.structureType AS course_structure_type,
                (SELECT COUNT(*) FROM academic_periods p2
                   WHERE p2.courseId = c.id AND p2.isActive = 1) AS course_period_count,
                (SELECT COUNT(*) FROM subjects s2
                   WHERE s2.academicPeriodId = p.id AND s2.isActive = 1) AS subject_count
         FROM academic_periods p
         JOIN courses c ON c.id = p.courseId
         WHERE p.id = :periodId AND p.isActive = 1 AND c.isActive = 1
        LIMIT 1
        """,
    )
    fun observeActiveAcademicPeriod(periodId: String): Flow<AcademicPeriodContextRow?>

    @Query(
        """
        SELECT s.*, COUNT(ch.id) AS chapter_count
        FROM subjects s
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN chapters ch
          ON ch.subjectId = s.id AND ch.isActive = 1
        WHERE s.academicPeriodId = :academicPeriodId
          AND s.isActive = 1 AND p.isActive = 1 AND c.isActive = 1
        GROUP BY s.id
        ORDER BY s.sortOrder, s.id
        """,
    )
    fun observeActiveSubjects(academicPeriodId: String): Flow<List<SubjectBrowseRow>>

    @Query(
        """
        SELECT s.*,
                c.id AS course_id,
                p.name AS period_name,
                p.shortName AS period_short_name,
                p.periodNumber AS period_number,
                p.periodType AS period_type,
                c.code AS course_code,
                c.name AS course_name,
                c.shortName AS course_short_name,
                c.structureType AS course_structure_type,
                (SELECT COUNT(*) FROM academic_periods p2
                   WHERE p2.courseId = c.id AND p2.isActive = 1) AS course_period_count,
                (SELECT COUNT(*) FROM subjects s2
                   WHERE s2.academicPeriodId = p.id AND s2.isActive = 1) AS subject_count,
                (SELECT COUNT(*) FROM chapters ch2
                   WHERE ch2.subjectId = s.id AND ch2.isActive = 1) AS chapter_count
         FROM subjects s
         JOIN academic_periods p ON p.id = s.academicPeriodId
         JOIN courses c ON c.id = p.courseId
         WHERE s.id = :subjectId
           AND s.isActive = 1 AND p.isActive = 1 AND c.isActive = 1
        LIMIT 1
        """,
    )
    fun observeActiveSubject(subjectId: String): Flow<SubjectContextRow?>

    @Query(
        """
        SELECT ch.*,
               COUNT(q.id) AS question_count
        FROM chapters ch
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN questions q
           ON q.chapterId = ch.id
          AND q.isActive = 1
          AND (q.topicId IS NULL OR EXISTS (
              SELECT 1 FROM topics t WHERE t.id = q.topicId AND t.isActive = 1
          ))
        WHERE ch.subjectId = :subjectId
          AND ch.isActive = 1 AND s.isActive = 1
          AND p.isActive = 1 AND c.isActive = 1
        GROUP BY ch.id
        ORDER BY ch.sortOrder, ch.id
        """,
    )
    fun observeActiveChapters(subjectId: String): Flow<List<ChapterBrowseRow>>

    @Query(
        """
        SELECT q.*
        FROM questions q
        JOIN chapters ch ON ch.id = q.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN topics t ON t.id = q.topicId
        WHERE q.chapterId = :chapterId
          AND q.isActive = 1
          AND ch.isActive = 1
          AND s.isActive = 1
          AND p.isActive = 1
          AND c.isActive = 1
          AND (
              q.topicId IS NULL
              OR (
                  t.id IS NOT NULL
                  AND t.chapterId = ch.id
                  AND t.isActive = 1
              )
          )
        ORDER BY q.sortOrder, q.id
        """,
    )
    suspend fun getEligibleQuestionsForChapter(chapterId: String): List<QuestionEntity>

    @Query(
        """
        SELECT q.*
        FROM questions q
        JOIN topics t ON t.id = q.topicId
        JOIN chapters ch ON ch.id = q.chapterId AND ch.id = t.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
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
    suspend fun getEligibleQuestionsForTopic(topicId: String): List<QuestionEntity>

    @Query(
        """
        SELECT CASE WHEN EXISTS (
            SELECT 1
            FROM academic_periods p
            JOIN courses c ON c.id = p.courseId
            WHERE p.id = :periodId AND p.courseId = :courseId
              AND p.isActive = 1 AND c.isActive = 1
        ) THEN 1 ELSE 0 END
        """,
    )
    suspend fun isActivePeriodForCourse(courseId: String, periodId: String): Boolean
}
