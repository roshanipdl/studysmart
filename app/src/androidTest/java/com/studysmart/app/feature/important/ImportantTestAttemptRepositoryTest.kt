package com.studysmart.app.feature.important

import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.feature.test.*
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Uses only a test-owned in-memory database and non-persistent preferences. */
class ImportantTestAttemptRepositoryTest {
    private var database: StudySmartDatabase? = null
    private fun graph() = ApplicationProvider.getApplicationContext<android.content.Context>().let { context ->
        coreAlphaTestContainer(context, createInMemoryStudySmartDatabase(context).also { database = it })
    }
    private fun request(id: String) = ImportantTestAttemptRequest("course-bca", "period-bca-semester-1", creationRequestId = id,
        subjectId = "subject-bca-s1-dbms", chapterId = "chapter-bca-s1-dbms-01")

    @Test fun savedFiltersSurviveNewTestAndRetestAfterBookmarkRemoval() = runBlocking {
        val graph = graph()
        val repository = graph.testAttemptRepository
        seedSavedCompetitors(graph)
        val created = repository.createImportantAttempt(request("source")) as TestAttemptCreationResult.Created
        assertEquals(created, repository.createImportantAttempt(request("source")))
        val initial = repository.observeAttempt(created.attemptId).first()!!
        assertEquals(listOf("question-bca-s1-dbms-direct"), initial.questions.map { it.questionId })
        assertEquals("subject-bca-s1-dbms", initial.attempt.subjectId)
        assertEquals("chapter-bca-s1-dbms-01", initial.attempt.chapterId)
        repository.startAttempt(created.attemptId)
        assertEquals(TestSubmissionResult.Submitted, repository.submitAttempt(created.attemptId, TestSubmitAction.MANUAL, null))
        val next = repository.createNewAttemptFromResult(ResultFollowUpAttemptRequest(created.attemptId, "next")) as TestAttemptCreationResult.Created
        val nextModel = repository.observeAttempt(next.attemptId).first()!!
        assertEquals(listOf("question-bca-s1-dbms-direct"), nextModel.questions.map { it.questionId })
        assertEquals(initial.attempt.subjectId, nextModel.attempt.subjectId)
        assertEquals(initial.attempt.chapterId, nextModel.attempt.chapterId)
        graph.bookmarkRepository.setBookmarked("question-bca-s1-dbms-direct", false)
        assertTrue(repository.createNewAttemptFromResult(ResultFollowUpAttemptRequest(created.attemptId, "now-empty")) is TestAttemptCreationResult.NoQuestions)
        val retest = repository.createRetestWeakAttempt(ResultFollowUpAttemptRequest(created.attemptId, "retest")) as TestAttemptCreationResult.Created
        assertEquals(listOf("question-bca-s1-dbms-direct"), repository.observeAttempt(retest.attemptId).first()!!.questions.map { it.questionId })
        assertNotNull(repository.loadResultActionContext(created.attemptId))
    }

    @Test fun mismatchedSavedHierarchyIsRejected() = runBlocking {
        val graph = graph()
        graph.bookmarkRepository.setBookmarked("question-bca-s1-dbms-direct", true)
        assertEquals(TestAttemptCreationResult.MissingScope, graph.testAttemptRepository.createImportantAttempt(
            request("invalid").copy(academicPeriodId = "period-bca-semester-5")))
    }

    @Test fun subjectAndPeriodScopesIncludeOnlyTheirSavedCandidates() = runBlocking {
        val graph = graph()
        seedSavedCompetitors(graph)
        suspend fun selected(request: ImportantTestAttemptRequest): Set<String> {
            val result = graph.testAttemptRepository.createImportantAttempt(request) as TestAttemptCreationResult.Created
            return graph.testAttemptRepository.observeAttempt(result.attemptId).first()!!.questions.map { it.questionId }.toSet()
        }
        assertEquals(setOf("question-bca-s1-dbms-direct", "question-bca-s1-dbms-other"), selected(request("subject").copy(chapterId = null)))
        assertEquals(setOf("question-bca-s1-dbms-direct", "question-bca-s1-dbms-other", "question-bca-s1-web-01"),
            selected(request("period").copy(subjectId = null, chapterId = null)))
    }

    @Test fun creationRetryCannotReuseAnotherScopeOrUserAttempt() = runBlocking {
        val graph = graph()
        seedSavedCompetitors(graph)
        val created = graph.testAttemptRepository.createImportantAttempt(request("collision")) as TestAttemptCreationResult.Created
        assertEquals(TestAttemptCreationResult.MissingScope, graph.testAttemptRepository.createImportantAttempt(request("collision").copy(chapterId = null)))
        database!!.openHelper.writableDatabase.execSQL("INSERT INTO users(id,displayName,createdAt,updatedAt) VALUES('other-user','Other',0,0)")
        database!!.openHelper.writableDatabase.execSQL("UPDATE test_attempts SET userId='other-user' WHERE id=?", arrayOf(created.attemptId))
        assertEquals(TestAttemptCreationResult.MissingScope, graph.testAttemptRepository.createImportantAttempt(request("collision")))
    }

    @After fun close() { database?.close() }
}
