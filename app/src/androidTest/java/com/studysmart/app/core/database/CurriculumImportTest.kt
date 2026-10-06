package com.studysmart.app.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.common.DefaultDispatcherProvider
import com.studysmart.app.core.data.importer.AssetCurriculumBundleSource
import com.studysmart.app.core.data.importer.CurriculumImportResult
import com.studysmart.app.core.data.importer.CurriculumJsonParser
import com.studysmart.app.core.data.importer.RoomCurriculumImporter
import com.studysmart.app.core.data.importer.CurriculumImportEngine
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import java.time.Clock
import java.time.Instant
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** All writes and triggers are confined to fresh in-memory test databases. */
class CurriculumImportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun platformParserRejectsNonstandardSyntaxBeforeReadingFiles() {
        for (text in listOf("{} trailing", "{'schemaVersion':1}", "{schemaVersion:1}",
            "{\"a\":1,}", "{\"a\":1,\"a\":2}", "{\"a\":1,\"\\u0061\":2}")) {
            assertThrows(IllegalArgumentException::class.java) {
                CurriculumJsonParser().read { path ->
                    assertEquals("bundle-manifest.json", path)
                    text.toByteArray(Charsets.UTF_8)
                }
            }
        }
    }

    @Test fun cancelledTransactionRollsBackAppliedRowsAndMarker() = runBlocking {
        val db = createInMemoryStudySmartDatabase(context)
        try {
            val dao = db.curriculumImportDao()
            val before = dao.questions()
            val payload = CurriculumJsonParser().read(AssetCurriculumBundleSource(context.assets))
            var cancelled = false
            try {
                db.withTransaction {
                    CurriculumImportEngine(dao).apply(payload, Instant.EPOCH)
                    throw CancellationException("test cancellation before commit")
                }
            } catch (_: CancellationException) { cancelled = true }
            assertTrue(cancelled)
            assertEquals(before, dao.questions())
            assertNull(dao.state("bca-stage1-formatted"))
        } finally { db.close() }
    }

    @Test fun realAssetsImportAlongsideSeedsAndRepeatIsNoOp() = runBlocking {
        val db = createInMemoryStudySmartDatabase(context)
        try {
            val dao = db.curriculumImportDao()
            val initialQuestions = dao.questions()
            val sql = db.openHelper.writableDatabase
            sql.execSQL("INSERT INTO user_question_flags(userId, questionId, isImportant, createdAt) VALUES ('local-user', ?, 1, 0)", arrayOf(initialQuestions.first().id))
            val importer = RoomCurriculumImporter(db, AssetCurriculumBundleSource(context.assets), DefaultDispatcherProvider, Clock.systemUTC())
            assertEquals(CurriculumImportResult.IMPORTED, importer.importIfNeeded())
            assertEquals(initialQuestions.size + 434, dao.questions().size)
            assertTrue(dao.questions().containsAll(initialQuestions))
            assertEquals(CurriculumImportResult.ALREADY_CURRENT, importer.importIfNeeded())
            assertNotNull(dao.state("bca-stage1-formatted"))
            sql.query("SELECT COUNT(*) FROM user_question_flags").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        } finally { db.close() }
    }

    @Test fun transactionFailureRollsBackCurriculumAndMarker() = runBlocking {
        val db = createInMemoryStudySmartDatabase(context)
        try {
            val dao = db.curriculumImportDao()
            val beforeCourses = dao.courses()
            val beforePeriods = dao.periods()
            val beforeSubjects = dao.subjects()
            val sql = db.openHelper.writableDatabase
            sql.execSQL("CREATE TRIGGER reject_import BEFORE INSERT ON subjects BEGIN SELECT RAISE(ABORT, 'test import rejection'); END")
            val importer = RoomCurriculumImporter(db, AssetCurriculumBundleSource(context.assets), DefaultDispatcherProvider, Clock.systemUTC())
            var failed = false
            try { importer.importIfNeeded() } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(beforeCourses, dao.courses())
            assertEquals(beforePeriods, dao.periods())
            assertEquals(beforeSubjects, dao.subjects())
            assertNull(dao.state("bca-stage1-formatted"))
        } finally { db.close() }
    }

    @Test fun platformParserReadsExpectedDraftInventory() {
        val payload = CurriculumJsonParser().read(AssetCurriculumBundleSource(context.assets))
        assertEquals(434, payload.questions.size)
        assertEquals(427, payload.questions.count { it.isActive })
        assertEquals(1L, payload.manifest.contentVersion)
        assertEquals("bca-stage1-formatted", payload.manifest.datasetId)
        assertEquals(2, payload.manifest.schemaVersion)
        assertTrue(payload.questions.all { it.answerFormat == com.studysmart.app.core.model.AnswerFormat.HTML })
    }
}
