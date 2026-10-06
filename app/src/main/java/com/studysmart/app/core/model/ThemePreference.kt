package com.studysmart.app.core.model

enum class ThemePreference(val label: String) {
    LIGHT("Light"), DARK("Dark"), SYSTEM("System");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemDark
    }

    companion object {
        fun fromStored(value: String?): ThemePreference = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
