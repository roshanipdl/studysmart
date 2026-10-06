package com.studysmart.app.core.data.importer

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CurriculumImportValidatorTest {
    @Test fun overflowingSchemaVersionIsRejected() {
        val root = File(requireNotNull(System.getProperty("curriculum.snapshot")) { "Missing required test property: curriculum.snapshot (configure the repository curriculum fixture)" })
        assertThrows(IllegalArgumentException::class.java) {
            CurriculumJsonParser().read { path ->
                val bytes = File(root, checkedBundlePath(path)).readBytes()
                if (path == "bundle-manifest.json") bytes.toString(Charsets.UTF_8)
                    .replace("\"schemaVersion\": 1", "\"schemaVersion\": 4294967297").toByteArray() else bytes
            }
        }
    }
    private fun payload(): CurriculumImportPayload {
        val root = File(requireNotNull(System.getProperty("curriculum.snapshot")) { "Missing required test property: curriculum.snapshot (configure the repository curriculum fixture)" })
        return CurriculumJsonParser().read { path -> File(root, checkedBundlePath(path)).readBytes() }
    }

    @Test fun stageOneParsesAndNormalizesWithAndroidStorageRules() {
        val p = payload()
        assertEquals(10, p.subjects.size)
        assertEquals(434, p.questions.size)
        assertEquals(433, p.questions.count { it.isActive })
        assertEquals(972, p.keywords.size)
        assertTrue(p.topics.isEmpty())
        assertTrue(p.questions.all { it.topicId == null })
    }

    @Test fun duplicateEntityIsRejected() {
        val p = payload()
        assertThrows(IllegalArgumentException::class.java) { CurriculumImportValidator.validate(p.copy(questions = p.questions + p.questions.first())) }
    }

    @Test fun missingParentIsRejected() {
        val p = payload()
        assertThrows(IllegalArgumentException::class.java) { CurriculumImportValidator.validate(p.copy(chapters = emptyList())) }
    }

    @Test fun inactiveAncestorIsRejected() {
        val p = payload()
        assertThrows(IllegalArgumentException::class.java) { CurriculumImportValidator.validate(p.copy(courses = p.courses.map { it.copy(isActive = false) })) }
    }

    @Test fun unsafePathIsRejected() {
        for (path in listOf("../curriculum.json", "/curriculum.json", "subjects/../a.json", "README.md", "subjects\\a.json")) {
            assertThrows(IllegalArgumentException::class.java) { checkedBundlePath(path) }
        }
    }

    @Test fun corruptFileHashIsRejected() {
        val root = File(requireNotNull(System.getProperty("curriculum.snapshot")) { "Missing required test property: curriculum.snapshot (configure the repository curriculum fixture)" })
        assertThrows(IllegalArgumentException::class.java) {
            CurriculumJsonParser().read { path -> File(root, checkedBundlePath(path)).readBytes() + if (path == "curriculum.json") byteArrayOf(32) else byteArrayOf() }
        }
    }

    @Test fun invalidScoreAndKeywordAreRejected() {
        val p = payload()
        assertThrows(IllegalArgumentException::class.java) { CurriculumImportValidator.validate(p.copy(questions = p.questions.mapIndexed { i, q -> if (i == 0) q.copy(maxScore = Double.NaN) else q })) }
        assertThrows(IllegalArgumentException::class.java) { CurriculumImportValidator.validate(p.copy(keywords = p.keywords.mapIndexed { i, k -> if (i == 0) k.copy(normalizedKeyword = " NOT Normalized ") else k })) }
    }
}
