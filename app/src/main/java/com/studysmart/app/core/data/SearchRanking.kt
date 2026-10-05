package com.studysmart.app.core.data

import com.studysmart.app.core.database.SearchCandidateRow
import com.studysmart.app.core.database.SearchTextNormalizer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.PriorityQueue

internal object SearchRanking {
    fun tier(query: SearchQuery, fields: List<String>): Int {
        val normalized = fields.map(SearchTextNormalizer::normalize)
        return when {
            normalized.any { it == query.normalized } -> 0
            normalized.any { it.startsWith(query.normalized) } -> 1
            else -> 2
        }
    }

    /** FTS4 pcx: sum local hits / corpus hits for each phrase-column pair.
     * No popularity/preferences or floating point accumulation; integer millionths,
     * accumulated in phrase/column order. Bigger is better. Native-endian unsigned words.
     */
    fun relevance(blob: ByteArray): Long {
        require(blob.size >= 8 && blob.size % 4 == 0)
        val words = ByteBuffer.wrap(blob).order(ByteOrder.nativeOrder())
        val phrases = words.int.toLong() and 0xffffffffL
        val columns = words.int.toLong() and 0xffffffffL
        require(phrases * columns == (blob.size - 8L) / 12L)
        var score = 0L
        repeat(((blob.size - 8) / 12)) {
            val local = words.int.toLong() and 0xffffffffL
            val corpus = words.int.toLong() and 0xffffffffL
            words.int // documents containing this phrase; intentionally not used
            if (corpus != 0L) score += local * 1_000_000L / corpus
        }
        return score
    }

    private fun scored(row: SearchCandidateRow, query: SearchQuery): Triple<SearchCandidateRow, Int, Long> {
        val normalized = row.normalizedTitle
        val tier = if (row.type == "subject" || normalized == null) {
            tier(query, if (row.type == "subject") listOf(row.subjectCode, row.subjectName) else listOf(row.title))
        } else when {
            normalized == query.normalized -> 0
            normalized.startsWith(query.normalized) -> 1
            else -> 2
        }
        return Triple(row, tier, relevance(row.matchInfo))
    }

    private val ordering = compareBy<Triple<SearchCandidateRow, Int, Long>> { it.second }
            .thenByDescending { it.third }
            .thenBy { it.first.courseOrder }.thenBy { it.first.periodOrder }
            .thenBy { it.first.subjectOrder }.thenBy { it.first.chapterOrder }
            .thenBy { it.first.topicOrder }.thenBy { it.first.questionOrder }
            .thenBy { it.first.courseId }.thenBy { it.first.academicPeriodId }
            .thenBy { it.first.subjectId }.thenBy { it.first.chapterId }
            .thenBy { it.first.topicId }.thenBy { it.first.entityId }

    fun rank(rows: List<SearchCandidateRow>, query: SearchQuery): List<SearchCandidateRow> =
        rows.map { scored(it, query) }.sortedWith(ordering).map { it.first }

    /** All valid matches compete before the cap; memory/sorting is bounded per group. */
    fun topPerType(rows: List<SearchCandidateRow>, query: SearchQuery, limit: Int = 20): List<SearchCandidateRow> {
        require(limit > 0)
        val groups = mutableMapOf<String, PriorityQueue<Triple<SearchCandidateRow, Int, Long>>>()
        for (row in rows) {
            val candidate = scored(row, query)
            val heap = groups.getOrPut(row.type) { PriorityQueue(limit, ordering.reversed()) }
            if (heap.size < limit) heap.add(candidate)
            else if (ordering.compare(candidate, heap.peek()) < 0) {
                heap.poll()
                heap.add(candidate)
            }
        }
        return groups.values.flatMap { it.toList() }.sortedWith(ordering).map { it.first }
    }
}
