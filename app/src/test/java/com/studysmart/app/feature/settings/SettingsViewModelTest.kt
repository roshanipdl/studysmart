package com.studysmart.app.feature.settings

import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.model.PreparationDefaults
import com.studysmart.app.testing.TestDispatcherProvider
import com.studysmart.app.testing.TestPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Test fun themeFailureBlocksNavigationAndMutationsUntilOriginalIntentRetries() = runTest {
        val backing = TestPreferencesRepository()
        var fail = true
        val preferences = object : PreferencesRepository by backing {
            override suspend fun updateTheme(theme: com.studysmart.app.core.model.ThemePreference) {
                check(!fail)
                backing.updateTheme(theme)
            }
        }
        val vm = SettingsViewModel(preferences, TestDispatcherProvider())
        vm.setTheme(com.studysmart.app.core.model.ThemePreference.DARK)
        assertTrue(vm.uiState.value.hasSaveError)
        assertEquals(com.studysmart.app.core.model.ThemePreference.SYSTEM, vm.uiState.value.theme)
        vm.setTheme(com.studysmart.app.core.model.ThemePreference.LIGHT)
        vm.incrementQuestionCount()
        assertEquals(10, vm.uiState.value.preparationDefaults!!.questionCount)
        fail = false
        vm.retry()
        assertEquals(com.studysmart.app.core.model.ThemePreference.DARK, vm.uiState.value.theme)
        assertFalse(vm.uiState.value.hasSaveError)
        backing.updateTestDefaults(com.studysmart.app.core.model.TestDefaults(questionCount = 23, timedMode = true))
        assertEquals(23, vm.uiState.value.testDefaults!!.questionCount)
        assertTrue(vm.uiState.value.testDefaults!!.timedMode)
    }

    @Test fun goalSaveFailureIsPartOfNavigationGate() {
        assertTrue(SettingsUiState(isGoalSaveError = true).hasSaveError)
        assertTrue(SettingsUiState(isSaveError = true).hasSaveError)
        assertTrue(SettingsUiState(isPreferenceSaveError = true).hasSaveError)
        assertFalse(SettingsUiState(isLoading = false).hasSaveError)
    }
    @Test fun nameSaveCancelValidationAndRetryPreserveConfirmedValue() = runTest {
        val storedName = kotlinx.coroutines.flow.MutableStateFlow<String?>("Student")
        var fail = true
        val repository = object : com.studysmart.app.core.data.LocalUserRepository {
            override val displayName = storedName
            override suspend fun updateDisplayName(name: String) { check(!fail); storedName.value = name }
        }
        val vm = SettingsViewModel(TestPreferencesRepository(), TestDispatcherProvider(), localUserRepository = repository)
        vm.editName()
        vm.changeName("  ")
        vm.saveName()
        assertEquals("Name must not be blank.", vm.uiState.value.nameError)
        vm.cancelName()
        assertEquals(null, vm.uiState.value.nameDraft)
        vm.editName()
        vm.changeName("  Ada  ")
        vm.saveName()
        assertTrue(vm.uiState.value.hasSaveError)
        assertEquals("Student", vm.uiState.value.displayName)
        vm.incrementDailyGoal()
        assertEquals(10, vm.uiState.value.dailyCardGoal)
        fail = false
        vm.retry()
        assertEquals("Ada", storedName.value)
        assertEquals(null, vm.uiState.value.nameDraft)
        assertFalse(vm.uiState.value.hasSaveError)
    }
    @Test fun dailyGoalBoundsAndSessionCountsAreIndependent() = runTest {
        val backing = TestPreferencesRepository()
        val vm = SettingsViewModel(backing, TestDispatcherProvider())
        assertEquals(10, vm.uiState.value.dailyCardGoal)
        repeat(110) { vm.incrementDailyGoal() }
        assertEquals(100, backing.preferencesValue().dailyCardGoal)
        repeat(110) { vm.decrementDailyGoal() }
        assertEquals(1, backing.preferencesValue().dailyCardGoal)
        assertEquals(10, backing.preferencesValue().preparationDefaults.questionCount)
        assertEquals(10, backing.preferencesValue().testDefaults.questionCount)
    }

    @Test fun failedDailyGoalRetryRetainsOriginalIntentAndBlocksOtherMutations() = runTest {
        val backing = TestPreferencesRepository()
        var fail = true
        val writes = mutableListOf<Int>()
        val preferences = object : PreferencesRepository by backing {
            override suspend fun updateDailyCardGoal(goal: Int) {
                writes += goal
                check(!fail)
                backing.updateDailyCardGoal(goal)
            }
        }
        val vm = SettingsViewModel(preferences, TestDispatcherProvider())
        vm.incrementDailyGoal()
        assertTrue(vm.uiState.value.isGoalSaveError)
        assertEquals(10, vm.uiState.value.dailyCardGoal)
        vm.incrementQuestionCount()
        vm.incrementDailyGoal()
        assertEquals(1, writes.size)
        assertEquals(10, backing.preferencesValue().preparationDefaults.questionCount)
        backing.updateDailyCardGoal(30)
        assertEquals(10, vm.uiState.value.dailyCardGoal)
        fail = false
        vm.retry()
        assertEquals(listOf(11, 11), writes)
        assertEquals(11, vm.uiState.value.dailyCardGoal)
    }

    @Test fun pendingDailyGoalWritePreventsDuplicateTaps() = runTest {
        val backing = TestPreferencesRepository()
        val release = CompletableDeferred<Unit>()
        val writes = mutableListOf<Int>()
        val preferences = object : PreferencesRepository by backing {
            override suspend fun updateDailyCardGoal(goal: Int) { writes += goal; release.await(); backing.updateDailyCardGoal(goal) }
        }
        val vm = SettingsViewModel(preferences, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        vm.incrementDailyGoal()
        vm.incrementDailyGoal()
        vm.incrementQuestionCount()
        runCurrent()
        assertEquals(listOf(11), writes)
        assertTrue(vm.uiState.value.isSaving)
        release.complete(Unit)
        runCurrent()
        assertEquals(11, vm.uiState.value.dailyCardGoal)
        assertFalse(vm.uiState.value.isSaving)
    }
    @Test fun defaultsBoundsAndIndependentPersistence() = runTest {
        val backing = TestPreferencesRepository()
        val viewModel = SettingsViewModel(backing, TestDispatcherProvider())
        assertEquals(10, viewModel.uiState.value.preparationDefaults!!.questionCount)
        repeat(40) { viewModel.incrementQuestionCount() }
        assertEquals(30, backing.preferencesValue().preparationDefaults.questionCount)
        repeat(40) { viewModel.decrementQuestionCount() }
        assertEquals(1, backing.preferencesValue().preparationDefaults.questionCount)
        assertEquals(10, backing.preferencesValue().testDefaults.questionCount)
    }

    @Test fun failedSaveRetainsConfirmedValueAndRetryKeepsDesiredCount() = runTest {
        val backing = TestPreferencesRepository()
        var fail = true
        val writes = mutableListOf<PreparationDefaults>()
        val preferences = object : PreferencesRepository by backing {
            override suspend fun updatePreparationDefaults(defaults: PreparationDefaults) {
                writes += defaults
                if (fail) error("disk unavailable")
                backing.updatePreparationDefaults(defaults)
            }
        }
        val viewModel = SettingsViewModel(preferences, TestDispatcherProvider())
        viewModel.incrementQuestionCount()
        assertTrue(viewModel.uiState.value.isSaveError)
        assertEquals(10, viewModel.uiState.value.preparationDefaults!!.questionCount)
        backing.updatePreparationDefaults(PreparationDefaults(20))
        assertEquals(10, viewModel.uiState.value.preparationDefaults!!.questionCount)
        viewModel.decrementQuestionCount()
        assertEquals(1, writes.size)
        fail = false
        viewModel.retry()
        assertEquals(listOf(PreparationDefaults(11), PreparationDefaults(11)), writes)
        assertEquals(11, viewModel.uiState.value.preparationDefaults!!.questionCount)
        assertFalse(viewModel.uiState.value.isSaveError)
    }

    @Test fun admissionIsSynchronousAndPendingWriteBlocksDuplicateTaps() = runTest {
        val backing = TestPreferencesRepository()
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<PreparationDefaults>()
        val preferences = object : PreferencesRepository by backing {
            override suspend fun updatePreparationDefaults(defaults: PreparationDefaults) {
                writes += defaults
                gate.await()
                backing.updatePreparationDefaults(defaults)
            }
        }
        val viewModel = SettingsViewModel(preferences, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()
        viewModel.incrementQuestionCount()
        viewModel.incrementQuestionCount()
        viewModel.retry()
        assertTrue(viewModel.uiState.value.isSaving)
        runCurrent()
        assertEquals(listOf(PreparationDefaults(11)), writes)
        gate.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(11, viewModel.uiState.value.preparationDefaults!!.questionCount)
    }

    @Test fun loadFailureCanBeRetried() = runTest {
        val backing = TestPreferencesRepository()
        var fail = true
        val preferences = object : PreferencesRepository by backing {
            override val preferences = flow {
                if (fail) error("read failed")
                emit(StudyPreferences(preparationDefaults = PreparationDefaults(15)))
            }
        }
        val viewModel = SettingsViewModel(preferences, TestDispatcherProvider())
        assertEquals("read failed", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
        fail = false
        viewModel.retry()
        assertEquals(15, viewModel.uiState.value.preparationDefaults!!.questionCount)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test fun cancellationReleasesSaveLockAndRetryUsesOriginalValue() = runTest {
        val backing = TestPreferencesRepository()
        var cancel = true
        val preferences = object : PreferencesRepository by backing {
            override suspend fun updatePreparationDefaults(defaults: PreparationDefaults) {
                if (cancel) throw CancellationException("cancelled")
                backing.updatePreparationDefaults(defaults)
            }
        }
        val viewModel = SettingsViewModel(preferences, TestDispatcherProvider())
        viewModel.incrementQuestionCount()
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.isSaveError)
        cancel = false
        viewModel.retry()
        assertEquals(11, backing.preferencesValue().preparationDefaults.questionCount)
    }

    private suspend fun TestPreferencesRepository.preferencesValue() = preferences.first()
}
