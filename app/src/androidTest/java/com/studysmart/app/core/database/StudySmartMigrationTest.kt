package com.studysmart.app.core.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.core.model.Rating
import com.studysmart.app.feature.preparation.PreparationCreationResult
import com.studysmart.app.feature.preparation.PreparationRatingRequest
import com.studysmart.app.feature.preparation.PreparationSessionRequest
import com.studysmart.app.feature.test.ChapterTestAttemptRequest
import com.studysmart.app.feature.test.TestAnswerDraft
import com.studysmart.app.feature.test.TestAttemptCreationResult
import com.studysmart.app.feature.test.TestSubmissionResult
import com.studysmart.app.feature.test.TestSubmitAction
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.testing.createPersistentStudySmartDatabase
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builds historical fixtures from version-controlled schemas without extra test dependencies. */
class StudySmartMigrationTest {
    @Test fun populatedVersionOneUpgradesToSeven() = verifyUpgrade(1)
    @Test fun populatedVersionTwoUpgradesToSeven() = verifyUpgrade(2)
    @Test fun populatedVersionThreeUpgradesToSeven() = verifyUpgrade(3)
    @Test fun populatedVersionFourUpgradesToSeven() = verifyUpgrade(4)
    @Test fun populatedVersionFiveUpgradesToSeven() = verifyUpgrade(5)
    @Test fun populatedVersionSixUpgradesToSeven() = verifyUpgrade(6)

