package com.studysmart.app.core.model

object DailyCardGoal {
    const val DEFAULT = 10
    const val MIN = 1
    const val MAX = 100
    fun normalize(value: Int?): Int = value?.takeIf { it in MIN..MAX } ?: DEFAULT
}
