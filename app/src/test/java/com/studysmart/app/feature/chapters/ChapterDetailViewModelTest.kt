package com.studysmart.app.feature.chapters

import com.studysmart.app.testing.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChapterDetailViewModelTest {
    private val content = ChapterDetailContent("chapter", "Title", "subject", "Context",
        listOf(ChapterDetailItem.Question("question", "Text", null)))

    @Test fun loadingResolvesToHighlightedContent() = runTest {
        val viewModel = ChapterDetailViewModel("chapter", ChapterHighlight(questionId = "question"),
            repository(flowOf(content)), TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("question_question", viewModel.uiState.value.highlightKey)
    }

    @Test fun missingChapterHasNoHighlightOrReplacement() = runTest {
        val viewModel = ChapterDetailViewModel("missing", ChapterHighlight(chapter = true),
            repository(flowOf(null)), TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        assertNull(viewModel.uiState.value.content)
        assertNull(viewModel.uiState.value.highlightKey)
        assertFalse(viewModel.uiState.value.hasError)
    }

    @Test fun repositoryFailureOffersRetry() = runTest {
        var fail = true
        val repository = object : ChapterDetailRepository {
            override suspend fun getAcademicParent(chapterId: String) = null
            override fun observeChapter(chapterId: String): Flow<ChapterDetailContent?> = flow {
                if (fail) error("injected") else emit(content)
            }
        }
        val viewModel = ChapterDetailViewModel("chapter", ChapterHighlight(), repository,
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        assertTrue(viewModel.uiState.value.hasError)
        fail = false
        viewModel.retry()
        runCurrent()
        assertFalse(viewModel.uiState.value.hasError)
        assertEquals(content, viewModel.uiState.value.content)
    }

    @Test fun deactivationClearsPreviouslyDisplayedChapter() = runTest {
        val source = MutableStateFlow<ChapterDetailContent?>(content)
        val viewModel = ChapterDetailViewModel("chapter", ChapterHighlight(questionId = "question"),
            repository(source), TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        runCurrent()
        source.value = null
        runCurrent()
        assertNull(viewModel.uiState.value.content)
        assertNull(viewModel.uiState.value.highlightKey)
    }

    private fun repository(source: Flow<ChapterDetailContent?>) = object : ChapterDetailRepository {
        override suspend fun getAcademicParent(chapterId: String) = null
        override fun observeChapter(chapterId: String) = source
    }
}
