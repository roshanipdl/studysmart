package com.studysmart.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.core.model.Rating
import com.studysmart.app.feature.preparation.PreparationCreationResult
import com.studysmart.app.feature.preparation.PreparationRatingRequest
import com.studysmart.app.feature.preparation.PreparationSessionRequest
import com.studysmart.app.feature.test.ChapterTestAttemptRequest
import com.studysmart.app.feature.test.TestAnswerDraft
import com.studysmart.app.feature.test.TestAttemptCreationResult
import com.studysmart.app.feature.test.TestAttemptStartResult
import com.studysmart.app.feature.test.TestSubmissionResult
import com.studysmart.app.feature.test.TestSubmitAction
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugSampleCurriculumSeedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun freshDatabaseHasTopicPoolsAndRetainsBaselineEmptyChapters() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, StudySmartDatabase::class.java)
            .addCallback(DatabaseSeed.callback()).allowMainThreadQueries().build()
        try {
            val dao = database.curriculumDao()
            DebugSampleCurriculum.chapters.forEach { chapter ->
                assertEquals(42, dao.getEligibleQuestionsForChapter(chapter.id).size)
                chapter.topics.forEach { topic ->
                    assertEquals(10, dao.getEligibleQuestionsForTopic(topic.id).size)
                }
            }
            assertEquals(216, count(database.openHelper.writableDatabase, "questions"))
            assertTrue(dao.getEligibleQuestionsForChapter("chapter-bca-s1-dbms-02").isEmpty())
            assertTrue(dao.getEligibleQuestionsForChapter("chapter-year1-foundations-02").isEmpty())
            assertEquals(0, count(database.openHelper.writableDatabase, "review_records"))
            assertEquals(0, count(database.openHelper.writableDatabase, "test_attempts"))
        } finally {
            database.close()
        }
    }

    @Test
    fun existingDatabaseGetsAdditionsOnceWithoutChangingCommittedLearningRows() = runBlocking {
        val name = "mvp10-test-sample-seed-${UUID.randomUUID()}.db"
        fun open(includeVariantSeed: Boolean) = Room.databaseBuilder(context, StudySmartDatabase::class.java, name)
            .addCallback(DatabaseSeed.callback(includeVariantSeed)).build()
        var database = open(false)
        try {
            val container = coreAlphaTestContainer(context, database)
            val sessionId = (container.preparationRepository.createSession(PreparationSessionRequest(
                PreparationScopeType.CHAPTER, "chapter-bca-s1-dbms-01", PreparationOriginType.CHAPTER,
                "chapter-bca-s1-dbms-01", "seed-history",
            )) as PreparationCreationResult.Created).sessionId
            val item = container.preparationRepository.observeSession(sessionId).first()!!.currentItem!!
            assertTrue(container.preparationRepository.revealCurrent(sessionId, item.questionId))
            container.preparationRepository.rateCurrent(PreparationRatingRequest(
                sessionId, item.questionId, item.presentationCount, Rating.GOOD, "seed-rating",
            ))
            val attemptId = (container.testAttemptRepository.createChapterAttempt(ChapterTestAttemptRequest(
                "chapter-bca-s1-dbms-01", creationRequestId = "seed-result",
            )) as TestAttemptCreationResult.Created).attemptId
            assertEquals(TestAttemptStartResult.Started, container.testAttemptRepository.startAttempt(attemptId))
            val question = container.testAttemptRepository.observeAttempt(attemptId).first()!!.questions.first()
            assertEquals(TestSubmissionResult.Submitted, container.testAttemptRepository.submitAttempt(
                attemptId, TestSubmitAction.MANUAL,
                TestAnswerDraft(question.id, "database organised collection DBMS storage access", false, 1_500L),
            ))
            val historyBefore = learningRows(database.openHelper.writableDatabase)
            assertTrue(historyBefore.getValue("review_records").isNotEmpty())
            assertTrue(historyBefore.getValue("test_answers").isNotEmpty())
            assertEquals(6, count(database.openHelper.writableDatabase, "questions"))
            database.close()
            database = open(true)
            assertEquals(216, count(database.openHelper.writableDatabase, "questions"))
            assertEquals(historyBefore, learningRows(database.openHelper.writableDatabase))
            val contentBefore = contentRows(database.openHelper.writableDatabase)
            database.close()
            database = open(true)
            assertEquals(contentBefore, contentRows(database.openHelper.writableDatabase))
            assertEquals(historyBefore, learningRows(database.openHelper.writableDatabase))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun failedInsertionRollsBackAllDemoRowsAndCanBeRetried() {
        val database = createInMemoryStudySmartDatabase(context)
        try {
            val sql = database.openHelper.writableDatabase
            val before = contentRows(sql)
            sql.execSQL("""
                CREATE TRIGGER sample_seed_failure BEFORE INSERT ON questions
                WHEN NEW.id = 'debug-sample-dbms-design-relational-q05'
                BEGIN SELECT RAISE(ABORT, 'injected seed failure'); END
            """.trimIndent())
            try {
                assertTrue(runCatching { applyVariantSeed(sql) }.isFailure)
                assertEquals(before, contentRows(sql))
            } finally {
                sql.execSQL("DROP TRIGGER sample_seed_failure")
            }
            applyVariantSeed(sql)
            assertEquals(216, count(sql, "questions"))
        } finally {
            database.close()
        }
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun learningRows(db: SupportSQLiteDatabase) = rows(db, listOf(
        "preparation_sessions", "preparation_session_items", "review_records", "review_schedules",
        "test_attempts", "test_attempt_questions", "test_answers", "user_question_flags",
    ))

    private fun contentRows(db: SupportSQLiteDatabase) = rows(db, listOf(
        "users", "courses", "academic_periods", "subjects", "chapters", "topics", "questions", "question_keywords",
    ))

    private fun rows(db: SupportSQLiteDatabase, tables: List<String>): Map<String, List<List<String?>>> =
        tables.associateWith { table ->
            db.query("SELECT * FROM $table ORDER BY rowid").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add((0 until cursor.columnCount).map { index ->
                        if (cursor.isNull(index)) null else cursor.getString(index)
                    })
                }
            }
        }
}
