package com.studysmart.app.core.database

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.common.DefaultDispatcherProvider
import com.studysmart.app.core.data.importer.AssetCurriculumBundleSource
import com.studysmart.app.core.data.importer.CurriculumImportResult
import com.studysmart.app.core.data.importer.CurriculumBundleSource
import com.studysmart.app.core.data.importer.CurriculumJsonParser
import com.studysmart.app.core.data.importer.RoomCurriculumImporter
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

/** Only isolated in-memory databases; no installed application data is touched. */
class SearchIndexSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun successiveImporterVersionsRetainOmittedDescendantsAndSynchronizeAncestorChanges() = runBlocking {
        val db = createInMemoryStudySmartDatabase(context)
        try {
            val sql = db.openHelper.writableDatabase
            val unaffected = db.searchDao().documents()
            fun matches(token: String): Set<String> = sql.query(
                "SELECT d.entityId FROM search_fts f JOIN search_documents d ON d.rowid = f.docid WHERE search_fts MATCH ?",
                arrayOf(token),
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            suspend fun install(version: Int, source: CurriculumBundleSource) {
                val payload = CurriculumJsonParser().read(source)
                assertEquals(CurriculumImportResult.IMPORTED, RoomCurriculumImporter(
                    db, source, DefaultDispatcherProvider, Clock.systemUTC(),
                ).importIfNeeded())
                val marker = db.curriculumImportDao().state("search-retained-dataset")!!
                assertEquals(version.toLong(), marker.contentVersion)
                assertEquals(payload.manifest.bundleDigest, marker.bundleDigest)
                assertTrue(db.searchDao().documents().containsAll(unaffected)) // Includes unchanged rowids.
            }
            val subjectId = "search-retained-subject"
            val chapterId = "search-retained-chapter"
            val topicId = "search-retained-topic"
            val questionId = "search-retained-question"
            install(1, retainedBundle(1, includeDescendants = true))
            assertEquals(setOf(questionId), matches("retainedquestion"))
            assertEquals(setOf(topicId), matches("retainedtopic"))
            assertEquals(setOf(chapterId), matches("originalancestor"))

            // Import validation requires active children to be deactivated with their parent.
            install(2, retainedBundle(2, chapterActive = false, includeDescendants = true, descendantsActive = false))
            assertEquals(setOf(subjectId), matches("originalsubject"))
            assertTrue(matches("retainedquestion").isEmpty())
            assertTrue(matches("retainedtopic").isEmpty())
            assertTrue(matches("originalancestor").isEmpty())
            val retainedChapter = db.curriculumImportDao().chapters().single { it.id == chapterId }
            val retainedTopic = db.curriculumImportDao().topics().single { it.id == topicId }
            val retainedQuestion = db.curriculumImportDao().questions().single { it.id == questionId }

            // Omit all existing descendants while deactivating and renaming the subject.
            install(3, retainedBundle(3, subjectActive = false, includeChapter = false, subjectTitle = "renamedsubject"))
            assertFalse(db.searchDao().documents().any { it.subjectId == subjectId })
            assertTrue(matches("renamedsubject").isEmpty())
            assertEquals(retainedChapter, db.curriculumImportDao().chapters().single { it.id == chapterId })
            assertEquals(retainedTopic, db.curriculumImportDao().topics().single { it.id == topicId })
            assertEquals(retainedQuestion, db.curriculumImportDao().questions().single { it.id == questionId })

            // Reactivating ancestors does not silently reactivate omitted, inactive descendants.
            install(4, retainedBundle(4, subjectTitle = "renamedsubject", chapterTitle = "changedancestor"))
            assertEquals(setOf(subjectId), matches("renamedsubject"))
            assertEquals(setOf(chapterId), matches("changedancestor"))
            assertTrue(matches("originalsubject").isEmpty())
            assertTrue(matches("retainedquestion").isEmpty())
            assertTrue(matches("retainedtopic").isEmpty())
            assertEquals(retainedTopic, db.curriculumImportDao().topics().single { it.id == topicId })
            assertEquals(retainedQuestion, db.curriculumImportDao().questions().single { it.id == questionId })

            install(5, retainedBundle(5, includeDescendants = true, subjectTitle = "renamedsubject", chapterTitle = "changedancestor"))
            val reactivated = db.searchDao().documents().filter { it.subjectId == subjectId }.associateBy { it.entityId }
            assertEquals(setOf(questionId), matches("retainedquestion"))
            assertEquals(setOf(topicId), matches("retainedtopic"))
            val activeTopic = db.curriculumImportDao().topics().single { it.id == topicId }
            val activeQuestion = db.curriculumImportDao().questions().single { it.id == questionId }

            // Active descendants are retained during a later ancestor-title-only import.
            install(6, retainedBundle(6, subjectTitle = "finalsubject", chapterTitle = "finalancestor"))
            val updated = db.searchDao().documents().filter { it.subjectId == subjectId }.associateBy { it.entityId }
            assertEquals(reactivated.mapValues { it.value.rowid }, updated.mapValues { it.value.rowid })
            assertEquals(reactivated.getValue(topicId), updated.getValue(topicId))
            assertEquals(reactivated.getValue(questionId), updated.getValue(questionId))
            assertEquals(activeTopic, db.curriculumImportDao().topics().single { it.id == topicId })
            assertEquals(activeQuestion, db.curriculumImportDao().questions().single { it.id == questionId })
            assertEquals(setOf(subjectId), matches("finalsubject"))
            assertEquals(setOf(chapterId), matches("finalancestor"))
            assertEquals(setOf(questionId), matches("retainedquestion"))
            assertEquals(setOf(topicId), matches("retainedtopic"))
            assertTrue(matches("changedancestor").isEmpty())
            assertTrue(matches("renamedsubject").isEmpty())
        } finally { db.close() }
    }

