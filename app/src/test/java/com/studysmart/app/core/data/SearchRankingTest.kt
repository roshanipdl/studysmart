package com.studysmart.app.core.data

import com.studysmart.app.core.database.SearchCandidateRow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class SearchRankingTest {
    private val query = SearchQuery.parse("data")!!
    private fun info(hits: Int = 1, corpus: Int = 10) = ByteBuffer.allocate(20)
        .order(ByteOrder.nativeOrder()).putInt(1).putInt(1).putInt(hits).putInt(corpus).putInt(5).array()
    private fun row(id: String, title: String, order: Int = 0, hits: Int = 1) = SearchCandidateRow(
        "chapter", id, "c", "p", "s", id, null, title, "CODE", "Subject", "Course", "Period",
        title, null, 0, 0, 0, order, 0, 0, info(hits))
    @Test fun exactThenFullFieldPrefixThenRelevance() {
        val rows = listOf(row("fts", "advanced data", hits = 9), row("prefix", "database"), row("exact", "Dáta"))
        assertEquals(listOf("exact", "prefix", "fts"), SearchRanking.rank(rows, query).map { it.entityId })
    }
    @Test fun subjectCodeAndNameRankIndependently() {
        assertEquals(0, SearchRanking.tier(query, listOf("DATA", "Other")))
        assertEquals(0, SearchRanking.tier(query, listOf("OTHER", "Data")))
        assertEquals(1, SearchRanking.tier(query, listOf("DB", "Database")))
        assertEquals(2, SearchRanking.tier(query, listOf("DB", "Advanced data")))
    }
    @Test fun relevanceThenCurriculumOrderThenStableIds() {
        val rows = listOf(row("z", "other data", 0), row("a", "other data", 0),
            row("first", "other data", -1), row("relevant", "other data", 9, 2))
        assertEquals(listOf("relevant", "first", "a", "z"), SearchRanking.rank(rows, query).map { it.entityId })
        assertEquals(200000L, SearchRanking.relevance(info(2)))
    }
    @Test fun limitMustFollowRankingOfAllCandidates() {
        val rows = (0..25).map { row("$it", "other data") } + row("last", "data")
        assertEquals("last", SearchRanking.rank(rows, query).take(20).first().entityId)
        assertEquals(20, SearchRanking.rank(rows, query).take(20).size)
    }
    @Test fun malformedMatchinfoIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { SearchRanking.relevance(byteArrayOf()) }
    }
    @Test fun boundedRankingMatchesFullRankingForEveryGroupAndInputOrder() {
        val rows = (0..150).map { index ->
            row("id-$index", if (index == 150) "Dáta" else "other data", index % 7, index % 5 + 1)
                .copy(type = if (index % 2 == 0) "chapter" else "question")
        }
        val expected = SearchRanking.rank(rows, query).groupBy { it.type }
            .mapValues { (_, values) -> values.take(20).map { it.entityId } }
        for (input in listOf(rows, rows.reversed(), rows.shuffled(kotlin.random.Random(42)))) {
            assertEquals(expected, SearchRanking.topPerType(input, query).groupBy { it.type }
                .mapValues { (_, values) -> values.map { it.entityId } })
        }
    }
    @Test fun indexedNormalizationPreservesExactAndPrefixAndSubjectFields() {
        val rows = listOf(
            row("exact", "").copy(normalizedTitle = "data"),
            row("prefix", "").copy(normalizedTitle = "database"),
            row("other", "").copy(normalizedTitle = "advanced data", matchInfo = info(9)),
            row("subject", "").copy(type = "subject", subjectCode = "DATA",
                subjectName = "Other", normalizedTitle = "data other"),
        )
        assertEquals(listOf("exact", "subject", "prefix", "other"),
            SearchRanking.topPerType(rows, query).map { it.entityId })
    }
}
