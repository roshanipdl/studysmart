package com.studysmart.app.feature.settings

import com.studysmart.app.core.data.LearningDataRepository
import com.studysmart.app.testing.TestDispatcherProvider
import com.studysmart.app.testing.TestPreferencesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsClearTest {
    @Test fun explicitConfirmationAndCancelNeverDelete() = runTest {
        var calls = 0
        val vm = SettingsViewModel(TestPreferencesRepository(), TestDispatcherProvider(),
            learningDataRepository = LearningDataRepository { calls++ })
        vm.confirmClear()
        assertEquals(0, calls)
        vm.requestClear()
        assertTrue(vm.uiState.value.clearConfirmation)
        assertFalse(vm.uiState.value.canMutate)
        vm.cancelClear()
        vm.confirmClear()
        assertEquals(0, calls)
    }

    @Test fun pendingClearBlocksDuplicateRequestsAndPreferenceWrites() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val vm = SettingsViewModel(TestPreferencesRepository(), TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
            learningDataRepository = LearningDataRepository { calls++; gate.await() })
        runCurrent()
        vm.requestClear()
        vm.confirmClear()
        vm.confirmClear()
        vm.cancelClear()
        vm.incrementQuestionCount()
        vm.incrementDailyGoal()
        vm.retry()
        runCurrent()
        assertEquals(1, calls)
        assertTrue(vm.uiState.value.isSaving)
        assertTrue(vm.uiState.value.clearConfirmation)
        assertEquals(10, vm.uiState.value.dailyCardGoal)
        gate.complete(Unit)
        runCurrent()
        assertTrue(vm.uiState.value.clearSucceeded)
        assertFalse(vm.uiState.value.clearConfirmation)
        assertFalse(vm.uiState.value.isSaving)
        vm.confirmClear()
        assertEquals(1, calls)
    }

    @Test fun failureRetainsConfirmationAndRetryCanSucceed() = runTest {
        var fail = true
        var calls = 0
        val vm = SettingsViewModel(TestPreferencesRepository(), TestDispatcherProvider(),
            learningDataRepository = LearningDataRepository { calls++; check(!fail) })
        vm.requestClear()
        vm.confirmClear()
        assertFalse(vm.uiState.value.clearSucceeded)
        assertNotNull(vm.uiState.value.clearError)
        assertTrue(vm.uiState.value.clearConfirmation)
        assertFalse(vm.uiState.value.isSaving)
        fail = false
        vm.confirmClear()
        assertEquals(2, calls)
        assertTrue(vm.uiState.value.clearSucceeded)
        assertNull(vm.uiState.value.clearError)
    }

    @Test fun failedPreferenceSavePreventsClearAdmission() = runTest {
        val backing = TestPreferencesRepository()
        val preferences = object : com.studysmart.app.core.data.PreferencesRepository by backing {
            override suspend fun updateTheme(theme: com.studysmart.app.core.model.ThemePreference) { error("disk unavailable") }
        }
        val vm = SettingsViewModel(preferences, TestDispatcherProvider(),
            learningDataRepository = LearningDataRepository {})
        vm.setTheme(com.studysmart.app.core.model.ThemePreference.DARK)
        vm.requestClear()
        assertTrue(vm.uiState.value.hasSaveError)
        assertFalse(vm.uiState.value.clearConfirmation)
        assertFalse(vm.uiState.value.clearSucceeded)
    }
}
