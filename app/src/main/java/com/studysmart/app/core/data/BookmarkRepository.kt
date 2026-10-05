package com.studysmart.app.core.data

import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.database.BookmarkDao
import com.studysmart.app.core.database.SavedQuestionRow
import com.studysmart.app.core.model.SavedQuestionScope
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface BookmarkRepository {
    fun observeBookmarkedIds(): Flow<Set<String>>
    fun observeSavedQuestions(scope: SavedQuestionScope): Flow<List<SavedQuestionRow>>
    suspend fun setBookmarked(questionId: String, bookmarked: Boolean): Boolean
}

class RoomBookmarkRepository(
    private val dao: BookmarkDao,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    private val userId: String = "local-user",
) : BookmarkRepository {
    override fun observeBookmarkedIds(): Flow<Set<String>> = dao.observeBookmarkedIds(userId).map { it.toSet() }

    override fun observeSavedQuestions(scope: SavedQuestionScope): Flow<List<SavedQuestionRow>> =
        dao.observeSavedQuestions(userId, scope.courseId, scope.academicPeriodId, scope.subjectId, scope.chapterId)

    override suspend fun setBookmarked(questionId: String, bookmarked: Boolean): Boolean = withContext(dispatchers.io) {
        require(questionId.isNotBlank())
        dao.setBookmarked(userId, questionId, bookmarked, clock.instant())
    }
}
