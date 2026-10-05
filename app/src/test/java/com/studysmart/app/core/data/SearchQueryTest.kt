package com.studysmart.app.core.data

import org.junit.Assert.*
import org.junit.Test

class SearchQueryTest {
    @Test fun shortAndPunctuationOnlyInputsAreRejected() {
        listOf("", "a", " é ", "***", "“”", " -- ").forEach { assertNull(SearchQuery.parse(it)) }
    }
    @Test fun normalizationIsSharedAndUnicodeAware() {
        val query = SearchQuery.parse("  CAFÉ\u00a0 Straße  ")!!
        assertEquals("cafe strasse", query.normalized)
        assertEquals("\"cafe*\" \"strasse*\"", query.expression)
        assertNotNull(SearchQuery.parse("数学"))
        assertEquals("\"𐐨𐐩*\"", SearchQuery.parse("𐐀𐐁")!!.expression)
    }
    @Test fun operatorsAndQuotesAreLiteralTokensAndPunctuationIsBoundary() {
        assertEquals("\"alpha*\" \"or*\" \"not*\" \"near*\" \"beta*\"",
            SearchQuery.parse("“alpha” OR NOT NEAR:beta*")!!.expression)
        assertEquals("\"foo*\" \"bar*\"", SearchQuery.parse("foo-bar")!!.expression)
    }
    @Test fun everyTokenUsesPrefixAndIsRequired() {
        assertEquals("\"data*\" \"struct*\"", SearchQuery.parse("data struct")!!.expression)
        assertEquals("\"ab*\" \"or*\" \"cd*\"", SearchQuery.parse("ab\" OR \"cd")!!.expression)
    }
}
