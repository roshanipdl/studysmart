package com.studysmart.app.core.database

import java.text.Normalizer
import java.util.Locale

/** Shared by indexing and future query input, independent of the SQLite tokenizer. */
object SearchTextNormalizer {
    private val combiningMarks = Regex("\\p{M}+")
    private val whitespace = Regex("[\\s\\p{Z}]+")

    fun normalize(value: String): String {
        // Broad-prefix searches rank every match. Avoid native Unicode normalization
        // and regex work per row when the entire live field is ordinary ASCII.
        if (value.all { it.code < 128 }) {
            val result = StringBuilder(value.length)
            var pendingSpace = false
            for (char in value) {
                if (char == ' ' || char in '\t'..'\r') {
                    if (result.isNotEmpty()) pendingSpace = true
                } else {
                    if (pendingSpace) result.append(' ')
                    pendingSpace = false
                    result.append(if (char in 'A'..'Z') char + 32 else char)
                }
            }
            return result.toString().trim()
        }
        return Normalizer.normalize(
            value.uppercase(Locale.ROOT).lowercase(Locale.ROOT), Normalizer.Form.NFD,
        ).replace(combiningMarks, "").replace(whitespace, " ").trim()
    }
}

object SearchDocumentProjector {
    fun project(
        type: String,
        entityId: String,
        courseId: String,
        academicPeriodId: String,
        subjectId: String,
        chapterId: String?,
        topicId: String?,
        text: String,
    ): SearchDocumentEntity = SearchDocumentEntity(
        type = type, entityId = entityId, courseId = courseId,
        academicPeriodId = academicPeriodId, subjectId = subjectId,
        chapterId = chapterId, topicId = topicId, text = SearchTextNormalizer.normalize(text),
    )

    // All ancestry is resolved afresh, including descendants omitted by an additive import.
    internal val sourceSql = """
        SELECT 'subject' AS type, s.id AS entityId, c.id AS courseId,
            p.id AS academicPeriodId, s.id AS subjectId, NULL AS chapterId,
            NULL AS topicId, s.code || ' ' || s.name AS text
        FROM subjects s JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        WHERE s.isActive = 1 AND p.isActive = 1 AND c.isActive = 1
        UNION ALL
        SELECT 'chapter', ch.id, c.id, p.id, s.id, ch.id, NULL, ch.title
        FROM chapters ch JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId JOIN courses c ON c.id = p.courseId
        WHERE ch.isActive = 1 AND s.isActive = 1 AND p.isActive = 1 AND c.isActive = 1
        UNION ALL
        SELECT 'topic', t.id, c.id, p.id, s.id, ch.id, t.id, t.title
        FROM topics t JOIN chapters ch ON ch.id = t.chapterId JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId JOIN courses c ON c.id = p.courseId
        WHERE t.isActive = 1 AND ch.isActive = 1 AND s.isActive = 1 AND p.isActive = 1 AND c.isActive = 1
        UNION ALL
        SELECT 'question', q.id, c.id, p.id, s.id, ch.id, q.topicId, q.question
        FROM questions q JOIN chapters ch ON ch.id = q.chapterId JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId JOIN courses c ON c.id = p.courseId
        LEFT JOIN topics t ON t.id = q.topicId
        WHERE q.isActive = 1 AND ch.isActive = 1 AND s.isActive = 1 AND p.isActive = 1 AND c.isActive = 1
            AND (q.topicId IS NULL OR (t.isActive = 1 AND t.chapterId = ch.id))
    """.trimIndent()
}
