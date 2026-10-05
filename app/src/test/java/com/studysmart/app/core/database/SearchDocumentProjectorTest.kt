package com.studysmart.app.core.database

import java.util.Locale
import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchDocumentProjectorTest {
    @Test fun asciiFastPathPreservesTheUnicodeNormalizationContract() {
        val cases = (0..127).map { " \tAB${it.toChar()} CD\r\n " } + listOf(
            "Database transaction principles question 9993", "\t A  B\u000bC\u000cD\r\n", "", "   ",
        )
        cases.forEach { value ->
            val expected = Normalizer.normalize(value.uppercase(Locale.ROOT).lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "").replace(Regex("[\\s\\p{Z}]+"), " ").trim()
            assertEquals("ASCII input ${value.toCharArray().map { it.code }}", expected, SearchTextNormalizer.normalize(value))
        }
        assertEquals("𐐨𐐩 lesson", SearchTextNormalizer.normalize("𐐀𐐁 LESSON"))
    }
    @Test fun normalizationFoldsAccentsCaseAndUnicodeWhitespace() {
        assertEquals("cafe strasse σ", SearchTextNormalizer.normalize("  CAFÉ\u00a0Straße\nς  "))
        assertEquals("cafe", SearchTextNormalizer.normalize("Cafe\u0301"))
    }

    @Test fun normalizationDoesNotDependOnDeviceLocaleAndIsIdempotent() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            val normalized = SearchTextNormalizer.normalize(" INTRODUCTION İ ")
            assertEquals("introduction i", normalized)
            assertEquals(normalized, SearchTextNormalizer.normalize(normalized))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun projectionPreservesIdentityAndOptionalDirectChapterContext() {
        val doc = SearchDocumentProjector.project("question", "q", "c", "p", "s", "ch", null, "CAFÉ?")
        assertEquals(SearchDocumentEntity(0, "question", "q", "c", "p", "s", "ch", null, "cafe?"), doc)
    }
}
