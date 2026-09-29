package com.studysmart.app.core.model

import java.text.Normalizer
import java.util.Locale

private const val SECONDS_PER_MINUTE = 60L

enum class TestQuestionSource {
    ALL,
    RANDOM,
    WEAK,
    UNSEEN,
    IMPORTANT,
}

enum class TestAttemptScopeType {
    CHAPTER,
    TOPIC,
    IMPORTANT,
    RETEST_WEAK,
    STATUS_FILTER,
}

enum class TestAttemptOriginType {
    CHAPTER,
    IMPORTANT,
    NEW_TEST,
    RETEST_WEAK,
}

enum class TestAttemptStatus {
    CREATED,
    IN_PROGRESS,
    SUBMITTING,
    SUBMITTED,
}

data class TestDefaults(
    val questionCount: Int = DEFAULT_QUESTION_COUNT,
    val timedMode: Boolean = DEFAULT_TIMED_MODE,
    val durationMinutes: Int = DEFAULT_DURATION_MINUTES,
    val questionSource: TestQuestionSource = DEFAULT_QUESTION_SOURCE,
) {
    init {
        require(questionCount in MIN_QUESTION_COUNT..MAX_QUESTION_COUNT) {
            "questionCount must be between $MIN_QUESTION_COUNT and $MAX_QUESTION_COUNT"
        }
        require(durationMinutes in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES) {
            "durationMinutes must be between $MIN_DURATION_MINUTES and $MAX_DURATION_MINUTES"
        }
    }

    val timeLimitSeconds: Long?
        get() = (durationMinutes.toLong() * SECONDS_PER_MINUTE)
            .takeIf { timedMode }

    companion object {
        const val MIN_QUESTION_COUNT = 1
        const val MAX_QUESTION_COUNT = 30
        const val MIN_DURATION_MINUTES = 1
        const val MAX_DURATION_MINUTES = 180
        const val DEFAULT_QUESTION_COUNT = 10
        const val DEFAULT_TIMED_MODE = false
        const val DEFAULT_DURATION_MINUTES = 30
        val DEFAULT_QUESTION_SOURCE = TestQuestionSource.RANDOM

        fun defaults(): TestDefaults = TestDefaults()
    }
}

data class KeywordSnapshotEntry(
    val keyword: String,
    val weight: Double,
) {
    init {
        require(keyword.isNotBlank()) { "keyword must not be blank" }
        require(keyword == KeywordNormalizer.normalize(keyword)) {
            "keyword must already be normalized"
        }
        require(weight.isFinite() && weight > 0.0) {
            "keyword weight must be finite and positive"
        }
    }
}

object KeywordNormalizer {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .trim()
        .replace(Regex("\\s+"), " ")
}

/** Versioned JSON for immutable keyword evidence captured at attempt creation. */
object KeywordSnapshotCodec {
    const val CURRENT_VERSION = 1

    fun encode(entries: List<KeywordSnapshotEntry>): String {
        val ordered = entries.sortedBy { it.keyword }
        return buildString {
            append("{\"version\":")
            append(CURRENT_VERSION)
            append(",\"keywords\":[")
            ordered.forEachIndexed { index, entry ->
                if (index > 0) append(',')
                append("{\"keyword\":\"")
                append(escape(entry.keyword))
                append("\",\"weight\":")
                append(entry.weight.toString())
                append('}')
            }
            append("]}")
        }
    }

    fun decode(value: String): List<KeywordSnapshotEntry> {
        val root = JsonCursor(value).parseRootObject()
        require(root.keys == setOf("version", "keywords")) { "invalid keyword snapshot fields" }
        require((root["version"] as? Number)?.toInt() == CURRENT_VERSION) {
            "unsupported keyword snapshot version"
        }
        val rawEntries = root["keywords"] as? List<*>
            ?: throw IllegalArgumentException("keyword snapshot keywords must be an array")
        return rawEntries.map { raw ->
            val entry = raw as? Map<*, *> ?: throw IllegalArgumentException("invalid keyword entry")
            require(entry.keys == setOf("keyword", "weight")) { "invalid keyword entry fields" }
            val keyword = entry["keyword"] as? String
                ?: throw IllegalArgumentException("keyword must be a string")
            val weight = (entry["weight"] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("keyword weight must be a number")
            KeywordSnapshotEntry(keyword, weight)
        }.also { entries ->
            require(entries.map { it.keyword }.distinct().size == entries.size) {
                "keyword snapshot contains duplicate keywords"
            }
        }
    }

    private fun escape(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
}

private class JsonCursor(
    private val source: String,
) {
    private var index = 0

    fun parseRootObject(): Map<String, Any?> {
        val result = parseObject()
        requireEnd()
        return result
    }

    private fun parseObject(): Map<String, Any?> {
        skipWhitespace()
        expect('{')
        skipWhitespace()
        val result = linkedMapOf<String, Any?>()
        if (peek() == '}') {
            index++
            return result
        }
        while (true) {
            val key = parseString()
            skipWhitespace()
            expect(':')
            result[key] = parseValue()
            skipWhitespace()
            when (val separator = next()) {
                ',' -> Unit
                '}' -> {
                    return result
                }
                else -> throw IllegalArgumentException("unexpected JSON character '$separator'")
            }
            skipWhitespace()
        }
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        return when (peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            '-', in '0'..'9' -> parseNumber()
            else -> throw IllegalArgumentException("invalid JSON value")
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        skipWhitespace()
        val result = mutableListOf<Any?>()
        if (peek() == ']') {
            index++
            return result
        }
        while (true) {
            result += parseValue()
            skipWhitespace()
            when (val separator = next()) {
                ',' -> {
                    skipWhitespace()
                }
                ']' -> return result
                else -> throw IllegalArgumentException("unexpected JSON character '$separator'")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (index < source.length) {
            when (val character = source[index++]) {
                '"' -> return result.toString()
                '\\' -> {
                    require(index < source.length) { "unterminated JSON escape" }
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            require(index + 4 <= source.length) { "invalid unicode escape" }
                            val code = source.substring(index, index + 4).toIntOrNull(16)
                                ?: throw IllegalArgumentException("invalid unicode escape")
                            result.append(code.toChar())
                            index += 4
                        }
                        else -> throw IllegalArgumentException("invalid JSON escape")
                    }
                }
                else -> result.append(character)
            }
        }
        throw IllegalArgumentException("unterminated JSON string")
    }

    private fun parseNumber(): Number {
        val start = index
        if (peek() == '-') index++
        while (peek()?.isDigit() == true) index++
        if (peek() == '.') {
            index++
            while (peek()?.isDigit() == true) index++
        }
        if (peek() == 'e' || peek() == 'E') {
            index++
            if (peek() == '+' || peek() == '-') index++
            while (peek()?.isDigit() == true) index++
        }
        val number = source.substring(start, index)
        return number.toDoubleOrNull() ?: throw IllegalArgumentException("invalid JSON number")
    }

    private fun peek(): Char? = source.getOrNull(index)

    private fun next(): Char = source.getOrNull(index++)
        ?: throw IllegalArgumentException("unexpected end of JSON")

    private fun expect(expected: Char) {
        if (next() != expected) throw IllegalArgumentException("expected '$expected'")
    }

    private fun skipWhitespace() {
        while (peek()?.isWhitespace() == true) index++
    }

    private fun requireEnd() {
        skipWhitespace()
        require(index == source.length) { "trailing JSON content" }
    }
}