    /** Small, hashed bundles exercise the real parser/importer rather than direct SQL writes. */
    private fun retainedBundle(
        version: Int,
        subjectActive: Boolean = true,
        chapterActive: Boolean = true,
        includeChapter: Boolean = true,
        includeDescendants: Boolean = false,
        descendantsActive: Boolean = true,
        subjectTitle: String = "originalsubject",
        chapterTitle: String = "originalancestor",
    ): CurriculumBundleSource {
        val timestamps = "\"createdAt\":0,\"updatedAt\":$version,\"sortOrder\":1"
        val subject = JSONObject("""{"id":"search-retained-subject","academicPeriodId":"search-retained-period","code":"RETAINED","name":"$subjectTitle","description":"","isActive":$subjectActive,$timestamps}""")
        val chapter = JSONObject("""{"id":"search-retained-chapter","subjectId":"search-retained-subject","chapterNumber":1,"title":"$chapterTitle","description":"","isActive":$chapterActive,$timestamps}""")
        val topic = JSONObject("""{"id":"search-retained-topic","chapterId":"search-retained-chapter","title":"retainedtopic","description":"","isActive":$descendantsActive,$timestamps}""")
        val question = JSONObject("""{"id":"search-retained-question","chapterId":"search-retained-chapter","topicId":"search-retained-topic","question":"retainedquestion","answer":"Reference answer","questionType":"written","difficulty":"normal","maxScore":2,"explanation":"","isActive":$descendantsActive,$timestamps}""")
        val subjectPath = "subjects/search-retained.json"
        val catalog = JSONObject("""{"schemaVersion":1,"contentRevision":$version,"courses":[{"id":"search-retained-course","code":"RETAINED","name":"Search fixture","shortName":"Search","structureType":"semester","summary":"","isActive":true,$timestamps}],"academicPeriods":[{"id":"search-retained-period","courseId":"search-retained-course","periodNumber":1,"periodType":"semester","name":"Semester 1","shortName":"Sem 1","isActive":true,$timestamps}],"subjectFiles":[{"path":"$subjectPath","subjectId":"search-retained-subject","academicPeriodId":"search-retained-period"}]}""")
        val document = JSONObject().put("schemaVersion", 1).put("contentRevision", version)
            .put("subject", subject)
            .put("chapters", JSONArray().apply { if (includeChapter) put(chapter) })
            .put("topics", JSONArray().apply { if (includeDescendants) put(topic) })
            .put("questions", JSONArray().apply { if (includeDescendants) put(question) })
            .put("questionKeywords", JSONArray()).put("provenance", JSONArray())
        val files = linkedMapOf("curriculum.json" to catalog.toString().toByteArray(), subjectPath to document.toString().toByteArray())
        val hashes = files.mapValues { CurriculumJsonParser.sha256(it.value) }
        val digest = CurriculumJsonParser.sha256(hashes.toSortedMap().entries.joinToString("") { "${it.key}\t${it.value}\n" }.toByteArray())
        val manifest = JSONObject().put("schemaVersion", 1).put("datasetId", "search-retained-dataset")
            .put("contentVersion", version).put("bundleDigest", digest)
            .put("files", JSONArray().apply { hashes.forEach { (path, hash) -> put(JSONObject().put("path", path).put("sha256", hash)) } })
        files["bundle-manifest.json"] = manifest.toString().toByteArray()
        return CurriculumBundleSource { path -> files.getValue(path) }
    }

