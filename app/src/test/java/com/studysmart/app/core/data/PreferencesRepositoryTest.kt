package com.studysmart.app.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.PreparationDefaults
import com.studysmart.app.core.model.TestQuestionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesRepositoryTest {
    @Test fun themePersistsAndInvalidValuesFallBackToSystem() = runTest {
        val store = PreferenceDataStoreFactory.create { temporaryFolder.root.resolve("theme.preferences_pb") }
        val repository = DataStorePreferencesRepository(store)
        assertEquals(com.studysmart.app.core.model.ThemePreference.SYSTEM, repository.preferences.first().theme)
        for (theme in com.studysmart.app.core.model.ThemePreference.entries) {
            repository.updateTheme(theme)
            assertEquals(theme, repository.preferences.first().theme)
        }
        store.edit { it[stringPreferencesKey("theme")] = "unknown" }
        assertEquals(com.studysmart.app.core.model.ThemePreference.SYSTEM, repository.preferences.first().theme)
        store.edit { it[intPreferencesKey("theme")] = 1 }
        assertEquals(com.studysmart.app.core.model.ThemePreference.SYSTEM, repository.preferences.first().theme)
    }
    @Test fun dailyGoalDefaultsValidationAndCorruptFallbackAreIndependent() = runTest {
        val dataStore = PreferenceDataStoreFactory.create { temporaryFolder.root.resolve("daily_goal.preferences_pb") }
        val repository = DataStorePreferencesRepository(dataStore)
        assertEquals(10, repository.preferences.first().dailyCardGoal)
        repository.updatePreparationDefaults(PreparationDefaults(17))
        repository.updateDailyCardGoal(100)
        assertEquals(100, repository.preferences.first().dailyCardGoal)
        assertEquals(17, repository.preferences.first().preparationDefaults.questionCount)
        repository.selectCourse("course")
        assertEquals(100, repository.preferences.first().dailyCardGoal)
        for (value in listOf(0, 101, -1)) {
            try { repository.updateDailyCardGoal(value); org.junit.Assert.fail("Expected invalid goal") }
            catch (_: IllegalArgumentException) { }
            dataStore.edit { it[intPreferencesKey("daily_card_goal")] = value }
            assertEquals(10, repository.preferences.first().dailyCardGoal)
        }
        dataStore.edit { it[stringPreferencesKey("daily_card_goal")] = "invalid" }
        assertEquals(10, repository.preferences.first().dailyCardGoal)
        assertEquals(17, repository.preferences.first().preparationDefaults.questionCount)
    }
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test fun sharedPreparationDefaultsAreIndependentAndInvalidStorageFallsBack() = runTest {
        val dataStore = PreferenceDataStoreFactory.create {
            temporaryFolder.root.resolve("preparation_defaults.preferences_pb")
        }
        val repository = DataStorePreferencesRepository(dataStore)
        assertEquals(PreparationDefaults(10), repository.preferences.first().preparationDefaults)
        repository.setSelectedContext("course", "period")
        val testDefaults = TestDefaults(questionCount = 23)
        repository.updateTestDefaults(testDefaults)
        repository.updatePreparationDefaults(PreparationDefaults(15))
        assertEquals(testDefaults, repository.preferences.first().testDefaults)
        assertEquals(SelectedAcademicContext("course", "period"), repository.preferences.first().selectedContext)
        repository.updateTestDefaults(TestDefaults(questionCount = 7))
        assertEquals(PreparationDefaults(15), repository.preferences.first().preparationDefaults)
        for (count in listOf(0, 31, -2)) {
            dataStore.edit { it[intPreferencesKey("preparation_review_question_count")] = count }
            assertEquals(PreparationDefaults(), repository.preferences.first().preparationDefaults)
        }
        dataStore.edit { it[stringPreferencesKey("preparation_review_question_count")] = "bad" }
        assertEquals(PreparationDefaults(), repository.preferences.first().preparationDefaults)
    }

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
    fun contextAndDefaultsSurviveClosingAndReopeningThePreferencesFile() = runTest {
        val file = temporaryFolder.root.resolve("restart.preferences_pb")
        val defaults = TestDefaults(20, true, 45, TestQuestionSource.WEAK)
        var job = SupervisorJob()
        var repository = DataStorePreferencesRepository(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file },
        ))
        try {
            repository.setSelectedContext("course-1", "period-1")
            repository.updateTestDefaults(defaults)
            repository.updatePreparationDefaults(PreparationDefaults(15))
            repository.updateDailyCardGoal(24)
            repository.updateTheme(com.studysmart.app.core.model.ThemePreference.DARK)
            job.cancelAndJoin()
            job = SupervisorJob()
            repository = DataStorePreferencesRepository(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file },
            ))
            val reopened = repository.preferences.first()
            assertEquals(SelectedAcademicContext("course-1", "period-1"), reopened.selectedContext)
            assertEquals(defaults, reopened.testDefaults)
            assertEquals(PreparationDefaults(15), reopened.preparationDefaults)
            assertEquals(24, reopened.dailyCardGoal)
            assertEquals(com.studysmart.app.core.model.ThemePreference.DARK, reopened.theme)

            repository.selectCourse("course-2")
            job.cancelAndJoin()
            job = SupervisorJob()
            repository = DataStorePreferencesRepository(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file },
            ))
            val reselected = repository.preferences.first()
            assertEquals("course-2", reselected.selectedCourseId)
            assertNull(reselected.selectedAcademicPeriodId)
            assertNull(reselected.selectedContext)
            assertEquals(defaults, reselected.testDefaults)
            assertEquals(PreparationDefaults(15), reselected.preparationDefaults)
        } finally {
            job.cancelAndJoin()
        }
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

    @Test
    fun conditionalPeriodClearKeepsCourseAndDefaultsOnlyWhenBothIdsMatch() = runTest {
        val repository = DataStorePreferencesRepository(PreferenceDataStoreFactory.create {
            temporaryFolder.root.resolve("conditional_period.preferences_pb")
        })
        val defaults = TestDefaults(15, true, 45, TestQuestionSource.WEAK)
        repository.updateTestDefaults(defaults)
        repository.setSelectedContext("course-1", "period-1")

        assertFalse(repository.clearSelectedPeriodIfUnchanged("course-2", "period-1"))
        assertFalse(repository.clearSelectedPeriodIfUnchanged("course-1", "old-period"))
        assertEquals(SelectedAcademicContext("course-1", "period-1"), repository.preferences.first().selectedContext)
        assertTrue(repository.clearSelectedPeriodIfUnchanged("course-1", "period-1"))
        val cleared = repository.preferences.first()
        assertEquals("course-1", cleared.selectedCourseId)
        assertNull(cleared.selectedAcademicPeriodId)
        assertEquals(defaults, cleared.testDefaults)
    }

    @Test
    fun staleCourseAndPeriodClearsDoNotEraseAReplacementContext() = runTest {
        val repository = DataStorePreferencesRepository(PreferenceDataStoreFactory.create {
            temporaryFolder.root.resolve("conditional_context.preferences_pb")
        })
        repository.setSelectedContext("course-1", "period-1")
        repository.setSelectedContext("course-2", "period-2")

        assertFalse(repository.clearSelectedCourseIfUnchanged("course-1", "period-1"))
        assertFalse(repository.clearSelectedPeriodIfUnchanged("course-1", "period-1"))
        assertFalse(repository.clearSelectedCourseIfUnchanged("course-2", "old-period"))
        assertEquals(SelectedAcademicContext("course-2", "period-2"), repository.preferences.first().selectedContext)
        assertTrue(repository.clearSelectedCourseIfUnchanged("course-2", "period-2"))
        assertNull(repository.preferences.first().selectedCourseId)
        assertNull(repository.preferences.first().selectedAcademicPeriodId)
    }

    @Test
    fun courseOnlyClearRequiresThePeriodToStillBeAbsent() = runTest {
        val repository = DataStorePreferencesRepository(PreferenceDataStoreFactory.create {
            temporaryFolder.root.resolve("conditional_course_only.preferences_pb")
        })
        repository.selectCourse("course-1")
        repository.setSelectedContext("course-1", "period-1")
        assertFalse(repository.clearSelectedCourseIfUnchanged("course-1", null))
        assertEquals(SelectedAcademicContext("course-1", "period-1"), repository.preferences.first().selectedContext)
        repository.selectCourse("course-1")
        assertTrue(repository.clearSelectedCourseIfUnchanged("course-1", null))
        assertNull(repository.preferences.first().selectedCourseId)
    }
}