    private fun verifyUpgrade(version: Int) = runBlocking {
        withTimeout(30_000) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val sourceName = "mvp10-test-migration-source-${UUID.randomUUID()}.db"
            val targetName = "mvp10-test-migration-v$version-${UUID.randomUUID()}.db"
            val source = createPersistentStudySmartDatabase(context, sourceName)
            try {
                val sessionId = populateLearningFixture(context, source)
                val schema = InstrumentationRegistry.getInstrumentation().context.assets.open(
                    "com.studysmart.app.core.database.StudySmartDatabase/$version.json",
                ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
                val entities = schema.getJSONArray("entities")
                val expected = linkedMapOf<String, List<List<String?>>>()
                context.openOrCreateDatabase(targetName, Context.MODE_PRIVATE, null).use { historical ->
                    historical.setForeignKeyConstraintsEnabled(true)
                    for (index in 0 until entities.length()) {
                        val entity = entities.getJSONObject(index)
                        val table = entity.getString("tableName")
                        historical.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        // Room omits this optional field for tables without indexes.
                        val indices = entity.optJSONArray("indices") ?: JSONArray()
                        for (item in 0 until indices.length()) {
                            historical.execSQL(indices.getJSONObject(item).getString("createSql")
                                .replace("\${TABLE_NAME}", table))
                        }
                        val columns = columns(entity)
                        val query = selectQuery(entity)
                        expected[table] = readRows(source.openHelper.writableDatabase, query)
                        source.openHelper.writableDatabase.query(query).use { cursor ->
                            while (cursor.moveToNext()) {
                                val values = ContentValues()
                                columns.forEachIndexed { columnIndex, column ->
                                    when (cursor.getType(columnIndex)) {
                                        Cursor.FIELD_TYPE_NULL -> values.putNull(column)
                                        Cursor.FIELD_TYPE_INTEGER -> values.put(column, cursor.getLong(columnIndex))
                                        Cursor.FIELD_TYPE_FLOAT -> values.put(column, cursor.getDouble(columnIndex))
                                        Cursor.FIELD_TYPE_BLOB -> values.put(column, cursor.getBlob(columnIndex))
                                        else -> values.put(column, cursor.getString(columnIndex))
                                    }
                                }
                                historical.insertOrThrow(table, null, values)
                            }
                        }
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (index in 0 until setup.length()) historical.execSQL(setup.getString(index))
                    historical.version = version
                }

                val migrated = createPersistentStudySmartDatabase(context, targetName)
                try {
                    // Opening through Room validates the complete v7 schema after migration.
                    val sql = migrated.openHelper.writableDatabase
                    assertEquals(7, sql.version)
                    for (index in 0 until entities.length()) {
                        val entity = entities.getJSONObject(index)
                        assertEquals("Retained rows for ${entity.getString("tableName")} from v$version",
                            expected.getValue(entity.getString("tableName")), readRows(sql, selectQuery(entity)))
                    }
                    sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                    assertEquals(2, migrated.curriculumDao().observeActiveCourses().first().size)
                    assertSearchBackfill(sql)
                    if (version >= 3) {
                        assertEquals(2, migrated.preparationDao().getSessionReviewRecords(sessionId).size)
                    }
                    if (version == 4) {
                        sql.query("SELECT startedAtElapsedRealtimeMillis, startedBootId, deadlineAt FROM test_attempts").use {
                            assertTrue(it.moveToFirst())
                            assertTrue(it.isNull(0) && it.isNull(1) && it.isNull(2))
                        }
                        sql.query("SELECT accumulatedTimeMillis FROM test_answers").use {
                            assertTrue(it.count > 0)
                            while (it.moveToNext()) assertEquals(0L, it.getLong(0))
                        }
                    }
                } finally {
                    migrated.close()
                }
                val reopened = createPersistentStudySmartDatabase(context, targetName)
                try {
                    assertSearchBackfill(reopened.openHelper.writableDatabase)
                    reopened.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                } finally {
                    reopened.close()
                }
            } finally {
                source.close()
                context.deleteDatabase(sourceName)
                context.deleteDatabase(targetName)
            }
        }
    }

    private fun assertSearchBackfill(sql: SupportSQLiteDatabase) {
        sql.query("SELECT COUNT(*) FROM search_documents").use {
            assertTrue(it.moveToFirst())
            // Migration 1→2 seeds release curriculum, so every supported path has content.
            assertTrue(it.getLong(0) > 0)
        }
        sql.query("SELECT d.entityId FROM search_fts f JOIN search_documents d ON d.rowid = f.docid WHERE search_fts MATCH 'database' AND d.type = 'question'").use {
            assertTrue(it.moveToFirst())
            assertEquals("question-bca-s1-dbms-direct", it.getString(0))
            assertFalse(it.moveToNext())
        }
    }

    private suspend fun populateLearningFixture(context: Context, database: StudySmartDatabase): String {
        val container = coreAlphaTestContainer(context, database)
        database.openHelper.writableDatabase.execSQL("INSERT INTO mvp0_schema_marker(id) VALUES (7)")
        val created = container.preparationRepository.createSession(PreparationSessionRequest(
            PreparationScopeType.CHAPTER, "chapter-bca-s1-dbms-01", PreparationOriginType.CHAPTER,
            "chapter-bca-s1-dbms-01", "migration-preparation",
        )) as PreparationCreationResult.Created
        repeat(2) { index ->
            val item = container.preparationRepository.observeSession(created.sessionId).first()!!.currentItem!!
            container.preparationRepository.revealCurrent(created.sessionId, item.questionId)
            container.preparationRepository.rateCurrent(PreparationRatingRequest(
                created.sessionId, item.questionId, item.presentationCount, Rating.GOOD, "migration-rating-$index",
            ))
        }
        val attempt = container.testAttemptRepository.createChapterAttempt(ChapterTestAttemptRequest(
            "chapter-bca-s1-dbms-01", creationRequestId = "migration-test",
        )) as TestAttemptCreationResult.Created
        container.testAttemptRepository.startAttempt(attempt.attemptId)
        val question = container.testAttemptRepository.observeAttempt(attempt.attemptId).first()!!.questions.first()
        assertEquals(TestSubmissionResult.Submitted, container.testAttemptRepository.submitAttempt(
            attempt.attemptId, TestSubmitAction.MANUAL,
            TestAnswerDraft(question.id, "database organised collection", true, 1_500L),
        ))
        database.testAttemptDao().insertQuestionFlag(UserQuestionFlagEntity(
            "local-user", question.questionId, true, java.time.Instant.EPOCH,
        ))
        return created.sessionId
    }

    private fun columns(entity: JSONObject): List<String> {
        val fields = entity.getJSONArray("fields")
        return (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
    }

    private fun selectQuery(entity: JSONObject): String {
        val keys = entity.getJSONObject("primaryKey").getJSONArray("columnNames")
        val order = (0 until keys.length()).joinToString { "`${keys.getString(it)}`" }
        return "SELECT ${columns(entity).joinToString { "`$it`" }} FROM `${entity.getString("tableName")}` ORDER BY $order"
    }

    private fun readRows(database: SupportSQLiteDatabase, query: String): List<List<String?>> =
        database.query(query).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map {
                    if (cursor.isNull(it)) null else cursor.getString(it)
                })
            }
        }
}