    @Test fun rollbackRestoresExistingFtsPostingsAndDocuments() = runBlocking {
        val db = createInMemoryStudySmartDatabase(context)
        try {
            val sql = db.openHelper.writableDatabase
            val before = db.searchDao().documents()
            var failed = false
            try {
                db.withTransaction {
                    sql.execSQL("UPDATE questions SET question = 'rollbacktoken' WHERE id = 'question-bca-s1-dbms-direct'")
                    SearchIndexSynchronizer.synchronize(sql)
                    sql.query("SELECT docid FROM search_fts WHERE search_fts MATCH 'rollbacktoken'").use { assertTrue(it.moveToFirst()) }
                    error("rollback after indexing")
                }
            } catch (_: IllegalStateException) { failed = true }
            assertTrue(failed)
            assertEquals(before, db.searchDao().documents())
            sql.query("SELECT docid FROM search_fts WHERE search_fts MATCH 'rollbacktoken'").use { assertFalse(it.moveToFirst()) }
            sql.query("SELECT d.entityId FROM search_fts f JOIN search_documents d ON d.rowid = f.docid WHERE search_fts MATCH 'database' AND d.type = 'question'").use {
                assertTrue(it.moveToFirst()); assertEquals("question-bca-s1-dbms-direct", it.getString(0))
            }
        } finally { db.close() }
    }

    @Test fun changedPromptReplacesTokensWithoutChangingIdentityAndDeactivationRemovesTokens() = runBlocking {
        val db = createInMemoryStudySmartDatabase(context)
        try {
            val sql = db.openHelper.writableDatabase
            val before = db.searchDao().documents().single { it.entityId == "question-bca-s1-dbms-direct" }
            val originalQuestion = db.curriculumImportDao().questions().single { it.id == before.entityId }
            db.withTransaction {
                sql.execSQL("UPDATE questions SET question = 'Éléphant uniquetoken' WHERE id = ?", arrayOf(before.entityId))
                SearchIndexSynchronizer.synchronize(sql)
            }
            val updated = db.searchDao().documents().single { it.entityId == before.entityId }
            assertEquals(before.rowid, updated.rowid)
            assertEquals("elephant uniquetoken", updated.text)
            sql.query("SELECT docid FROM search_fts WHERE search_fts MATCH 'elephant'").use {
                assertTrue(it.moveToFirst()); assertEquals(before.rowid, it.getLong(0)); assertFalse(it.moveToNext())
            }
            sql.query("SELECT docid FROM search_fts WHERE search_fts MATCH 'database'").use {
                while (it.moveToNext()) assertNotEquals(before.rowid, it.getLong(0))
            }
            db.withTransaction {
                // Curriculum removal is soft: keywords/history retain their question FK.
                sql.execSQL("UPDATE questions SET isActive = 0 WHERE id = ?", arrayOf(before.entityId))
                SearchIndexSynchronizer.synchronize(sql)
            }
            sql.query("SELECT docid FROM search_fts WHERE search_fts MATCH 'uniquetoken'").use { assertFalse(it.moveToFirst()) }
            assertFalse(db.searchDao().documents().any { it.entityId == before.entityId })
            assertEquals(originalQuestion.copy(question = "Éléphant uniquetoken", isActive = false),
                db.curriculumImportDao().questions().single { it.id == before.entityId })
            sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close() }
    }

