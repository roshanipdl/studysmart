package com.studysmart.app.feature.important

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.studysmart.app.core.common.DefaultDispatcherProvider
import com.studysmart.app.core.data.RoomBookmarkRepository
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.model.SavedQuestionScope
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** All mutations are confined to this test's in-memory database. */
@RunWith(AndroidJUnit4::class)
class ImportantRepositoryTest {
    private lateinit var database: StudySmartDatabase
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private fun repository(at: Instant = now) = RoomBookmarkRepository(database.bookmarkDao(),
        Clock.fixed(at, ZoneOffset.UTC), DefaultDispatcherProvider)

    @Before fun create() { database = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext()) }
    @After fun close() { database.close() }

    @Test fun repeatedSavesKeepOneFlagAndOriginalTime() = runBlocking {
        assertTrue(repository().setBookmarked("question-bca-s1-dbms-direct", true))
        assertTrue(repository(now.plusSeconds(1)).setBookmarked("question-bca-s1-dbms-direct", true))
        assertEquals(now, database.bookmarkDao().getFlag("local-user", "question-bca-s1-dbms-direct")!!.createdAt)
        assertEquals(setOf("question-bca-s1-dbms-direct"), repository().observeBookmarkedIds().first())
    }

    @Test fun scopedSavedRowsExcludeOtherPeriodsAndMismatchedFilters() = runBlocking {
        val repo = repository()
        repo.setBookmarked("question-bca-s1-dbms-direct", true)
        repo.setBookmarked("question-bca-s1-dbms-topic", true)
        val scope = SavedQuestionScope("course-bca", "period-bca-semester-1", "subject-bca-s1-dbms", "chapter-bca-s1-dbms-01")
        assertEquals(2, repo.observeSavedQuestions(scope).first().size)
        assertTrue(repo.observeSavedQuestions(scope.copy(academicPeriodId = "period-example-year-1")).first().isEmpty())
        assertTrue(repo.observeSavedQuestions(scope.copy(subjectId = "subject-year1-foundations")).first().isEmpty())
    }

    @Test fun deactivationHidesRowsWithoutDeletingFlagsAndReactivationRestoresThem() = runBlocking {
        val repo = repository()
        repo.setBookmarked("question-bca-s1-dbms-topic", true)
        val scope = SavedQuestionScope("course-bca", "period-bca-semester-1")
        database.openHelper.writableDatabase.execSQL("UPDATE topics SET isActive = 0 WHERE id = 'topic-bca-s1-dbms-01'")
        assertTrue(repo.observeSavedQuestions(scope).first().isEmpty())
        assertFalse(repo.setBookmarked("question-bca-s1-dbms-topic", true))
        assertNotNull(database.bookmarkDao().getFlag("local-user", "question-bca-s1-dbms-topic"))
        database.openHelper.writableDatabase.execSQL("UPDATE topics SET isActive = 1 WHERE id = 'topic-bca-s1-dbms-01'")
        assertEquals(1, repo.observeSavedQuestions(scope).first().size)
        assertTrue(repo.setBookmarked("question-bca-s1-dbms-topic", false))
        assertNull(database.bookmarkDao().getFlag("local-user", "question-bca-s1-dbms-topic"))
    }

    @Test fun missingQuestionIsNonDestructive() = runBlocking {
        assertFalse(repository().setBookmarked("missing", true))
        assertTrue(repository().observeBookmarkedIds().first().isEmpty())
    }

    @Test fun tiedBookmarkTimesUseStableQuestionIdRatherThanCurriculumOrder() = runBlocking {
        val repo = repository()
        repo.setBookmarked("question-bca-s1-dbms-topic", true)
        repo.setBookmarked("question-bca-s1-dbms-direct", true)
        // Both are direct questions, so the old curriculum ordering would put
        // the larger ID first. The canonical bookmark tie-break must not.
        database.openHelper.writableDatabase.execSQL("UPDATE questions SET topicId = NULL, sortOrder = 0 WHERE id = 'question-bca-s1-dbms-topic'")
        assertEquals(listOf("question-bca-s1-dbms-direct", "question-bca-s1-dbms-topic"),
            repo.observeSavedQuestions(SavedQuestionScope("course-bca", "period-bca-semester-1")).first().map { it.questionId })
    }

    @Test fun eachInactiveAncestorHidesSavedRowsAndRejectsNewSaves() = runBlocking {
        val repo = repository()
        val question = "question-bca-s1-dbms-topic"
        val scope = SavedQuestionScope("course-bca", "period-bca-semester-1")
        repo.setBookmarked(question, true)
        listOf(
            "questions" to question,
            "topics" to "topic-bca-s1-dbms-01",
            "chapters" to "chapter-bca-s1-dbms-01",
            "subjects" to "subject-bca-s1-dbms",
            "academic_periods" to "period-bca-semester-1",
            "courses" to "course-bca",
        ).forEach { (table, id) ->
            database.openHelper.writableDatabase.execSQL("UPDATE $table SET isActive = 0 WHERE id = ?", arrayOf(id))
            assertFalse(table, repo.setBookmarked(question, true))
            assertTrue(table, repo.observeSavedQuestions(scope).first().isEmpty())
            assertNotNull(database.bookmarkDao().getFlag("local-user", question))
            database.openHelper.writableDatabase.execSQL("UPDATE $table SET isActive = 1 WHERE id = ?", arrayOf(id))
            assertEquals(1, repo.observeSavedQuestions(scope).first().size)
        }
    }

    @Test fun mismatchedTopicMembershipIsExcludedWithoutDeletingFlag() = runBlocking {
        val repo = repository()
        val question = "question-bca-s1-dbms-topic"
        repo.setBookmarked(question, true)
        database.openHelper.writableDatabase.execSQL("UPDATE topics SET chapterId = 'chapter-bca-s1-dbms-02' WHERE id = 'topic-bca-s1-dbms-01'")
        assertFalse(repo.setBookmarked(question, true))
        assertTrue(repo.observeSavedQuestions(SavedQuestionScope("course-bca", "period-bca-semester-1")).first().isEmpty())
        assertNotNull(database.bookmarkDao().getFlag("local-user", question))
    }

    @Test fun allReadsAndRemovalsAreUserIsolated() = runBlocking {
        database.openHelper.writableDatabase.execSQL("INSERT INTO users(id, displayName, createdAt, updatedAt) VALUES ('other-user', 'Other', 0, 0)")
        val local = repository()
        val other = RoomBookmarkRepository(database.bookmarkDao(), Clock.fixed(now, ZoneOffset.UTC),
            DefaultDispatcherProvider, "other-user")
        val direct = "question-bca-s1-dbms-direct"
        val topic = "question-bca-s1-dbms-topic"
        val scope = SavedQuestionScope("course-bca", "period-bca-semester-1")
        local.setBookmarked(direct, true)
        other.setBookmarked(direct, true)
        other.setBookmarked(topic, true)
        assertEquals(setOf(direct), local.observeBookmarkedIds().first())
        assertEquals(setOf(direct, topic), other.observeBookmarkedIds().first())
        assertEquals(listOf(direct), local.observeSavedQuestions(scope).first().map { it.questionId })
        local.setBookmarked(direct, false)
        assertTrue(local.observeSavedQuestions(scope).first().isEmpty())
        assertEquals(2, other.observeSavedQuestions(scope).first().size)
    }
}
