package com.studysmart.app.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only a private in-memory fixture, never the application's installed database. */
@RunWith(AndroidJUnit4::class)
class LearningDataTransactionTest {
    private val learningTables = listOf("review_records", "review_schedules", "user_question_flags",
        "preparation_sessions", "preparation_session_items", "test_attempts", "test_attempt_questions", "test_answers")
    private val keptTables = listOf("users", "courses", "academic_periods", "subjects", "chapters", "topics",
        "questions", "question_keywords", "search_documents", "search_fts")

    @Test fun clearIsUserScopedAndPreservesContent() = runBlocking {
        val database = fixture()
        try {
            val sql = database.openHelper.writableDatabase
            val before = keptTables.associateWith { count(database, it) }
            database.learningDataDao().clearForUser("local-user")
            learningTables.forEach { assertEquals("Other user retained in $it", 1, count(database, it)) }
            assertEquals(before, keptTables.associateWith { count(database, it) })
            sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            database.learningDataDao().clearForUser("local-user")
            learningTables.forEach { assertEquals(1, count(database, it)) }
        } finally { database.close() }
    }

    @Test fun failureRollsBackEveryDeleteAndRetrySucceeds() = runBlocking {
        val database = fixture()
        try {
            val sql = database.openHelper.writableDatabase
            sql.execSQL("CREATE TRIGGER fail_clear BEFORE DELETE ON test_attempts BEGIN SELECT RAISE(ABORT, 'fixture failure'); END")
            var failed = false
            try { database.learningDataDao().clearForUser("local-user") } catch (_: Exception) { failed = true }
            assertTrue(failed)
            learningTables.forEach { assertEquals("Rollback $it", 2, count(database, it)) }
            sql.execSQL("DROP TRIGGER fail_clear")
            database.learningDataDao().clearForUser("local-user")
            learningTables.forEach { assertEquals(1, count(database, it)) }
        } finally { database.close() }
    }

    private fun count(database: StudySmartDatabase, table: String): Int =
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun fixture(): StudySmartDatabase {
        val database = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext<Context>())
        val sql = database.openHelper.writableDatabase
        sql.execSQL("INSERT INTO users VALUES ('other-user', 'Other', 0, 0)")
        listOf("local-user", "other-user").forEach { user ->
            sql.execSQL("INSERT INTO preparation_sessions(id,userId,courseId,academicPeriodId,scopeType,originType,creationRequestId,status,startedAt,updatedAt,uniqueQuestionCount,reviewCount) VALUES (?,?,'course-bca','period-bca-semester-1','chapter','chapter',?,'completed',0,0,1,1)", arrayOf<Any>("session-$user", user, "prepare-$user"))
            sql.execSQL("INSERT INTO preparation_session_items(id,sessionId,questionId,questionSnapshot,answerSnapshot,questionTypeSnapshot,originalOrder,queueSequence,presentationCount,hardRequeueUsed,isRevealed,isCompleted) VALUES (?,?,'question-bca-s1-dbms-direct','Q','A','written',0,0,1,0,1,1)", arrayOf<Any>("item-$user", "session-$user"))
            sql.execSQL("INSERT INTO review_records(id,userId,preparationSessionId,questionId,rating,reviewedAt) VALUES (?,?,?,'question-bca-s1-dbms-direct','good',0)", arrayOf<Any>("record-$user", user, "session-$user"))
            sql.execSQL("INSERT INTO review_schedules(userId,questionId,learningState,intervalSeconds,repetitionCount,lapseCount) VALUES (?,'question-bca-s1-dbms-direct','good',1,1,0)", arrayOf<Any>(user))
            sql.execSQL("INSERT INTO user_question_flags VALUES (?,'question-bca-s1-dbms-direct',1,0)", arrayOf<Any>(user))
            sql.execSQL("INSERT INTO test_attempts(id,userId,courseId,academicPeriodId,scopeType,originType,creationRequestId,requestedQuestionCount,actualQuestionCount,questionSource,isTimed,status) VALUES (?,?,'course-bca','period-bca-semester-1','chapter','chapter',?,1,1,'all',0,'submitted')", arrayOf<Any>("attempt-$user", user, "test-$user"))
            sql.execSQL("INSERT INTO test_attempt_questions(id,testAttemptId,questionId,displayOrder,questionTextSnapshot,referenceAnswerSnapshot,questionTypeSnapshot,maxScoreSnapshot,keywordSnapshotJson,keywordSnapshotVersion) VALUES (?,?,'question-bca-s1-dbms-direct',0,'Q','A','written',2,'[]',1)", arrayOf<Any>("snapshot-$user", "attempt-$user"))
            sql.execSQL("INSERT INTO test_answers(id,testAttemptQuestionId,answerText,isFlagged,updatedAt) VALUES (?,?,'answer',0,0)", arrayOf<Any>("answer-$user", "snapshot-$user"))
        }
        // Committed history may outlive a discarded preparation session.
        sql.execSQL("UPDATE review_records SET preparationSessionId = NULL WHERE userId = 'local-user'")
        return database
    }
}
