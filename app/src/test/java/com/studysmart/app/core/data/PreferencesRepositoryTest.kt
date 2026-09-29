package com.studysmart.app.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.TestQuestionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun selectedContextIsWrittenAndClearedAsOnePreferencePair() = runTest {
        val dataStore = PreferenceDataStoreFactory.create {
            temporaryFolder.root.resolve("user_preferences.preferences_pb")
        }
        val repository = DataStorePreferencesRepository(dataStore)

        repository.setSelectedContext("course-1", "period-1")
        assertEquals(
            SelectedAcademicContext("course-1", "period-1"),
            repository.preferences.first().selectedContext,
        )

        repository.clearSelectedContext()
        assertNull(repository.preferences.first().selectedContext)
    }

    @Test
    fun selectingCourseClearsPeriodButKeepsCourseSelection() = runTest {
        val dataStore = PreferenceDataStoreFactory.create {
            temporaryFolder.root.resolve("course_selection.preferences_pb")
        }
        val repository = DataStorePreferencesRepository(dataStore)

        repository.setSelectedContext("course-1", "period-1")
        repository.selectCourse("course-2")

        val preferences = repository.preferences.first()
        assertEquals("course-2", preferences.selectedCourseId)
        assertNull(preferences.selectedAcademicPeriodId)
        assertNull(preferences.selectedContext)
    }

    @Test
    fun clearingPeriodDoesNotSelectAnotherPeriod() = runTest {
        val dataStore = PreferenceDataStoreFactory.create {
            temporaryFolder.root.resolve("period_clear.preferences_pb")
        }
        val repository = DataStorePreferencesRepository(dataStore)

        repository.setSelectedContext("course-1", "period-1")
        repository.clearSelectedPeriod()

        val preferences = repository.preferences.first()
        assertEquals("course-1", preferences.selectedCourseId)
        assertNull(preferences.selectedAcademicPeriodId)
    }

    @Test
    fun testDefaultsPersistAndUntimedModeRetainsDuration() = runTest {
        val dataStore = PreferenceDataStoreFactory.create {
            temporaryFolder.root.resolve("test_defaults.preferences_pb")
        }
        val repository = DataStorePreferencesRepository(dataStore)

        repository.updateTestDefaults(
            TestDefaults(
                questionCount = 20,
                timedMode = true,
                durationMinutes = 45,
                questionSource = TestQuestionSource.WEAK,
            ),
        )
        repository.updateTestDefaults(
            TestDefaults(
                questionCount = 20,
                timedMode = false,
                durationMinutes = 45,
                questionSource = TestQuestionSource.WEAK,
            ),
        )

        val defaults = repository.preferences.first().testDefaults
        assertEquals(20, defaults.questionCount)
        assertEquals(false, defaults.timedMode)
        assertEquals(45, defaults.durationMinutes)
        assertEquals(TestQuestionSource.WEAK, defaults.questionSource)
    }

    @Test
    fun corruptTestDefaultsFallBackWithoutThrowing() = runTest {
        val dataStore = PreferenceDataStoreFactory.create {
            temporaryFolder.root.resolve("corrupt_test_defaults.preferences_pb")
        }
        dataStore.edit { values ->
            values[stringPreferencesKey("default_question_count")] = "bad"
            values[intPreferencesKey("default_duration_minutes")] = 999
            values[stringPreferencesKey("default_timed_mode")] = "bad"
            values[stringPreferencesKey("default_question_source")] = "future"
        }

        val defaults = DataStorePreferencesRepository(dataStore).preferences.first().testDefaults
        assertEquals(TestDefaults.DEFAULT_QUESTION_COUNT, defaults.questionCount)
        assertEquals(TestDefaults.DEFAULT_DURATION_MINUTES, defaults.durationMinutes)
        assertEquals(TestDefaults.DEFAULT_TIMED_MODE, defaults.timedMode)
        assertEquals(TestQuestionSource.RANDOM, defaults.questionSource)
    }

    @Test
    fun invalidTestDefaultsAreRejectedBeforePersistence() = runTest {
        val dataStore = PreferenceDataStoreFactory.create {
            temporaryFolder.root.resolve("invalid_test_defaults.preferences_pb")
        }
        val repository = DataStorePreferencesRepository(dataStore)

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            TestDefaults(questionCount = 31)
        }
        assertEquals(TestDefaults(), repository.preferences.first().testDefaults)
    }
}
