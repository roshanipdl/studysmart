package com.studysmart.app.feature.chapters

import com.studysmart.app.core.database.ChapterDetailContextRow
import com.studysmart.app.core.database.ChapterDetailDao
import com.studysmart.app.core.database.QuestionEntity
import com.studysmart.app.core.database.TopicEntity
import com.studysmart.app.core.model.QuestionType
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterDetailRepositoryTest {
    @Test fun topicsAndTheirQuestionsPrecedeDirectQuestions() = runTest {
        val result = RoomChapterDetailRepository(FakeChapterDetailDao()).observeChapter("chapter").first()!!
        assertEquals(listOf("topic_topic", "question_owned", "question_direct"), result.items.map { it.key })
        assertEquals(2, result.questionCount)
        assertEquals("Course · Year 1 · SUBJECT", result.contextLabel)
    }

    @Test fun missingContextNeverExposesChildContent() = runTest {
        assertNull(RoomChapterDetailRepository(FakeChapterDetailDao(context = null)).observeChapter("chapter").first())
    }

    @Test fun emptyActiveContentRemainsAValidChapter() = runTest {
        val content = RoomChapterDetailRepository(FakeChapterDetailDao(topics = emptyList(), questions = emptyList()))
            .observeChapter("chapter").first()!!
        assertEquals("chapter", content.chapterId)
        assertEquals(0, content.questionCount)
    }

    @Test fun orphanTopicAndOtherChapterQuestionsAreExcluded() = runTest {
        val dao = FakeChapterDetailDao(questions = listOf(
            question("orphan", "missing"), question("other", null, "other-chapter"), question("direct", null),
        ))
        val content = RoomChapterDetailRepository(dao).observeChapter("chapter").first()!!
        assertEquals(listOf("topic_topic", "question_direct"), content.items.map { it.key })
    }

    @Test fun highlightMembershipIsValidated() = runTest {
        val content = RoomChapterDetailRepository(FakeChapterDetailDao()).observeChapter("chapter").first()!!
        assertEquals("chapter_header", content.highlightKey(ChapterHighlight(chapter = true)))
        assertEquals("topic_topic", content.highlightKey(ChapterHighlight(topicId = "topic")))
        assertEquals("question_owned", content.highlightKey(ChapterHighlight(questionId = "owned")))
        assertNull(content.highlightKey(ChapterHighlight(questionId = "other")))
        assertNull(content.highlightKey(ChapterHighlight(topicId = "missing")))
        assertNull(content.highlightKey(ChapterHighlight()))
    }
}

internal class FakeChapterDetailDao(
    private val context: ChapterDetailContextRow? = ChapterDetailContextRow("chapter", "Title", "subject", "SUBJECT", "Course", "Year 1"),
    private val topics: List<TopicEntity> = listOf(TopicEntity("topic", "chapter", "Topic", "", 0, true, Instant.EPOCH, Instant.EPOCH)),
    private val questions: List<QuestionEntity> = listOf(question("direct", null), question("owned", "topic")),
) : ChapterDetailDao {
    override suspend fun getAcademicParent(chapterId: String) = com.studysmart.app.core.database.ChapterAcademicParent("subject", "period", "course")
    override fun observeContext(chapterId: String): Flow<ChapterDetailContextRow?> = flowOf(context)
    override fun observeTopics(chapterId: String): Flow<List<TopicEntity>> = flowOf(topics)
    override fun observeQuestions(chapterId: String): Flow<List<QuestionEntity>> = flowOf(questions)
}

private fun question(id: String, topicId: String?, chapterId: String = "chapter") = QuestionEntity(
    id, chapterId, topicId, "Question $id", "Answer", QuestionType.WRITTEN, "normal", 2.0, "", true, 0,
    Instant.EPOCH, Instant.EPOCH,
)
