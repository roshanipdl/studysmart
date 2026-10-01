package com.studysmart.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PreparationDefaultsTest {
    @Test fun defaultAndBounds() {
        assertEquals(10, PreparationDefaults().questionCount)
        assertEquals(1, PreparationDefaults(1).questionCount)
        assertEquals(30, PreparationDefaults(30).questionCount)
        listOf(-1, 0, 31, Int.MAX_VALUE).forEach { count ->
            assertThrows(IllegalArgumentException::class.java) { PreparationDefaults(count) }
        }
    }
}
