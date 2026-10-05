package com.studysmart.app.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackedSearchCandidateRowTest {
    private fun pack(fields: List<String?>, orders: List<Int>): PackedSearchCandidateRow {
        val payload = fields.joinToString("") { value ->
            if (value == null) "-1:" else "${value.toByteArray(Charsets.UTF_8).size}:$value"
        } + ":" + orders.joinToString(":")
        return PackedSearchCandidateRow(payload, byteArrayOf())
    }

    @Test fun preservesUnicodeDelimitersNullsAndSignedOrders() {
        val row = pack(listOf("subject", "id:😀", "course:\u00001", "period", "id:😀",
            null, null, "ÉCO:ß", "Café 😀", "eco:ss cafe 😀"),
            listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE, -9)).unpack()
        assertEquals("id:😀", row.entityId)
        assertEquals("ÉCO:ß", row.subjectCode)
        assertEquals("Café 😀", row.subjectName)
        assertEquals("eco:ss cafe 😀", row.normalizedTitle)
        assertNull(row.chapterId)
        assertNull(row.topicId)
        assertEquals(Int.MIN_VALUE, row.courseOrder)
        assertEquals(Int.MAX_VALUE, row.topicOrder)
        assertEquals(-9, row.questionOrder)
    }

    @Test fun emptyIdsRemainDistinctFromNull() {
        val row = pack(listOf("question", "q", "c", "p", "s", "", "", "", "", "text"),
            List(6) { 0 }).unpack()
        assertEquals("", row.chapterId)
        assertEquals("", row.topicId)
    }
}
