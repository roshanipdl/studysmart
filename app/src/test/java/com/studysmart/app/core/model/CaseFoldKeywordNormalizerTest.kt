package com.studysmart.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CaseFoldKeywordNormalizerTest {
    @Test
    fun defaultUnicodeFoldingCoversExpansionsContextualSigmaAndCherokee() {
        assertEquals("strasse", CaseFoldKeywordNormalizer.normalize("Straße"))
        assertEquals("strasse", CaseFoldKeywordNormalizer.normalize("STRAẞE"))
        assertEquals("οσ", CaseFoldKeywordNormalizer.normalize("ΟΣ"))
        assertEquals("οσ", CaseFoldKeywordNormalizer.normalize("ος"))
        assertEquals("Ꭰ", CaseFoldKeywordNormalizer.normalize("ꭰ"))
        assertEquals("Ꭰ", CaseFoldKeywordNormalizer.normalize("Ꭰ"))
    }

    @Test
    fun defaultUnicodeFoldingRetainsDotlessIAndHandlesDottedAndSupplementaryLetters() {
        assertEquals("i\u0307", CaseFoldKeywordNormalizer.normalize("İ"))
        assertEquals("ı", CaseFoldKeywordNormalizer.normalize("ı"))
        assertEquals("𐐨", CaseFoldKeywordNormalizer.normalize("𐐀"))
        assertEquals("ffi", CaseFoldKeywordNormalizer.normalize("ﬃ"))
        assertEquals("cafe", CaseFoldKeywordNormalizer.normalize("ＣＡＦＥ"))
    }
}
