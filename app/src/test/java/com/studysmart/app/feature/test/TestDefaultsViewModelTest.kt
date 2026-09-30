package com.studysmart.app.feature.test

import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.TestQuestionSource
import com.studysmart.app.testing.TestDispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestDefaultsViewModelTest {
    @Test
    fun controlsPersistDefaultsAndUntimedModeRetainsDuration() = runTest {
        val repository = FakePreferencesRepository(
            TestDefaults(questionCount = 10, timedMode = true, durationMinutes = 45),
        )
        val viewModel = viewModel(repository)

        viewModel.incrementQuestionCount()
        viewModel.setTimedMode(false)
        viewModel.setQuestionSource(TestQuestionSource.WEAK)

        assertEquals(
            TestDefaults(questionCount = 11, timedMode = false, durationMinutes = 45, questionSource = TestQuestionSource.WEAK),
            viewModel.uiState.value.defaults,
        )
        assertEquals(viewModel.uiState.value.defaults, repository.state.value.testDefaults)
        assertEquals(3, repository.writes.size)
    }

    @Test
    fun questionCountCannotCrossConfiguredBounds() = runTest {
        val repository = FakePreferencesRepository(TestDefaults(questionCount = TestDefaults.MIN_QUESTION_COUNT))
        val viewModel = viewModel(repository)

        viewModel.decrementQuestionCount()

        assertEquals(TestDefaults.MIN_QUESTION_COUNT, viewModel.uiState.value.defaults?.questionCount)
        assertTrue(repository.writes.isEmpty())

        viewModel.incrementQuestionCount()

        assertEquals(TestDefaults.MIN_QUESTION_COUNT + 1, repository.state.value.testDefaults.questionCount)

        val maximumRepository = FakePreferencesRepository(
            TestDefaults(questionCount = TestDefaults.MAX_QUESTION_COUNT),
        )
        val maximumViewModel = viewModel(maximumRepository)
        maximumViewModel.incrementQuestionCount()
        assertEquals(TestDefaults.MAX_QUESTION_COUNT, maximumViewModel.uiState.value.defaults?.questionCount)
        assertTrue(maximumRepository.writes.isEmpty())
    }

    @Test
    fun durationRequiresTimedModeAndStaysWithinBounds() = runTest {
        val repository = FakePreferencesRepository(
            TestDefaults(timedMode = true, durationMinutes = TestDefaults.MIN_DURATION_MINUTES),
        )
        val viewModel = viewModel(repository)

        viewModel.setDurationMinutes(TestDefaults.MIN_DURATION_MINUTES - 1)
        viewModel.setDurationMinutes(TestDefaults.MAX_DURATION_MINUTES + 1)
        assertTrue(repository.writes.isEmpty())

        viewModel.setDurationMinutes(TestDefaults.MAX_DURATION_MINUTES)
        assertEquals(TestDefaults.MAX_DURATION_MINUTES, repository.state.value.testDefaults.durationMinutes)

        viewModel.setTimedMode(false)
        viewModel.setDurationMinutes(TestDefaults.MIN_DURATION_MINUTES)
        assertEquals(TestDefaults.MAX_DURATION_MINUTES, repository.state.value.testDefaults.durationMinutes)
    }

    @Test
    fun failedWriteKeepsConfirmedValueAndRetryPersistsPendingValue() = runTest {
        val repository = FakePreferencesRepository(failFirstWrite = true)
        val viewModel = viewModel(repository)

        viewModel.setQuestionSource(TestQuestionSource.IMPORTANT)

        assertEquals(TestDefaults.DEFAULT_QUESTION_SOURCE, viewModel.uiState.value.defaults?.questionSource)
        assertTrue(viewModel.uiState.value.isSaveError)
        assertNotNull(viewModel.uiState.value.error)

        viewModel.retry()

        assertEquals(TestQuestionSource.IMPORTANT, viewModel.uiState.value.defaults?.questionSource)
        assertEquals(TestQuestionSource.IMPORTANT, repository.state.value.testDefaults.questionSource)
        assertFalse(viewModel.uiState.value.isSaveError)
        assertEquals(2, repository.writeAttempts)
    }

    @Test
    fun everyQuestionSourceCanBeSaved() = runTest {
        val repository = FakePreferencesRepository()
        val viewModel = viewModel(repository)

        TestQuestionSource.values().forEach(viewModel::setQuestionSource)

        assertEquals(TestQuestionSource.values().toList(), repository.writes.map { it.questionSource })
    }

    private fun viewModel(repository: FakePreferencesRepository) = TestDefaultsViewModel(
        preferencesRepository = repository,
        dispatchers = TestDispatcherProvider(),
    )
}

private class FakePreferencesRepository(
    defaults: TestDefaults = TestDefaults(),
    private val failFirstWrite: Boolean = false,
) : PreferencesRepository {
    val state = MutableStateFlow(StudyPreferences(testDefaults = defaults))
    override val preferences = state.asStateFlow()
    val writes = mutableListOf<TestDefaults>()
    var writeAttempts = 0
        private set

    override suspend fun selectCourse(courseId: String) = Unit
    override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) = Unit
    override suspend fun clearSelectedCourse() = Unit
    override suspend fun clearSelectedPeriod() = Unit
    override suspend fun clearSelectedContext() = Unit

    override suspend fun updateTestDefaults(defaults: TestDefaults) {
        writeAttempts++
        if (failFirstWrite && writeAttempts == 1) error("save failed")
        writes += defaults
        state.value = state.value.copy(testDefaults = defaults)
    }
}
