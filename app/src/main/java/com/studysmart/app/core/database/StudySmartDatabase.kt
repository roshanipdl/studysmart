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
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(StudySmartConverters::class)
abstract class StudySmartDatabase : RoomDatabase() {
    abstract fun curriculumDao(): CurriculumDao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(DatabaseSeed.callback())
                .build()

        private const val DATABASE_NAME = "studysmart.db"
    }
}
