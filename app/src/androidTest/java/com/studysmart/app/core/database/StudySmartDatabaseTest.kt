package com.studysmart.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudySmartDatabaseTest {
    @Test
    fun inMemoryDatabaseInitializes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            StudySmartDatabase::class.java,
        ).addCallback(DatabaseSeed.callback(includeVariantSeed = false)).allowMainThreadQueries().build()

        database.openHelper.writableDatabase

        assertTrue(database.isOpen)
        database.close()
    }

    @Test
    fun seedCallbackDoesNotInsertMvp0OrDebugLearningRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            StudySmartDatabase::class.java,
        ).allowMainThreadQueries().build()

        val cursor = database.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM mvp0_schema_marker",
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertTrue(it.getInt(0) == 0)
        }
        database.close()
    }

    @Test
    fun seededCurriculumQueriesDriveTheBrowseHierarchy() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            StudySmartDatabase::class.java,
        ).addCallback(DatabaseSeed.callback(includeVariantSeed = false)).allowMainThreadQueries().build()

        val courses = database.curriculumDao().observeActiveCourses().first()
        assertEquals(listOf("course-bca", "course-yearly-example"), courses.map { it.course.id })
        assertEquals(8, courses.first().periodCount)

        val periods = database.curriculumDao().observeActivePeriods("course-bca").first()
        assertEquals(8, periods.size)
        assertEquals("Semester 1", periods.first().period.name)

        val subjects = database.curriculumDao()
            .observeActiveSubjects("period-bca-semester-1")
            .first()
        assertEquals(listOf("DBMS", "WEB"), subjects.map { it.subject.code })

        val chapters = database.curriculumDao()
            .observeActiveChapters("subject-bca-s1-dbms")
            .first()
        assertEquals(2, chapters.size)
        assertEquals(2, chapters.first().questionCount)

        database.close()
    }

    @Test
    fun eligibleQuestionQueriesRequireAnActiveHierarchy() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            StudySmartDatabase::class.java,
        ).addCallback(DatabaseSeed.callback(includeVariantSeed = false)).allowMainThreadQueries().build()

        try {
            val dao = database.curriculumDao()
            assertEquals(
                listOf(
                    "question-bca-s1-dbms-direct",
                    "question-bca-s1-dbms-topic",
                ),
                dao.getEligibleQuestionsForChapter("chapter-bca-s1-dbms-01").map { it.id },
            )
            assertEquals(
                listOf("question-bca-s1-dbms-topic"),
                dao.getEligibleQuestionsForTopic("topic-bca-s1-dbms-01").map { it.id },
            )

            val db = database.openHelper.writableDatabase
            db.execSQL("UPDATE topics SET isActive = 0 WHERE id = 'topic-bca-s1-dbms-01'")
            assertEquals(
                listOf("question-bca-s1-dbms-direct"),
                dao.getEligibleQuestionsForChapter("chapter-bca-s1-dbms-01").map { it.id },
            )
            assertTrue(dao.getEligibleQuestionsForTopic("topic-bca-s1-dbms-01").isEmpty())

            db.execSQL("UPDATE topics SET isActive = 1 WHERE id = 'topic-bca-s1-dbms-01'")
            db.execSQL("UPDATE chapters SET isActive = 0 WHERE id = 'chapter-bca-s1-dbms-01'")
            assertTrue(dao.getEligibleQuestionsForChapter("chapter-bca-s1-dbms-01").isEmpty())

            db.execSQL("UPDATE chapters SET isActive = 1 WHERE id = 'chapter-bca-s1-dbms-01'")
            db.execSQL("UPDATE subjects SET isActive = 0 WHERE id = 'subject-bca-s1-dbms'")
            assertTrue(dao.getEligibleQuestionsForChapter("chapter-bca-s1-dbms-01").isEmpty())

            db.execSQL("UPDATE subjects SET isActive = 1 WHERE id = 'subject-bca-s1-dbms'")
            db.execSQL("UPDATE academic_periods SET isActive = 0 WHERE id = 'period-bca-semester-1'")
            assertTrue(dao.getEligibleQuestionsForChapter("chapter-bca-s1-dbms-01").isEmpty())

            db.execSQL("UPDATE academic_periods SET isActive = 1 WHERE id = 'period-bca-semester-1'")
            db.execSQL("UPDATE courses SET isActive = 0 WHERE id = 'course-bca'")
            assertTrue(dao.getEligibleQuestionsForChapter("chapter-bca-s1-dbms-01").isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun versionOneMigratesToTheCurriculumSchema() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "curriculum-migration"
        context.deleteDatabase(databaseName)
        try {
            val versionOne = context.openOrCreateDatabase(
                databaseName,
                Context.MODE_PRIVATE,
                null,
            )
            try {
                versionOne.execSQL(
                    "CREATE TABLE mvp0_schema_marker (id INTEGER NOT NULL, PRIMARY KEY(id))",
                )
                versionOne.execSQL(
                    "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
                )
                versionOne.execSQL(
                    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                        "VALUES(42, 'c9c461b0d2dad01a52d0d1d42f508d34')",
                )
                versionOne.version = 1
            } finally {
                versionOne.close()
            }

            val migratedDatabase = Room.databaseBuilder(
                context,
                StudySmartDatabase::class.java,
                databaseName,
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .allowMainThreadQueries()
                .build()
            try {
                val migrated = migratedDatabase.openHelper.writableDatabase
                val cursor = migrated.query("SELECT COUNT(*) FROM courses")
                cursor.use {
                    assertTrue(it.moveToFirst())
                    assertEquals(2, it.getInt(0))
                }
                listOf(
                    "preparation_sessions",
                    "preparation_session_items",
                    "review_records",
                    "review_schedules",
                    "user_question_flags",
                    "test_attempts",
                    "test_attempt_questions",
                    "test_answers",
                ).forEach { table ->
                    val tableCursor = migrated.query("SELECT COUNT(*) FROM $table")
                    tableCursor.use {
                        assertTrue(it.moveToFirst())
                        assertEquals(0, it.getInt(0))
                    }
                }
                val keywordCursor = migrated.query("SELECT COUNT(*) FROM question_keywords")
                keywordCursor.use {
                    assertTrue(it.moveToFirst())
                    assertTrue(it.getInt(0) > 0)
                }
                val attemptColumns = mutableSetOf<String>()
                migrated.query("PRAGMA table_info(test_attempts)").use { columns ->
                    val nameIndex = columns.getColumnIndexOrThrow("name")
                    while (columns.moveToNext()) attemptColumns += columns.getString(nameIndex)
                }
                assertTrue("startedAtElapsedRealtimeMillis" in attemptColumns)
                assertTrue("startedBootId" in attemptColumns)
                assertTrue("deadlineAt" in attemptColumns)

                var accumulatedTimeDefault: String? = null
                migrated.query("PRAGMA table_info(test_answers)").use { columns ->
                    val nameIndex = columns.getColumnIndexOrThrow("name")
                    val defaultIndex = columns.getColumnIndexOrThrow("dflt_value")
                    while (columns.moveToNext()) {
                        if (columns.getString(nameIndex) == "accumulatedTimeMillis") {
                            accumulatedTimeDefault = columns.getString(defaultIndex)
                        }
                    }
                }
                assertEquals("0", accumulatedTimeDefault)
            } finally {
                migratedDatabase.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
