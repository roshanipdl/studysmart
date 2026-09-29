package com.studysmart.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TestModelsTest {
    @Test
    fun defaultsUseTheAcceptedValues() {
        val defaults = TestDefaults()

        assertEquals(10, defaults.questionCount)
        assertEquals(false, defaults.timedMode)
        assertEquals(30, defaults.durationMinutes)
        assertEquals(TestQuestionSource.RANDOM, defaults.questionSource)
        assertNull(defaults.timeLimitSeconds)
    }

    @Test
    fun timedDefaultsExposeSecondsAndBoundsAreEnforced() {
        val defaults = TestDefaults(
            questionCount = 30,
            timedMode = true,
            durationMinutes = 180,
            questionSource = TestQuestionSource.ALL,
        )

        assertEquals(10_800L, defaults.timeLimitSeconds)
        assertThrows(IllegalArgumentException::class.java) {
            TestDefaults(questionCount = 31)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TestDefaults(durationMinutes = 0)
        }
    }

    @Test
    fun keywordSnapshotsRoundTripEscapedTextAndStableOrdering() {
        val entries = listOf(
            KeywordSnapshotEntry("database", 2.0),
            KeywordSnapshotEntry(KeywordNormalizer.normalize("  ACID\nproperties  "), 1.5),
            KeywordSnapshotEntry("quoted \"term\"", 0.5),
        )

        val encoded = KeywordSnapshotCodec.encode(entries)
        val decoded = KeywordSnapshotCodec.decode(encoded)

        assertEquals(entries.sortedBy { it.keyword }, decoded)
    }

    @Test
    fun keywordSnapshotsRejectUnsupportedOrInvalidData() {
        assertThrows(IllegalArgumentException::class.java) {
            KeywordSnapshotCodec.decode("{\"version\":2,\"keywords\":[]}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            KeywordSnapshotEntry("Not normalized", 1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KeywordSnapshotEntry("term", 0.0)
        }
    }
}
