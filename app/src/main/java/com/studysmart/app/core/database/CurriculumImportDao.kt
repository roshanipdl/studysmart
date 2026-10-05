package com.studysmart.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/** Import writes deliberately use INSERT/UPDATE, never cascading REPLACE. */
@Dao
interface CurriculumImportDao {
    @Query("SELECT * FROM curriculum_import_state WHERE datasetId = :id")
    suspend fun state(id: String): CurriculumImportStateEntity?

    @Insert suspend fun insertState(row: CurriculumImportStateEntity)
    @Update suspend fun updateState(row: CurriculumImportStateEntity)

    @Query("SELECT * FROM courses") suspend fun courses(): List<CourseEntity>
    @Query("SELECT * FROM academic_periods") suspend fun periods(): List<AcademicPeriodEntity>
    @Query("SELECT * FROM subjects") suspend fun subjects(): List<SubjectEntity>
    @Query("SELECT * FROM chapters") suspend fun chapters(): List<ChapterEntity>
    @Query("SELECT * FROM topics") suspend fun topics(): List<TopicEntity>
    @Query("SELECT * FROM questions") suspend fun questions(): List<QuestionEntity>

    @Insert suspend fun insertCourse(row: CourseEntity)
    @Update suspend fun updateCourse(row: CourseEntity)
    @Insert suspend fun insertPeriod(row: AcademicPeriodEntity)
    @Update suspend fun updatePeriod(row: AcademicPeriodEntity)
    @Insert suspend fun insertSubject(row: SubjectEntity)
    @Update suspend fun updateSubject(row: SubjectEntity)
    @Insert suspend fun insertChapter(row: ChapterEntity)
    @Update suspend fun updateChapter(row: ChapterEntity)
    @Insert suspend fun insertTopic(row: TopicEntity)
    @Update suspend fun updateTopic(row: TopicEntity)
    @Insert suspend fun insertQuestion(row: QuestionEntity)
    @Update suspend fun updateQuestion(row: QuestionEntity)

    @Query("DELETE FROM question_keywords WHERE questionId = :questionId")
    suspend fun deleteKeywords(questionId: String)

    @Insert suspend fun insertKeywords(rows: List<QuestionKeywordEntity>)
}
