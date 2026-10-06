package com.studysmart.app.core.data.importer

import com.studysmart.app.core.common.AnswerContentParser
import com.studysmart.app.core.model.AnswerFormat
import com.studysmart.app.core.model.AnswerNode
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class FormattedStageOneBundleTest {
    private fun read(property: String) = CurriculumJsonParser().read {
        File(requireNotNull(System.getProperty(property)), checkedBundlePath(it)).readBytes()
    }

    @Test fun allAnswersParseAndOnlyApprovedFieldsChange() {
        val reviewed = read("curriculum.reviewed")
        val formatted = read("curriculum.formatted")
        assertEquals("bca-stage1-formatted", formatted.manifest.datasetId)
        assertEquals(2, formatted.manifest.schemaVersion)
        assertEquals(1L, formatted.manifest.contentVersion)
        assertEquals("876b3154fca24caa8324c3613d2905da1336924f5c9ec1c5b7576f2d7fede6e5", formatted.manifest.bundleDigest)
        assertEquals(434, formatted.questions.size)
        assertEquals(427, formatted.questions.count { it.isActive })
        assertEquals(reviewed.courses, formatted.courses)
        assertEquals(reviewed.periods, formatted.periods)
        assertEquals(reviewed.subjects, formatted.subjects)
        assertEquals(reviewed.chapters, formatted.chapters)
        assertEquals(reviewed.topics, formatted.topics)
        assertEquals(reviewed.keywords, formatted.keywords)
        var codeBlocks = 0
        fun count(nodes: List<AnswerNode>) {
            nodes.filterIsInstance<AnswerNode.Element>().forEach {
                if (it.tag == "pre") codeBlocks++
                count(it.children)
            }
        }
        reviewed.questions.zip(formatted.questions).forEach { (old, next) ->
            assertEquals(old.copy(answer = next.answer, answerFormat = next.answerFormat, updatedAt = next.updatedAt), next)
            assertEquals(AnswerFormat.HTML, next.answerFormat)
            val parsed = AnswerContentParser.parse(next.answer, next.answerFormat)
            assertTrue(AnswerContentParser.plainText(parsed).isNotBlank())
            count(parsed.nodes)
        }
        assertEquals(120, codeBlocks)
    }
}
