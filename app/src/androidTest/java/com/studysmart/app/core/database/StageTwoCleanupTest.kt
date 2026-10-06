package com.studysmart.app.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.core.model.*
import com.studysmart.app.feature.preparation.*
import com.studysmart.app.feature.test.*

/** Cleanup verification only touches fresh in-memory fixtures. */
class StageTwoCleanupTest {
    @Test fun populatedHistoryIsPreservedExactlyAndYearlyHistoryRemoved() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = createInMemoryStudySmartDatabase(context)
        try {
            val graph = coreAlphaTestContainer(context, db)
            for (chapter in listOf("chapter-bca-s1-dbms-01", "chapter-year1-foundations-01")) {
                val prep = graph.preparationRepository
                val id = (prep.createSession(PreparationSessionRequest(PreparationScopeType.CHAPTER,
                    chapter, PreparationOriginType.CHAPTER, chapter, "history-$chapter", 1)) as PreparationCreationResult.Created).sessionId
                val item = prep.observeSession(id).first()!!.currentItem!!
                prep.revealCurrent(id, item.questionId)
                prep.rateCurrent(PreparationRatingRequest(id, item.questionId, item.presentationCount, Rating.GOOD, "rate-$chapter"))
                graph.bookmarkRepository.setBookmarked(item.questionId, true)
                val test = graph.testAttemptRepository
                val attempt = (test.createChapterAttempt(ChapterTestAttemptRequest(chapter, creationRequestId = "test-$chapter")) as TestAttemptCreationResult.Created).attemptId
                test.startAttempt(attempt)
                test.submitAttempt(attempt, TestSubmitAction.MANUAL, null)
            }
            val sql = db.openHelper.writableDatabase
            val filters = mapOf(
                "preparation_sessions" to "courseId='course-bca'",
                "preparation_session_items" to "sessionId IN (SELECT id FROM preparation_sessions WHERE courseId='course-bca')",
                "review_records" to "questionId LIKE 'question-bca-%'",
                "review_schedules" to "questionId LIKE 'question-bca-%'",
                "user_question_flags" to "questionId LIKE 'question-bca-%'",
                "test_attempts" to "courseId='course-bca'",
                "test_attempt_questions" to "testAttemptId IN (SELECT id FROM test_attempts WHERE courseId='course-bca')",
                "test_answers" to "testAttemptQuestionId IN (SELECT id FROM test_attempt_questions WHERE testAttemptId IN (SELECT id FROM test_attempts WHERE courseId='course-bca'))",
            )
            fun snapshot(): Map<String, List<List<String?>>> = filters.mapValues { (table, where) ->
                sql.query("SELECT * FROM $table WHERE $where ORDER BY rowid").use { c ->
                    buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
                }
            }
            val before = snapshot()
            before.forEach { (table, rows) -> assertTrue("Populated $table", rows.isNotEmpty()) }
            sql.beginTransaction()
            try { cleanupImportedVariant(sql, "bca-local-curriculum", 2); sql.setTransactionSuccessful() }
            finally { sql.endTransaction() }
            assertEquals(before, snapshot())
            assertEquals(0, count(sql, "preparation_sessions", "courseId='course-yearly-example'"))
            assertEquals(0, count(sql, "test_attempts", "courseId='course-yearly-example'"))
            assertEquals(0, count(sql, "questions", "id='question-year1-foundations-01'"))
        } finally { db.close() }
    }

    @Test fun malformedCrossCourseSessionsAreRejectedInBothDirections() = runBlocking {
        for (chapter in listOf("chapter-bca-s1-dbms-01", "chapter-year1-foundations-01")) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = createInMemoryStudySmartDatabase(context)
            try {
                val graph = coreAlphaTestContainer(context, db)
                val id = (graph.preparationRepository.createSession(PreparationSessionRequest(
                    PreparationScopeType.CHAPTER, chapter, PreparationOriginType.CHAPTER, chapter, "malformed", 1)) as PreparationCreationResult.Created).sessionId
                val opposite = if (chapter.startsWith("chapter-bca")) "question-year1-foundations-01" else "question-bca-s1-dbms-direct"
                val sql = db.openHelper.writableDatabase
                sql.execSQL("UPDATE preparation_session_items SET questionId=? WHERE sessionId=?", arrayOf(opposite, id))
                sql.beginTransaction()
                try { assertThrows(IllegalStateException::class.java) { cleanupImportedVariant(sql, "bca-local-curriculum", 2) } }
                finally { sql.endTransaction() }
                assertEquals(1, count(sql, "courses", "id='course-yearly-example'"))
                assertEquals(1, count(sql, "preparation_sessions", "id='$id'"))
            } finally { db.close() }
        }
    }

    @Test fun cleanupPreservesBcaAndIsIdempotent() {
        val db = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext<Context>())
        try {
            val sql = db.openHelper.writableDatabase
            val before = count(sql, "questions", "id LIKE 'question-bca-%'")
            sql.execSQL("INSERT INTO user_question_flags(userId,questionId,isImportant,createdAt) VALUES ('local-user','question-bca-s1-dbms-direct',1,0)")
            sql.execSQL("INSERT INTO user_question_flags(userId,questionId,isImportant,createdAt) SELECT 'local-user',q.id,1,0 FROM questions q JOIN chapters c ON c.id=q.chapterId JOIN subjects s ON s.id=c.subjectId JOIN academic_periods p ON p.id=s.academicPeriodId WHERE p.courseId='course-yearly-example'")
            repeat(2) {
                sql.beginTransaction()
                try { cleanupImportedVariant(sql, "bca-local-curriculum", 2); sql.setTransactionSuccessful() }
                finally { sql.endTransaction() }
                assertEquals(0, count(sql, "courses", "id='course-yearly-example'"))
                assertEquals(before, count(sql, "questions", "id LIKE 'question-bca-%'"))
                assertEquals(1, count(sql, "user_question_flags", "questionId='question-bca-s1-dbms-direct'"))
                sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
        } finally { db.close() }
    }

    @Test fun rejectionRollsBackCleanupAndVersionOneDoesNotClean() {
        val db = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext<Context>())
        try {
            val sql = db.openHelper.writableDatabase
            cleanupImportedVariant(sql, "bca-local-curriculum", 1)
            cleanupImportedVariant(sql, "other-dataset", 2)
            assertEquals(1, count(sql, "courses", "id='course-yearly-example'"))
            val before = count(sql, "questions", "1=1")
            sql.execSQL("CREATE TRIGGER reject_cleanup BEFORE DELETE ON courses BEGIN SELECT RAISE(ABORT,'cleanup rollback'); END")
            sql.beginTransaction()
            try { assertThrows(Exception::class.java) { cleanupImportedVariant(sql, "bca-local-curriculum", 2) } }
            finally { sql.endTransaction() }
            assertEquals(before, count(sql, "questions", "1=1"))
            assertEquals(1, count(sql, "courses", "id='course-yearly-example'"))
        } finally { db.close() }
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, where: String): Int =
        db.query("SELECT COUNT(*) FROM $table WHERE $where").use { it.moveToFirst(); it.getInt(0) }
}
