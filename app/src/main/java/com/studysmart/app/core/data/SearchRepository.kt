package com.studysmart.app.core.data

import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.database.SearchCandidateRow
import com.studysmart.app.core.database.SearchDao
import com.studysmart.app.core.database.StudySmartDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map

data class SearchContext(
    val courseId: String, val academicPeriodId: String, val subjectId: String,
    val courseName: String, val academicPeriodName: String, val subjectCode: String, val subjectName: String,
)
sealed interface SearchResult {
    val id: String
    val title: String
    val context: SearchContext
    data class Subject(override val id: String, override val title: String, override val context: SearchContext) : SearchResult
    data class Chapter(override val id: String, override val title: String, override val context: SearchContext) : SearchResult
    data class Topic(override val id: String, override val title: String, override val context: SearchContext,
        val chapterId: String, val chapterTitle: String) : SearchResult
    data class Question(override val id: String, override val title: String, override val context: SearchContext,
        val chapterId: String, val chapterTitle: String, val topicId: String?, val topicTitle: String?) : SearchResult
}
data class SearchResults(
    val subjects: List<SearchResult.Subject> = emptyList(),
    val chapters: List<SearchResult.Chapter> = emptyList(),
    val topics: List<SearchResult.Topic> = emptyList(),
    val questions: List<SearchResult.Question> = emptyList(),
)
interface SearchRepository {
    fun observeResults(query: String): Flow<SearchResults>
}
class RoomSearchRepository(
    private val dao: SearchDao,
    private val dispatchers: DispatcherProvider,
    private val database: StudySmartDatabase,
) : SearchRepository {
    override fun observeResults(query: String): Flow<SearchResults> = flow {
        val parsed = SearchQuery.parse(query)
        if (parsed == null) emit(SearchResults()) else emitAll(database.invalidationTracker.createFlow(
            "search_fts", "search_documents", "questions", "topics", "chapters",
            "subjects", "academic_periods", "courses", emitInitialState = true,
        ).map {
            database.withTransaction {
                val rows = dao.rankingCandidates(parsed.expression)
                val winners = SearchRanking.topPerType(rows, parsed)
                val display = if (winners.isEmpty()) emptyMap() else
                    dao.loadDisplayCandidates(winners.map { it.entityId }).associateBy { it.type to it.entityId }
                val ranked = winners.map { requireNotNull(display[it.type to it.entityId]) }
                fun group(type: String) = ranked.asSequence().filter { it.type == type }.take(20).map(::result).toList()
                SearchResults(group("subject").filterIsInstance<SearchResult.Subject>(),
                    group("chapter").filterIsInstance<SearchResult.Chapter>(),
                    group("topic").filterIsInstance<SearchResult.Topic>(),
                    group("question").filterIsInstance<SearchResult.Question>())
            }
        })
    }.flowOn(dispatchers.io)

    private fun result(row: SearchCandidateRow): SearchResult {
        val context = SearchContext(row.courseId, row.academicPeriodId, row.subjectId,
            row.courseName, row.academicPeriodName, row.subjectCode, row.subjectName)
        return when (row.type) {
            "subject" -> SearchResult.Subject(row.entityId, row.title, context)
            "chapter" -> SearchResult.Chapter(row.entityId, row.title, context)
            "topic" -> SearchResult.Topic(row.entityId, row.title, context, requireNotNull(row.chapterId), requireNotNull(row.chapterTitle))
            "question" -> SearchResult.Question(row.entityId, row.title, context, requireNotNull(row.chapterId),
                requireNotNull(row.chapterTitle), row.topicId, row.topicTitle)
            else -> error("Unknown search document type: ${row.type}")
        }
    }
}
