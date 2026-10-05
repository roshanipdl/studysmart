package com.studysmart.app.core.database

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Index identities only; display strings and context labels must come from curriculum. */
@Dao
interface SearchDao {
    @Query("SELECT * FROM search_documents ORDER BY rowid")
    suspend fun documents(): List<SearchDocumentEntity>

    // Rank every valid MATCH, but do not materialize repeated display labels for
    // thousands of candidates. Display data is fetched once for the winners.
    @Query(SEARCH_RANKING_SQL)
    suspend fun packedRankingCandidates(expression: String): List<PackedSearchCandidateRow>

    suspend fun rankingCandidates(expression: String): List<SearchCandidateRow> =
        packedRankingCandidates(expression).map(PackedSearchCandidateRow::unpack)

    @Query("""
        SELECT d.type, d.entityId, c.id AS courseId, p.id AS academicPeriodId,
            s.id AS subjectId, ch.id AS chapterId, t.id AS topicId,
            CASE d.type WHEN 'subject' THEN s.name WHEN 'chapter' THEN ch.title
                WHEN 'topic' THEN t.title ELSE q.question END AS title,
            s.code AS subjectCode, s.name AS subjectName, c.name AS courseName,
            p.name AS academicPeriodName, ch.title AS chapterTitle, t.title AS topicTitle,
            c.sortOrder AS courseOrder, p.sortOrder AS periodOrder, s.sortOrder AS subjectOrder,
            COALESCE(ch.sortOrder, 0) AS chapterOrder, COALESCE(t.sortOrder, 0) AS topicOrder,
            COALESCE(q.sortOrder, 0) AS questionOrder, matchinfo(search_fts, 'pcx') AS matchInfo,
            NULL AS normalizedTitle
        FROM search_fts JOIN search_documents d ON d.rowid = search_fts.docid
        LEFT JOIN questions q ON d.type = 'question' AND q.id = d.entityId
        LEFT JOIN topics t ON t.id = CASE WHEN d.type = 'topic' THEN d.entityId ELSE q.topicId END
        LEFT JOIN chapters ch ON ch.id = CASE WHEN d.type = 'chapter' THEN d.entityId
            WHEN d.type = 'topic' THEN t.chapterId ELSE q.chapterId END
        JOIN subjects s ON s.id = CASE WHEN d.type = 'subject' THEN d.entityId ELSE ch.subjectId END
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        WHERE search_fts MATCH :expression AND c.isActive = 1 AND p.isActive = 1 AND s.isActive = 1
            AND (d.type = 'subject' OR ch.isActive = 1)
            AND (d.type != 'topic' OR t.isActive = 1)
            AND (d.type != 'question' OR (q.isActive = 1 AND
                (q.topicId IS NULL OR (t.isActive = 1 AND t.chapterId = ch.id))))
        """)
    fun observeCandidates(expression: String): Flow<List<SearchCandidateRow>>

    @Query("""
        SELECT d.type, d.entityId, c.id AS courseId, p.id AS academicPeriodId,
            s.id AS subjectId, ch.id AS chapterId, t.id AS topicId,
            CASE d.type WHEN 'subject' THEN s.name WHEN 'chapter' THEN ch.title
                WHEN 'topic' THEN t.title ELSE q.question END AS title,
            s.code AS subjectCode, s.name AS subjectName, c.name AS courseName,
            p.name AS academicPeriodName, ch.title AS chapterTitle, t.title AS topicTitle,
            c.sortOrder AS courseOrder, p.sortOrder AS periodOrder, s.sortOrder AS subjectOrder,
            COALESCE(ch.sortOrder, 0) AS chapterOrder, COALESCE(t.sortOrder, 0) AS topicOrder,
            COALESCE(q.sortOrder, 0) AS questionOrder, X'' AS matchInfo, NULL AS normalizedTitle
        FROM search_documents d
        LEFT JOIN questions q ON d.type = 'question' AND q.id = d.entityId
        LEFT JOIN topics t ON t.id = CASE WHEN d.type = 'topic' THEN d.entityId ELSE q.topicId END
        LEFT JOIN chapters ch ON ch.id = CASE WHEN d.type = 'chapter' THEN d.entityId
            WHEN d.type = 'topic' THEN t.chapterId ELSE q.chapterId END
        JOIN subjects s ON s.id = CASE WHEN d.type = 'subject' THEN d.entityId ELSE ch.subjectId END
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        WHERE d.entityId IN (:ids) AND c.isActive = 1 AND p.isActive = 1 AND s.isActive = 1
            AND (d.type = 'subject' OR ch.isActive = 1)
            AND (d.type != 'topic' OR t.isActive = 1)
            AND (d.type != 'question' OR (q.isActive = 1 AND
                (q.topicId IS NULL OR (t.isActive = 1 AND t.chapterId = ch.id))))
        """)
    suspend fun loadDisplayCandidates(ids: List<String>): List<SearchCandidateRow>
}

