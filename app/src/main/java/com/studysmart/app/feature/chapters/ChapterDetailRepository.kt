package com.studysmart.app.feature.chapters

import com.studysmart.app.core.database.ChapterDetailDao
import com.studysmart.app.core.database.ChapterAcademicParent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

sealed interface ChapterDetailItem {
    val key: String
    data class Topic(val id: String, val title: String) : ChapterDetailItem {
        override val key: String = "topic_$id"
    }
    data class Question(val id: String, val text: String, val topicId: String?) : ChapterDetailItem {
        override val key: String = "question_$id"
    }
}

data class ChapterDetailContent(
    val chapterId: String,
    val title: String,
    val subjectId: String,
    val contextLabel: String,
    val items: List<ChapterDetailItem>,
) {
    val questionCount: Int get() = items.count { it is ChapterDetailItem.Question }
}

data class ChapterHighlight(
    val topicId: String? = null,
    val questionId: String? = null,
    val chapter: Boolean = false,
)

/** Ignore stale/cross-chapter targets rather than navigating or creating learning data. */
fun ChapterDetailContent.highlightKey(target: ChapterHighlight): String? = when {
    target.questionId != null -> items.filterIsInstance<ChapterDetailItem.Question>()
        .firstOrNull { it.id == target.questionId }?.key
    target.topicId != null -> items.filterIsInstance<ChapterDetailItem.Topic>()
        .firstOrNull { it.id == target.topicId }?.key
    target.chapter -> "chapter_header"
    else -> null
}

interface ChapterDetailRepository {
    fun observeChapter(chapterId: String): Flow<ChapterDetailContent?>
    suspend fun getAcademicParent(chapterId: String): ChapterAcademicParent?
}

class RoomChapterDetailRepository(private val dao: ChapterDetailDao) : ChapterDetailRepository {
    override suspend fun getAcademicParent(chapterId: String) = dao.getAcademicParent(chapterId)
    override fun observeChapter(chapterId: String): Flow<ChapterDetailContent?> = combine(
        dao.observeContext(chapterId), dao.observeTopics(chapterId), dao.observeQuestions(chapterId),
    ) { context, topics, questions ->
        context?.let {
            val activeTopics = topics.filter { topic -> topic.chapterId == it.chapterId }
            val eligible = questions.filter { question -> question.chapterId == it.chapterId &&
                (question.topicId == null || activeTopics.any { topic -> topic.id == question.topicId }) }
            ChapterDetailContent(
                chapterId = it.chapterId,
                title = it.chapterTitle,
                subjectId = it.subjectId,
                contextLabel = "${it.courseCode} · ${it.periodName} · ${it.subjectCode}",
                items = buildList {
                    activeTopics.forEach { topic ->
                        add(ChapterDetailItem.Topic(topic.id, topic.title))
                        eligible.filter { question -> question.topicId == topic.id }.forEach { question ->
                            add(ChapterDetailItem.Question(question.id, question.question, question.topicId))
                        }
                    }
                    eligible.filter { question -> question.topicId == null }.forEach { question ->
                        add(ChapterDetailItem.Question(question.id, question.question, null))
                    }
                },
            )
        }
    }
}
