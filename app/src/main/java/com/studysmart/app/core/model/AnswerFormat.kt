package com.studysmart.app.core.model

enum class AnswerFormat(val storedValue: String) {
    PLAIN_TEXT("plain_text"),
    HTML("html");

    companion object {
        fun fromStoredValue(value: String): AnswerFormat =
            entries.firstOrNull { it.storedValue == value }
                ?: throw IllegalArgumentException("Unknown answer format")
    }
}
