package com.studysmart.app.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class SearchRankingCandidateRowTest {
    @Test fun projectionPreservesRankingKeysWithoutDisplayHydration() {
        val matchInfo = byteArrayOf(1, 2)
        val row = SearchRankingCandidateRow("question", "q:😀", "c", "p", "s", "ch", null,
            "", "", "cafe question", -1, 2, 3, 4, 5, Int.MAX_VALUE, matchInfo).toCandidate()
        assertEquals("q:😀", row.entityId)
        assertEquals("cafe question", row.normalizedTitle)
        assertEquals(-1, row.courseOrder)
        assertEquals(Int.MAX_VALUE, row.questionOrder)
        assertNull(row.topicId)
        assertEquals("", row.title)
        assertSame(matchInfo, row.matchInfo)
    }

    @Test fun subjectFieldsRemainAvailableForIndependentCodeAndNameRanking() {
        val row = SearchRankingCandidateRow("subject", "s", "c", "p", "s", null, null,
            "NÉÉDLE", "Needle café 😀", "needle needle cafe 😀", 0, 0, 0, 0, 0, 0, byteArrayOf())
            .toCandidate()
        assertEquals("NÉÉDLE", row.subjectCode)
        assertEquals("Needle café 😀", row.subjectName)
        assertNull(row.chapterId)
    }
}
