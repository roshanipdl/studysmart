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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import com.studysmart.app.core.data.LocalUserRepository
import com.studysmart.app.core.database.RecentCompletedStudyRow

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun nameAndCompletedHistoryReactWithoutLosingCourses() = runTest {
        val names = MutableStateFlow<String?>(" Ada ")
        val history = MutableStateFlow<RecentCompletedStudyRow?>(null)
        val repository = object : HomeRepository {
            override fun observeCourses() = MutableStateFlow(listOf(HomeCourseUiModel("c", "C", "C", "Course", 2)))
            override fun observeRecentStudy() = history
        }
        val user = object : LocalUserRepository {
            override val displayName = names
            override suspend fun updateDisplayName(name: String) { names.value = name }
        }
        val vm = HomeViewModel(repository, TestPreferencesRepository(), TestDispatcherProvider(StandardTestDispatcher(testScheduler)), localUserRepository = user)
        runCurrent()
        assertEquals("Ada", vm.uiState.value.displayName)
        history.value = RecentCompletedStudyRow("s", "Subject", "C", "Year 1", 2, 4)
        names.value = "Grace"
        runCurrent()
        assertEquals("Grace", vm.uiState.value.displayName)
        assertEquals(2, vm.uiState.value.recentContext?.studied)
        assertEquals(1, vm.uiState.value.courses.size)
        history.value = null
        names.value = " "
        runCurrent()
        assertEquals(null, vm.uiState.value.recentContext)
        assertEquals("User", vm.uiState.value.displayName)
        assertEquals(0, vm.uiState.value.dueCount)
        assertEquals(0, vm.uiState.value.savedCount)
    }

    @Test
    fun historyFailureDoesNotHideCoursesAndRetryRecovers() = runTest {
        var fails = true
        val repository = object : HomeRepository {
            override fun observeCourses() = MutableStateFlow(listOf(HomeCourseUiModel("c", "C", "C", "Course", 2)))
            override fun observeRecentStudy() = flow<RecentCompletedStudyRow?> {
                if (fails) error("history unavailable")
                emit(null)
            }
        }
        val vm = HomeViewModel(repository, TestPreferencesRepository(), TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        assertNotNull(vm.uiState.value.historyError)
        assertEquals(null, vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.courses.size)
        fails = false
        vm.retry()
        runCurrent()
        assertEquals(null, vm.uiState.value.historyError)
    }
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
