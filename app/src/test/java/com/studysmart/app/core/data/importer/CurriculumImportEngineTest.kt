package com.studysmart.app.core.data.importer

import com.studysmart.app.core.database.*
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CurriculumImportEngineTest {
    @Test fun omittedActiveDescendantPreventsParentDeactivation() = runBlocking {
        val p = payload(); val dao = MemoryDao(); val engine = CurriculumImportEngine(dao)
        engine.apply(p, Instant.EPOCH)
        val writes = dao.writes
        val next = p.copy(manifest = p.manifest.copy(contentVersion = 2),
            courses = p.courses.map { it.copy(isActive = false) }, periods = emptyList(),
            subjects = emptyList(), chapters = emptyList(), topics = emptyList(), questions = emptyList(), keywords = emptyList())
        try { engine.apply(next, Instant.EPOCH); fail("Expected preserved ancestry rejection") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("ancestry")) }
        assertEquals(writes, dao.writes)
        assertEquals(1L, dao.marker!!.contentVersion)
    }
    private fun payload(): CurriculumImportPayload {
        val root = File(requireNotNull(System.getProperty("curriculum.snapshot")) { "Missing required test property: curriculum.snapshot (configure the repository curriculum fixture)" })
        return CurriculumJsonParser().read { File(root, checkedBundlePath(it)).readBytes() }
    }

    @Test fun importIsIdempotentAndPreservesUnlistedContent() = runBlocking {
        val p = payload(); val dao = MemoryDao()
        val extra = p.subjects.first().copy(id = "unlisted", code = "EXTRA")
        dao.subjectRows += extra
        val engine = CurriculumImportEngine(dao)
        assertEquals(CurriculumImportResult.IMPORTED, engine.apply(p, Instant.EPOCH))
        assertEquals(434, dao.questionRows.size)
        assertTrue(extra in dao.subjectRows)
        val writes = dao.writes
        assertEquals(CurriculumImportResult.ALREADY_CURRENT, engine.apply(p, Instant.EPOCH))
        assertEquals(writes, dao.writes)
    }

    @Test fun collisionFailsBeforeWrites() = runBlocking {
        val p = payload(); val dao = MemoryDao()
        dao.subjectRows += p.subjects.first().copy(id = "conflicting-id")
        try { CurriculumImportEngine(dao).apply(p, Instant.EPOCH); fail("Expected collision") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("collision")) }
        assertEquals(0, dao.writes)
        assertNull(dao.marker)
    }

    @Test fun versionMismatchAndDowngradeDoNotWrite() = runBlocking {
        val p = payload(); val dao = MemoryDao()
        dao.marker = CurriculumImportStateEntity(p.manifest.datasetId, 1, "different", Instant.EPOCH)
        try { CurriculumImportEngine(dao).apply(p, Instant.EPOCH); fail("Expected digest rejection") }
        catch (_: IllegalArgumentException) { }
        dao.marker = dao.marker!!.copy(contentVersion = 2)
        assertEquals(CurriculumImportResult.NEWER_ALREADY_INSTALLED, CurriculumImportEngine(dao).apply(p, Instant.EPOCH))
        assertEquals(0, dao.writes)
    }

    @Test fun updatePreservesCreatedAtAndReplacesOnlyOwnedKeywords() = runBlocking {
        val p = payload(); val dao = MemoryDao(); val engine = CurriculumImportEngine(dao)
        engine.apply(p, Instant.EPOCH)
        val old = dao.questionRows.first()
        dao.questionRows[0] = old.copy(createdAt = Instant.ofEpochMilli(1))
        dao.keywordRows += QuestionKeywordEntity("unlisted-question", "keep", 1.0, 1)
        val updated = p.copy(manifest = p.manifest.copy(contentVersion = 2, bundleDigest = "new"),
            questions = p.questions.map { if (it.id == old.id) it.copy(answer = "Updated answer") else it },
            keywords = p.keywords.filter { it.questionId != old.id })
        engine.apply(updated, Instant.ofEpochMilli(2))
        assertEquals(Instant.ofEpochMilli(1), dao.questionRows.first { it.id == old.id }.createdAt)
        assertEquals("Updated answer", dao.questionRows.first { it.id == old.id }.answer)
        assertFalse(dao.keywordRows.any { it.questionId == old.id })
        assertTrue(dao.keywordRows.any { it.questionId == "unlisted-question" })
    }

    private class MemoryDao : CurriculumImportDao {
        var marker: CurriculumImportStateEntity? = null
        var writes = 0
        val courseRows = mutableListOf<CourseEntity>()
        val periodRows = mutableListOf<AcademicPeriodEntity>()
        val subjectRows = mutableListOf<SubjectEntity>()
        val chapterRows = mutableListOf<ChapterEntity>()
        val topicRows = mutableListOf<TopicEntity>()
        val questionRows = mutableListOf<QuestionEntity>()
        val keywordRows = mutableListOf<QuestionKeywordEntity>()
        private fun <T> insert(rows: MutableList<T>, row: T) { rows += row; writes++ }
        private fun <T> update(rows: MutableList<T>, row: T, match: (T) -> Boolean) { rows[rows.indexOfFirst(match)] = row; writes++ }
        override suspend fun state(id: String) = marker?.takeIf { it.datasetId == id }
        override suspend fun insertState(row: CurriculumImportStateEntity) { marker = row; writes++ }
        override suspend fun updateState(row: CurriculumImportStateEntity) { marker = row; writes++ }
        override suspend fun courses() = courseRows.toList()
        override suspend fun periods() = periodRows.toList()
        override suspend fun subjects() = subjectRows.toList()
        override suspend fun chapters() = chapterRows.toList()
        override suspend fun topics() = topicRows.toList()
        override suspend fun questions() = questionRows.toList()
        override suspend fun insertCourse(row: CourseEntity) = insert(courseRows, row)
        override suspend fun updateCourse(row: CourseEntity) = update(courseRows, row) { it.id == row.id }
        override suspend fun insertPeriod(row: AcademicPeriodEntity) = insert(periodRows, row)
        override suspend fun updatePeriod(row: AcademicPeriodEntity) = update(periodRows, row) { it.id == row.id }
        override suspend fun insertSubject(row: SubjectEntity) = insert(subjectRows, row)
        override suspend fun updateSubject(row: SubjectEntity) = update(subjectRows, row) { it.id == row.id }
        override suspend fun insertChapter(row: ChapterEntity) = insert(chapterRows, row)
        override suspend fun updateChapter(row: ChapterEntity) = update(chapterRows, row) { it.id == row.id }
        override suspend fun insertTopic(row: TopicEntity) = insert(topicRows, row)
        override suspend fun updateTopic(row: TopicEntity) = update(topicRows, row) { it.id == row.id }
        override suspend fun insertQuestion(row: QuestionEntity) = insert(questionRows, row)
        override suspend fun updateQuestion(row: QuestionEntity) = update(questionRows, row) { it.id == row.id }
        override suspend fun deleteKeywords(questionId: String) { keywordRows.removeAll { it.questionId == questionId }; writes++ }
        override suspend fun insertKeywords(rows: List<QuestionKeywordEntity>) { keywordRows += rows; writes++ }
    }
}
