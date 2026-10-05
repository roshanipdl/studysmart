package com.studysmart.app.core.data.importer

/** Syntax guard because Android's JSONTokener also accepts JavaScript-like syntax. */
internal fun validateStrictJson(text: String) {
    StrictJsonReader(text).read()
}

private class StrictJsonReader(private val text: String) {
    private var offset = 0

    fun read() {
        value(0)
        whitespace()
        require(offset == text.length) { "Trailing JSON content at $offset" }
    }

    private fun whitespace() {
        while (offset < text.length && text[offset] in " \t\r\n") offset++
    }

    private fun take(expected: Char): Boolean {
        whitespace()
        if (offset < text.length && text[offset] == expected) { offset++; return true }
        return false
    }

    private fun expect(expected: Char) {
        require(take(expected)) { "Expected JSON delimiter at $offset" }
    }

    private fun value(depth: Int) {
        require(depth < 128) { "JSON nesting limit exceeded" }
        whitespace()
        require(offset < text.length) { "Missing JSON value" }
        when (text[offset]) {
            '{' -> {
                offset++
                val keys = mutableSetOf<String>()
                if (take('}')) return
                do {
                    whitespace()
                    val key = string()
                    require(keys.add(key)) { "Duplicate JSON key at $offset" }
                    expect(':')
                    value(depth + 1)
                    if (take('}')) return
                    expect(',')
                } while (true)
            }
            '[' -> {
                offset++
                if (take(']')) return
                do {
                    value(depth + 1)
                    if (take(']')) return
                    expect(',')
                } while (true)
            }
            '"' -> string()
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            else -> {
                val match = NUMBER.find(text, offset)
                require(match != null && match.range.first == offset) { "Invalid JSON value at $offset" }
                offset = match.range.last + 1
            }
        }
    }

    private fun literal(expected: String) {
        require(text.startsWith(expected, offset)) { "Invalid JSON literal at $offset" }
        offset += expected.length
    }

    private fun string(): String {
        require(offset < text.length && text[offset++] == '"') { "Expected quoted JSON string" }
        val result = StringBuilder()
        while (offset < text.length) {
            val character = text[offset++]
            if (character == '"') return result.toString()
            require(character.code >= 32) { "Unescaped JSON control character" }
            if (character != '\\') { result.append(character); continue }
            require(offset < text.length) { "Incomplete JSON escape" }
            result.append(when (val escaped = text[offset++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000c'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    require(offset + 4 <= text.length) { "Incomplete JSON Unicode escape" }
                    val hex = text.substring(offset, offset + 4)
                    require(hex.all { it in "0123456789abcdefABCDEF" }) { "Invalid JSON Unicode escape" }
                    offset += 4
                    hex.toInt(16).toChar()
                }
                else -> error("Invalid JSON escape")
            })
        }
        error("Unterminated JSON string")
    }

    private companion object {
        val NUMBER = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
    }
}