/** Shared verbatim with test-only cursor diagnostics; no alternate query or early cap. */
internal const val SEARCH_RANKING_SQL = """
    SELECT
        length(CAST(d.type AS BLOB)) || ':' || d.type ||
        length(CAST(d.entityId AS BLOB)) || ':' || d.entityId ||
        length(CAST(c.id AS BLOB)) || ':' || c.id ||
        length(CAST(p.id AS BLOB)) || ':' || p.id ||
        length(CAST(s.id AS BLOB)) || ':' || s.id ||
        COALESCE(length(CAST(ch.id AS BLOB)), -1) || ':' || COALESCE(ch.id, '') ||
        COALESCE(length(CAST(t.id AS BLOB)), -1) || ':' || COALESCE(t.id, '') ||
        length(CAST(CASE WHEN d.type = 'subject' THEN s.code ELSE '' END AS BLOB)) || ':' ||
            CASE WHEN d.type = 'subject' THEN s.code ELSE '' END ||
        length(CAST(CASE WHEN d.type = 'subject' THEN s.name ELSE '' END AS BLOB)) || ':' ||
            CASE WHEN d.type = 'subject' THEN s.name ELSE '' END ||
        length(CAST(d.text AS BLOB)) || ':' || d.text ||
        ':' || c.sortOrder || ':' || p.sortOrder || ':' || s.sortOrder ||
        ':' || COALESCE(ch.sortOrder, 0) || ':' || COALESCE(t.sortOrder, 0) ||
        ':' || COALESCE(q.sortOrder, 0) AS payload,
        matchinfo(search_fts, 'pcx') AS matchInfo
    FROM search_fts JOIN search_documents d ON d.rowid = search_fts.docid
    LEFT JOIN questions q ON d.type = 'question' AND q.id = d.entityId
    LEFT JOIN topics t ON t.id = CASE WHEN d.type = 'topic' THEN d.entityId ELSE q.topicId END
    LEFT JOIN chapters ch ON ch.id = CASE WHEN d.type = 'chapter' THEN d.entityId
        WHEN d.type = 'topic' THEN t.chapterId ELSE q.chapterId END
    JOIN subjects s ON s.id = CASE WHEN d.type = 'subject' THEN d.entityId ELSE ch.subjectId END
    JOIN academic_periods p ON p.id = s.academicPeriodId
    JOIN courses c ON c.id = p.courseId
    WHERE search_fts MATCH :expression AND c.isActive = 1 AND p.isActive = 1 AND s.isActive = 1
        AND (d.type = 'subject' OR ch.isActive = 1)
        AND (d.type != 'topic' OR t.isActive = 1)
        AND (d.type != 'question' OR (q.isActive = 1 AND
            (q.topicId IS NULL OR (t.isActive = 1 AND t.chapterId = ch.id))))
"""

/** Two cursor reads per match, rather than 22. Lengths are UTF-8 byte counts,
 * not UTF-16 lengths; NULs, arbitrary delimiters and supplementary characters are safe.
 * Numeric orders stay signed integers and IDs retain Kotlin's original tie ordering.
 */
data class PackedSearchCandidateRow(val payload: String, val matchInfo: ByteArray) {
    fun unpack(): SearchCandidateRow {
        var offset = 0
        fun nullableField(): String? {
            val separator = payload.indexOf(':', offset)
            val length = payload.substring(offset, separator).toInt()
            val start = separator + 1
            if (length == -1) {
                offset = start
                return null
            }
            offset = start
            var bytes = 0
            while (bytes < length) {
                val point = payload.codePointAt(offset)
                bytes += when {
                    point < 0x80 -> 1
                    point < 0x800 -> 2
                    point < 0x10000 -> 3
                    else -> 4
                }
                offset += Character.charCount(point)
            }
            require(bytes == length)
            return payload.substring(start, offset)
        }
        fun field(): String = requireNotNull(nullableField())
        val type = field()
        val entityId = field()
        val courseId = field()
        val periodId = field()
        val subjectId = field()
        val chapterId = nullableField()
        val topicId = nullableField()
        val code = field()
        val name = field()
        val text = field()
        val orders = payload.substring(offset + 1).split(':').map(String::toInt)
        return SearchCandidateRow(type, entityId, courseId, periodId, subjectId,
            chapterId, topicId, "", code, name,
            "", "", null, null, orders[0], orders[1], orders[2], orders[3], orders[4],
            orders[5], matchInfo, text)
    }
}

/** Live joined projection, not a public result: FTS relevance and sort keys stay internal. */
data class SearchCandidateRow(
    val type: String, val entityId: String, val courseId: String, val academicPeriodId: String,
    val subjectId: String, val chapterId: String?, val topicId: String?, val title: String,
    val subjectCode: String, val subjectName: String, val courseName: String,
    val academicPeriodName: String, val chapterTitle: String?, val topicTitle: String?,
    val courseOrder: Int, val periodOrder: Int, val subjectOrder: Int, val chapterOrder: Int,
    val topicOrder: Int, val questionOrder: Int, val matchInfo: ByteArray,
    val normalizedTitle: String? = null,
)
