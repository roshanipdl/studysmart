package com.studysmart.app.testing

import com.studysmart.app.core.data.BookmarkRepository
import com.studysmart.app.core.database.SavedQuestionRow
import com.studysmart.app.core.model.SavedQuestionScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class TestBookmarkRepository : BookmarkRepository {
    val ids = MutableStateFlow<Set<String>>(emptySet())
    val writes = mutableListOf<Pair<String, Boolean>>()
    override fun observeBookmarkedIds(): Flow<Set<String>> = ids
    override fun observeSavedQuestions(scope: SavedQuestionScope): Flow<List<SavedQuestionRow>> = flowOf(emptyList())
    override suspend fun setBookmarked(questionId: String, bookmarked: Boolean): Boolean {
        writes += questionId to bookmarked
        ids.value = if (bookmarked) ids.value + questionId else ids.value - questionId
        return true
    }
}
