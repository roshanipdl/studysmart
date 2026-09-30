package com.studysmart.app.feature.home

import com.studysmart.app.testing.TestDispatcherProvider
import com.studysmart.app.testing.TestHomeRepository
import com.studysmart.app.testing.TestPreferencesRepository
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun homeEmitsConfiguredCoursesAndSelectedContext() = runTest {
        val viewModel = HomeViewModel(
            homeRepository = TestHomeRepository(
                courses = listOf(
                    HomeCourseUiModel(
                        id = "course-1",
                        code = "BCA",
                        shortName = "BCA",
                        name = "Course",
                        periodCount = 8,
                    ),
                ),
            ),
            preferencesRepository = TestPreferencesRepository(),
            dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.courses.size)
        assertEquals("course-1", viewModel.uiState.value.courses.single().id)
        assertEquals("BCA", viewModel.uiState.value.courses.single().shortName)
    }

    @Test
    fun homeRepositoryFailureBecomesRetryableErrorState() = runTest {
        val viewModel = HomeViewModel(
            homeRepository = TestHomeRepository(failure = IllegalStateException("load failed")),
            preferencesRepository = TestPreferencesRepository(),
            dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.error)
        assertEquals("load failed", viewModel.uiState.value.error)
    }
}
