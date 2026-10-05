package com.studysmart.app.core.data

import com.studysmart.app.core.database.SearchTextNormalizer

class SearchQuery private constructor(val normalized: String, val expression: String) {
    companion object {
        fun parse(input: String): SearchQuery? {
            val normalized = SearchTextNormalizer.normalize(input)
            if (normalized.codePointCount(0, normalized.length) < 2) return null
            // unicode61's default searchable categories are letters, numbers and private-use.
            // Punctuation is a boundary, never FTS syntax. Even OR/NOT/NEAR become quoted terms.
            val tokens = Regex("[\\p{L}\\p{N}\\p{Co}]+").findAll(normalized).map { it.value }.toList()
            if (tokens.isEmpty()) return null
            // Adjacent quoted phrases are an implicit conjunction in both FTS4 parsers.
            // Android builds without SQLITE_ENABLE_FTS3_PARENTHESIS treat AND as a
            // searchable word. FTS4 prefix '*' belongs inside the quoted phrase.
            return SearchQuery(normalized, tokens.joinToString(" ") { "\"$it*\"" })
        }
    }
}
