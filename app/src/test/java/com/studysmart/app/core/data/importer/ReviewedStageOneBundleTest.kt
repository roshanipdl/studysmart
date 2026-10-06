package com.studysmart.app.core.data.importer

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ReviewedStageOneBundleTest {
    @Test fun reviewedBundleUsesOnlyStageOneRecordsAndRetainsAnswersAndKeywords() {
        fun read(root: File) = CurriculumJsonParser().read { File(root, checkedBundlePath(it)).readBytes() }
        val old = read(File(requireNotNull(System.getProperty("curriculum.snapshot")) { "Missing required test property: curriculum.snapshot (configure the repository curriculum fixture)" }))
        val next = read(File(requireNotNull(System.getProperty("curriculum.reviewed")) { "Missing required test property: curriculum.reviewed (configure the repository curriculum fixture)" }))
        assertEquals("bca-stage1-reviewed", next.manifest.datasetId)
        assertEquals(1L, next.manifest.contentVersion)
        assertEquals(setOf("course-bca"), next.courses.map { it.id }.toSet())
        assertEquals(434, next.questions.size)
        assertEquals(427, next.questions.count { it.isActive })
        assertEquals(old.keywords, next.keywords)
        val source = old.questions.associateBy { it.id }
        next.questions.forEach { question ->
            val original = source.getValue(question.id)
            assertEquals(original.copy(question = question.question, isActive = question.isActive), question)
        }
    }
}
