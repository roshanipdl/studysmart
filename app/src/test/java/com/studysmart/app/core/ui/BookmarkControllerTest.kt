package com.studysmart.app.core.ui

import com.studysmart.app.core.data.BookmarkRepository
import com.studysmart.app.core.database.SavedQuestionRow
import com.studysmart.app.core.model.SavedQuestionScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkControllerTest {
    @Test fun loadingDisablesWritesThenExplicitToggleSavesAndRemoves() = runTest {
        val repository = FakeBookmarks()
        val controller = BookmarkController(repository, backgroundScope, dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)) {}
        controller.toggle("question")
        assertTrue(repository.writes.isEmpty())
        runCurrent()
        controller.toggle("question")
        assertTrue(controller.state.isSaving)
        runCurrent()
        assertEquals(setOf("question"), controller.state.savedIds)
        controller.toggle("question")
        runCurrent()
        assertTrue(controller.state.savedIds.isEmpty())
        assertEquals(listOf("question" to true, "question" to false), repository.writes)
    }

    @Test fun failedSaveRetriesSameIntentWithoutToggling() = runTest {
        val repository = FakeBookmarks().apply { failWrite = true }
        val controller = BookmarkController(repository, backgroundScope, dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)) {}
        runCurrent()
        controller.toggle("question")
        runCurrent()
        assertTrue(controller.state.hasWriteError)
        assertTrue(controller.state.savedIds.isEmpty())
        controller.toggle("other")
        assertEquals(1, repository.writes.size)
        repository.failWrite = false
        controller.retry()
        runCurrent()
        assertEquals(listOf("question" to true, "question" to true), repository.writes)
        assertEquals(setOf("question"), controller.state.savedIds)
    }

    @Test fun pendingWriteBlocksRepeatedActionsAndStateUpdatesRemainObservable() = runTest {
        val repository = FakeBookmarks().apply { release = CompletableDeferred() }
        val controller = BookmarkController(repository, backgroundScope, dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)) {}
        runCurrent()
        controller.toggle("question")
        controller.toggle("question")
        runCurrent()
        assertTrue(controller.state.isSaving)
        assertEquals(1, repository.writes.size)
        repository.release!!.complete(Unit)
        runCurrent()
        assertFalse(controller.state.isSaving)
        repository.ids.value = setOf("external")
        runCurrent()
        assertEquals(setOf("external"), controller.state.savedIds)
    }

    @Test fun loadFailureHasRetryAndDoesNotPermitWrites() = runTest {
        val repository = FakeBookmarks().apply { failLoad = true }
        val controller = BookmarkController(repository, backgroundScope, dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)) {}
        runCurrent()
        assertTrue(controller.state.hasLoadError)
        controller.toggle("question")
        assertTrue(repository.writes.isEmpty())
        repository.failLoad = false
        controller.retry()
        runCurrent()
        assertTrue(controller.state.canChange)
    }

    @Test fun missingQuestionNeverAppearsSaved() = runTest {
        val repository = FakeBookmarks().apply { missing = true }
        val controller = BookmarkController(repository, backgroundScope, dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)) {}
        runCurrent()
        controller.toggle("question")
        runCurrent()
        assertTrue(controller.state.isMissingQuestion)
        assertTrue(controller.state.savedIds.isEmpty())
        assertEquals(setOf("question"), controller.state.unavailableIds)
        repository.missing = false
        controller.toggle("next-question")
        runCurrent()
        assertEquals(setOf("next-question"), controller.state.savedIds)
    }
}

private class FakeBookmarks : BookmarkRepository {
    val ids = MutableStateFlow<Set<String>>(emptySet())
    val writes = mutableListOf<Pair<String, Boolean>>()
    var failLoad = false
    var failWrite = false
    var missing = false
    var release: CompletableDeferred<Unit>? = null
    override fun observeBookmarkedIds(): Flow<Set<String>> = flow {
        check(!failLoad)
        ids.collect { emit(it) }
    }
    override fun observeSavedQuestions(scope: SavedQuestionScope): Flow<List<SavedQuestionRow>> = MutableStateFlow(emptyList())
    override suspend fun setBookmarked(questionId: String, bookmarked: Boolean): Boolean {
        writes += questionId to bookmarked
        release?.await()
        check(!failWrite)
        if (missing) return false
        ids.value = if (bookmarked) ids.value + questionId else ids.value - questionId
        return true
    }
}
