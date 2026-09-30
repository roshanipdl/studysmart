package com.studysmart.app.feature.academic

import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.data.SelectedAcademicContext
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.model.AcademicPeriodBrowseModel
import com.studysmart.app.core.model.AcademicPeriodType
import com.studysmart.app.core.model.CourseBrowseModel
import com.studysmart.app.core.model.CoursePeriodsBrowseModel
import com.studysmart.app.feature.courses.CoursesViewModel
import com.studysmart.app.feature.home.HomeCourseUiModel
import com.studysmart.app.feature.home.HomeViewModel
import com.studysmart.app.testing.TestDispatcherProvider
import com.studysmart.app.testing.TestHomeRepository
import com.studysmart.app.testing.TestPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContextSelectionViewModelsTest {
    @Test
    fun homeAndCoursesAdmitOnlyOneSelectionBeforeQueuedWorkStarts() = runTest {
        listOf(true, false).forEach { home ->
            val backing = TestPreferencesRepository()
            val writes = mutableListOf<String>()
            val preferences = object : PreferencesRepository by backing {
                override suspend fun selectCourse(courseId: String) {
                    writes += courseId
                    backing.selectCourse(courseId)
                }
            }
            val screen = courseScreen(home, preferences, curriculum(), TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
            runCurrent()
            var navigations = 0
            screen.select("course-1") { navigations++ }
            assertTrue(screen.isSelecting())
            assertTrue(writes.isEmpty())
            screen.select("course-2") { navigations++ }
            screen.retrySelection()
            runCurrent()
            assertEquals(listOf("course-1"), writes)
            assertEquals(1, navigations)
            assertFalse(screen.isSelecting())
        }
    }

    @Test
    fun periodAdmitsOnlyOneSelectionBeforeQueuedWorkStarts() = runTest {
        val backing = TestPreferencesRepository()
        val writes = mutableListOf<String>()
        val preferences = object : PreferencesRepository by backing {
            override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) {
                writes += academicPeriodId
                backing.setSelectedContext(courseId, academicPeriodId)
            }
        }
        val viewModel = AcademicPeriodViewModel("course-1", curriculum(), preferences, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        var navigations = 0
        viewModel.selectPeriod("period-1") { navigations++ }
        assertTrue(viewModel.uiState.value.isSelecting)
        assertTrue(writes.isEmpty())
        viewModel.selectPeriod("another-period") { navigations++ }
        viewModel.retrySelection()
        runCurrent()
        assertEquals(listOf("period-1"), writes)
        assertEquals(1, navigations)
        assertFalse(viewModel.uiState.value.isSelecting)
    }

    @Test
    fun homeAndCoursesNavigateOnlyAfterTheCourseWriteAndBlockCompetingTaps() = runTest {
        listOf(true, false).forEach { home ->
            val backing = TestPreferencesRepository()
            val release = CompletableDeferred<Unit>()
            val writes = mutableListOf<String>()
            val preferences = object : PreferencesRepository by backing {
                override suspend fun selectCourse(courseId: String) {
                    writes += courseId
                    release.await()
                    backing.selectCourse(courseId)
                }
            }
            val screen = courseScreen(home, preferences, curriculum(), TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
            runCurrent()
            var navigations = 0
            screen.select("course-1") { navigations++ }
            screen.select("course-2") { navigations++ }
            screen.retrySelection()
            runCurrent()
            assertTrue(screen.isSelecting())
            assertEquals(listOf("course-1"), writes)
            assertEquals(0, navigations)
            release.complete(Unit)
            runCurrent()
            assertFalse(screen.isSelecting())
            assertEquals(1, navigations)
            assertEquals("course-1", backing.preferences.first().selectedCourseId)
        }
    }

    @Test
    fun courseWriteFailureKeepsContentAndRetriesTheSameSelectionOnce() = runTest {
        listOf(true, false).forEach { home ->
            val backing = TestPreferencesRepository()
            val writes = mutableListOf<String>()
            val preferences = object : PreferencesRepository by backing {
                override suspend fun selectCourse(courseId: String) {
                    writes += courseId
                    if (writes.size == 1) error("injected preference failure")
                    backing.selectCourse(courseId)
                }
            }
            val screen = courseScreen(home, preferences, curriculum(), TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
            runCurrent()
            var navigations = 0
            screen.select("course-1") { navigations++ }
            runCurrent()
            assertNotNull(screen.selectionError())
            assertFalse(screen.isSelecting())
            assertEquals(2, screen.courseCount())
            assertEquals(0, navigations)
            screen.select("course-2") { navigations++ }
            assertEquals(listOf("course-1"), writes)
            screen.retrySelection()
            runCurrent()
            assertEquals(listOf("course-1", "course-1"), writes)
            assertNull(screen.selectionError())
            assertEquals(1, navigations)
        }
    }

    @Test
    fun cancelledCourseWriteDoesNotNavigateOrBecomeAStorageError() = runTest {
        listOf(true, false).forEach { home ->
            val backing = TestPreferencesRepository()
            val preferences = object : PreferencesRepository by backing {
                override suspend fun selectCourse(courseId: String) { throw CancellationException("cancelled") }
            }
            val screen = courseScreen(home, preferences, curriculum(), TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
            runCurrent()
            var navigations = 0
            screen.select("course-1") { navigations++ }
            runCurrent()
            assertFalse(screen.isSelecting())
            assertNull(screen.selectionError())
            assertEquals(0, navigations)
            assertNull(backing.preferences.first().selectedCourseId)
        }
    }

    @Test
    fun delayedStalePeriodClearCannotEraseANewerValidPeriod() = runTest {
        listOf(true, false).forEach { home ->
            val backing = TestPreferencesRepository(StudyPreferences(SelectedAcademicContext("course-1", "old-period")))
            val release = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            var cleared: Boolean? = null
            val preferences = object : PreferencesRepository by backing {
                override suspend fun clearSelectedPeriodIfUnchanged(courseId: String, academicPeriodId: String): Boolean {
                    entered.complete(Unit)
                    // Simulates invalidation work that has already crossed a non-cancellable boundary.
                    return withContext(NonCancellable) {
                        release.await()
                        backing.clearSelectedPeriodIfUnchanged(courseId, academicPeriodId).also { cleared = it }
                    }
                }
            }
            val screen = courseScreen(home, preferences, curriculum(), TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
            runCurrent()
            assertTrue(entered.isCompleted)
            backing.setSelectedContext("course-1", "period-1")
            release.complete(Unit)
            runCurrent()
            assertEquals(false, cleared)
            assertEquals(SelectedAcademicContext("course-1", "period-1"), backing.preferences.first().selectedContext)
            assertEquals("period-1", screen.selectedPeriod())
            assertNull(screen.loadError())
        }
    }

    @Test
    fun delayedStaleCourseClearCannotEraseANewerValidContext() = runTest {
        listOf(true, false).forEach { home ->
            val backing = TestPreferencesRepository(StudyPreferences(SelectedAcademicContext("deleted-course", "old-period")))
            val release = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            var cleared: Boolean? = null
            val preferences = object : PreferencesRepository by backing {
                override suspend fun clearSelectedCourseIfUnchanged(courseId: String, academicPeriodId: String?): Boolean {
                    entered.complete(Unit)
                    return withContext(NonCancellable) {
                        release.await()
                        backing.clearSelectedCourseIfUnchanged(courseId, academicPeriodId).also { cleared = it }
                    }
                }
            }
            val screen = courseScreen(home, preferences, curriculum(), TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
            runCurrent()
            assertTrue(entered.isCompleted)
            backing.setSelectedContext("course-1", "period-1")
            release.complete(Unit)
            runCurrent()
            assertEquals(false, cleared)
            assertEquals(SelectedAcademicContext("course-1", "period-1"), backing.preferences.first().selectedContext)
            assertEquals("course-1", screen.selectedCourse())
        }
    }

    @Test
    fun validationFailureOffersLoadRetryWithoutClearingTheContext() = runTest {
        listOf(true, false).forEach { home ->
            val backing = TestPreferencesRepository(StudyPreferences(SelectedAcademicContext("course-1", "period-1")))
            var fail = true
            val repository = curriculum { _, _ ->
                if (fail) error("injected validation failure")
                true
            }
            val screen = courseScreen(home, backing, repository, TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
            runCurrent()
            assertNotNull(screen.loadError())
            assertEquals(SelectedAcademicContext("course-1", "period-1"), backing.preferences.first().selectedContext)
            fail = false
            screen.retryLoad()
            runCurrent()
            assertNull(screen.loadError())
            assertEquals("period-1", screen.selectedPeriod())
        }
    }

    @Test
    fun periodWriteWaitsForPersistenceAndBlocksCompetingSelections() = runTest {
        val backing = TestPreferencesRepository()
        val release = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val preferences = object : PreferencesRepository by backing {
            override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) {
                writes += academicPeriodId
                release.await()
                backing.setSelectedContext(courseId, academicPeriodId)
            }
        }
        val viewModel = AcademicPeriodViewModel("course-1", curriculum(), preferences, TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
        var navigations = 0
        viewModel.selectPeriod("period-1") { navigations++ }
        viewModel.selectPeriod("another-period") { navigations++ }
        viewModel.retrySelection()
        runCurrent()
        assertTrue(viewModel.uiState.value.isSelecting)
        assertEquals(listOf("period-1"), writes)
        assertEquals(0, navigations)
        release.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.isSelecting)
        assertEquals(1, navigations)
        assertEquals(SelectedAcademicContext("course-1", "period-1"), backing.preferences.first().selectedContext)
    }

    @Test
    fun periodWriteFailureRetainsTheOldPairAndRetriesWithoutReplacingContent() = runTest {
        val original = SelectedAcademicContext("course-2", "old-period")
        val backing = TestPreferencesRepository(StudyPreferences(original))
        val writes = mutableListOf<String>()
        val preferences = object : PreferencesRepository by backing {
            override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) {
                writes += academicPeriodId
                if (writes.size == 1) error("injected preference failure")
                backing.setSelectedContext(courseId, academicPeriodId)
            }
        }
        val viewModel = AcademicPeriodViewModel("course-1", curriculum(), preferences, TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
        var navigations = 0
        viewModel.selectPeriod("period-1") { navigations++ }
        runCurrent()
        assertNotNull(viewModel.uiState.value.selectionError)
        assertEquals(1, viewModel.uiState.value.content?.periods?.size)
        assertEquals(original, backing.preferences.first().selectedContext)
        assertEquals(0, navigations)
        viewModel.retrySelection()
        runCurrent()
        assertNull(viewModel.uiState.value.selectionError)
        assertEquals(listOf("period-1", "period-1"), writes)
        assertEquals(1, navigations)
    }

    @Test
    fun invalidPeriodOnRetryDoesNotPersistOrNavigate() = runTest {
        val backing = TestPreferencesRepository()
        var valid = true
        var writes = 0
        val preferences = object : PreferencesRepository by backing {
            override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) {
                writes++
                error("injected preference failure")
            }
        }
        val viewModel = AcademicPeriodViewModel("course-1", curriculum { _, _ -> valid }, preferences, TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
        var navigations = 0
        viewModel.selectPeriod("period-1") { navigations++ }
        runCurrent()
        valid = false
        viewModel.retrySelection()
        runCurrent()
        assertTrue(viewModel.uiState.value.isMissingContent)
        assertNull(viewModel.uiState.value.selectionError)
        assertFalse(viewModel.uiState.value.isSelecting)
        assertEquals(1, writes)
        assertEquals(0, navigations)
        assertNull(backing.preferences.first().selectedContext)
    }

    @Test
    fun cancelledPeriodWriteDoesNotNavigateOrBecomeAStorageError() = runTest {
        val backing = TestPreferencesRepository()
        val preferences = object : PreferencesRepository by backing {
            override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) {
                throw CancellationException("cancelled")
            }
        }
        val viewModel = AcademicPeriodViewModel("course-1", curriculum(), preferences, TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
        var navigations = 0
        viewModel.selectPeriod("period-1") { navigations++ }
        runCurrent()
        assertFalse(viewModel.uiState.value.isSelecting)
        assertNull(viewModel.uiState.value.selectionError)
        assertEquals(0, navigations)
    }

    private fun curriculum(validate: (suspend (String, String) -> Boolean)? = null): CurriculumRepository =
        FakeBrowseRepository(
            courses = listOf(course, course.copy(id = "course-2")),
            coursePeriods = CoursePeriodsBrowseModel(course, listOf(period)),
            validatePeriod = validate,
        )

    private fun courseScreen(
        home: Boolean,
        preferences: PreferencesRepository,
        curriculum: CurriculumRepository,
        dispatchers: TestDispatcherProvider,
    ): CourseSelectionScreen {
        if (home) {
            val viewModel = HomeViewModel(
                TestHomeRepository(courses = listOf(course, course.copy(id = "course-2")).map {
                    HomeCourseUiModel(it.id, it.code, it.shortName, it.name, it.periodCount, it.structureType)
                }), preferences, dispatchers, curriculum,
            )
            return CourseSelectionScreen(
                viewModel::selectCourse, viewModel::retrySelection, viewModel::retry,
                { viewModel.uiState.value.isSelecting }, { viewModel.uiState.value.selectionError },
                { viewModel.uiState.value.error }, { viewModel.uiState.value.courses.size },
                { viewModel.uiState.value.selectedCourseId }, { viewModel.uiState.value.selectedAcademicPeriodId },
            )
        }
        val viewModel = CoursesViewModel(curriculum, preferences, dispatchers)
        return CourseSelectionScreen(
            viewModel::selectCourse, viewModel::retrySelection, viewModel::retry,
            { viewModel.uiState.value.isSelecting }, { viewModel.uiState.value.selectionError },
            { viewModel.uiState.value.error }, { viewModel.uiState.value.courses.size },
            { viewModel.uiState.value.selectedCourseId }, { viewModel.uiState.value.selectedAcademicPeriodId },
        )
    }

    private data class CourseSelectionScreen(
        val select: (String, () -> Unit) -> Unit,
        val retrySelection: () -> Unit,
        val retryLoad: () -> Unit,
        val isSelecting: () -> Boolean,
        val selectionError: () -> String?,
        val loadError: () -> String?,
        val courseCount: () -> Int,
        val selectedCourse: () -> String?,
        val selectedPeriod: () -> String?,
    )

    private val course = CourseBrowseModel("course-1", "BCA", "Course", "BCA", AcademicPeriodType.SEMESTER, 1)
    private val period = AcademicPeriodBrowseModel("period-1", "course-1", 1, AcademicPeriodType.SEMESTER, "Semester 1", "Sem 1", 1)
}