    @Test fun initialSeedDebugSeedAndNoOpKeepStableRowsAndSearchableTokens() = runBlocking {
        val db = createInMemoryStudySmartDatabase(context)
        try {
            val sql = db.openHelper.writableDatabase
            DatabaseSeed.callback().onOpen(sql)
            val initial = db.searchDao().documents()
            assertTrue(initial.any { it.entityId == "question-bca-s1-dbms-direct" })
            assertTrue(initial.any { it.chapterId?.startsWith("debug-sample-") == true })
            db.withTransaction { SearchIndexSynchronizer.synchronize(sql) }
            DatabaseSeed.callback().onOpen(sql)
            assertEquals(initial, db.searchDao().documents())
            sql.query("SELECT COUNT(*) FROM search_fts WHERE search_fts MATCH 'database'").use {
                assertTrue(it.moveToFirst()); assertTrue(it.getInt(0) > 0)
            }
            sql.query("SELECT COUNT(*) FROM search_fts WHERE search_fts MATCH 'organised'").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) // Answer only.
            }
        } finally { db.close() }
    }

    @Test fun retainedDescendantsFollowAncestorChangesDeactivationAndReactivation() = runBlocking {
        val db = createInMemoryStudySmartDatabase(context)
        try {
            val sql = db.openHelper.writableDatabase
            val before = db.searchDao().documents()
            val question = before.single { it.entityId == "question-bca-s1-dbms-direct" }
            db.withTransaction {
                sql.execSQL("UPDATE subjects SET academicPeriodId = 'period-bca-semester-2' WHERE id = 'subject-bca-s1-dbms'")
                SearchIndexSynchronizer.synchronize(sql)
            }
            val moved = db.searchDao().documents().single { it.entityId == question.entityId }
            assertEquals(question.rowid, moved.rowid)
            assertEquals("period-bca-semester-2", moved.academicPeriodId)
            db.withTransaction {
                sql.execSQL("UPDATE academic_periods SET isActive = 0 WHERE id = 'period-bca-semester-2'")
                SearchIndexSynchronizer.synchronize(sql)
            }
            assertFalse(db.searchDao().documents().any { it.subjectId == question.subjectId })
            db.withTransaction {
                sql.execSQL("UPDATE academic_periods SET isActive = 1 WHERE id = 'period-bca-semester-2'")
                SearchIndexSynchronizer.synchronize(sql)
            }
            assertTrue(db.searchDao().documents().any { it.entityId == question.entityId })
            db.withTransaction {
                sql.execSQL("UPDATE topics SET isActive = 0 WHERE id = 'topic-bca-s1-dbms-01'")
                SearchIndexSynchronizer.synchronize(sql)
            }
            val docs = db.searchDao().documents()
            assertFalse(docs.any { it.entityId == "question-bca-s1-dbms-topic" })
            assertTrue(docs.any { it.entityId == question.entityId })
        } finally { db.close() }
    }

    @Test fun failedIndexWriteRollsBackImportedCurriculumMarkerAndFts() = runBlocking {
        val db = createInMemoryStudySmartDatabase(context)
        try {
            val sql = db.openHelper.writableDatabase
            val before = db.searchDao().documents()
            val courses = db.curriculumImportDao().courses()
            sql.execSQL("CREATE TRIGGER reject_search BEFORE INSERT ON search_documents BEGIN SELECT RAISE(ABORT, 'index rejection'); END")
            val importer = RoomCurriculumImporter(db, AssetCurriculumBundleSource(context.assets), DefaultDispatcherProvider, Clock.systemUTC())
            var failed = false
            try { importer.importIfNeeded() } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(before, db.searchDao().documents())
            assertEquals(courses, db.curriculumImportDao().courses())
            assertNull(db.curriculumImportDao().state("bca-local-curriculum"))
            sql.execSQL("DROP TRIGGER reject_search")
            assertEquals(CurriculumImportResult.IMPORTED, importer.importIfNeeded())
            val imported = db.searchDao().documents()
            assertEquals(before.count { it.type == "question" } + 433, imported.count { it.type == "question" })
            assertTrue(imported.containsAll(before))
            sql.query("SELECT COUNT(*) FROM search_fts WHERE search_fts MATCH 'database'").use {
                assertTrue(it.moveToFirst()); assertTrue(it.getInt(0) > 0)
            }
            assertEquals(CurriculumImportResult.ALREADY_CURRENT, importer.importIfNeeded())
            assertEquals(imported, db.searchDao().documents())
        } finally { db.close() }
    }
}
