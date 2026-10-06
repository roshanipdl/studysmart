package com.studysmart.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Mvp0SchemaMarkerEntity::class,
        UserEntity::class,
        CourseEntity::class,
        AcademicPeriodEntity::class,
        SubjectEntity::class,
        ChapterEntity::class,
        TopicEntity::class,
        QuestionEntity::class,
        PreparationSessionEntity::class,
        PreparationSessionItemEntity::class,
        ReviewRecordEntity::class,
        ReviewScheduleEntity::class,
        QuestionKeywordEntity::class,
        UserQuestionFlagEntity::class,
        TestAttemptEntity::class,
        TestAttemptQuestionEntity::class,
        TestAnswerEntity::class,
        CurriculumImportStateEntity::class,
        SearchDocumentEntity::class,
        SearchFtsEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
@TypeConverters(StudySmartConverters::class)
abstract class StudySmartDatabase : RoomDatabase() {
    abstract fun learningDataDao(): LearningDataDao
    abstract fun userDao(): UserDao
    abstract fun curriculumDao(): CurriculumDao
    abstract fun chapterDetailDao(): ChapterDetailDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun progressDao(): ProgressDao
    abstract fun curriculumImportDao(): CurriculumImportDao
    abstract fun searchDao(): SearchDao

    abstract fun preparationDao(): PreparationDao

    abstract fun testAttemptDao(): TestAttemptDao

    abstract fun readinessDao(): ReadinessDao

    companion object {
        fun create(context: Context): StudySmartDatabase =
            Room.databaseBuilder(
                context,
                StudySmartDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .addCallback(applicationDatabaseSeed())
                .build()

        private const val DATABASE_NAME = "studysmart.db"
    }
}
