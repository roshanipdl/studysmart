package com.studysmart.app.feature.search

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.database.SearchIndexSynchronizer
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.navigation.AppRoutes
import com.studysmart.app.testing.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Baseline seed in an isolated in-memory database; no installed app data is changed. */
class SearchNavigationTest {
    @get:Rule val compose = createComposeRule()
    private var database: StudySmartDatabase? = null

    private fun open(kind: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = createInMemoryStudySmartDatabase(context).also { database = it }
        val graph = coreAlphaTestContainer(context, db)
        val dao = db.curriculumImportDao()
        runBlocking {
            db.withTransaction {
                val subject = dao.subjects().single { it.id == "subject-bca-s1-dbms" }
                val chapter = dao.chapters().first { it.subjectId == subject.id }
                val topic = dao.topics().first { it.chapterId == chapter.id }
                val question = dao.questions().first { it.chapterId == chapter.id }
                when (kind) {
                    "subject" -> dao.updateSubject(subject.copy(name = "Unique needle"))
                    "chapter" -> dao.updateChapter(chapter.copy(title = "Unique needle"))
                    "topic" -> dao.updateTopic(topic.copy(title = "Unique needle"))
                    else -> dao.updateQuestion(question.copy(question = "Unique needle"))
                }
                SearchIndexSynchronizer.synchronize(db.openHelper.writableDatabase)
            }
        }
        val before = runBlocking { graph.preferences.preferences.first() }
        compose.setStudySmartContent(graph, AppRoutes.SEARCH)
        compose.awaitNodeWithTag("search_input")
        compose.onNodeWithTag("search_input").performTextInput("Unique needle")
        val id = runBlocking {
            val results = graph.searchRepository.observeResults("Unique needle").first()
            when (kind) {
                "subject" -> results.subjects.single().id
                "chapter" -> results.chapters.single().id
                "topic" -> results.topics.single().id
                else -> results.questions.single().id
            }
        }
        compose.awaitNodeWithTag("search_result_$id")
        compose.onNodeWithTag("search_result_$id").performScrollTo().performClick()
        when (kind) {
            "chapter" -> { compose.awaitNodeWithTag("chapter_header"); compose.onNodeWithTag("chapter_header").assertIsSelected() }
            "topic" -> { compose.awaitNodeWithTag("topic_$id"); compose.onNodeWithTag("topic_$id").assertIsSelected() }
            "question" -> { compose.awaitNodeWithTag("question_$id"); compose.onNodeWithTag("question_$id").assertIsSelected() }
            else -> {
                compose.awaitNodeWithTag("chapters_screen")
                val code = runBlocking { dao.subjects().single { it.id == id }.code }
                compose.awaitNodeWithText(code)
            }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.awaitNodeWithTag("search_input")
        compose.onNodeWithTag("search_input").assertTextContains("Unique needle")
        assertEquals(before, runBlocking { graph.preferences.preferences.first() })
    }
    @Test fun subjectResultAndQueryBack() = open("subject")
    @Test fun chapterResultAndQueryBack() = open("chapter")
    @Test fun topicResultAndQueryBack() = open("topic")
    @Test fun questionResultAndQueryBack() = open("question")
    @After fun close() { database?.close() }
}
