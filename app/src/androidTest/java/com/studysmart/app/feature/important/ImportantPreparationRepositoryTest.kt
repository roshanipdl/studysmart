package com.studysmart.app.feature.important

import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.SavedQuestionScope
import com.studysmart.app.feature.preparation.ImportantPreparationRequest
import com.studysmart.app.feature.preparation.PreparationCreationResult
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** All writes use an isolated in-memory database, never installed app data. */
class ImportantPreparationRepositoryTest {
    private var database: StudySmartDatabase? = null
    private val scope = SavedQuestionScope("course-bca", "period-bca-semester-1", "subject-bca-s1-dbms", "chapter-bca-s1-dbms-01")
    private fun graph() = ApplicationProvider.getApplicationContext<android.content.Context>().let { context ->
        coreAlphaTestContainer(context, createInMemoryStudySmartDatabase(context).also { database = it })
    }

    @Test fun savedSessionPersistsFiltersFixedItemsAndIdempotentCreation() = runBlocking {
        val graph = graph()
        graph.bookmarkRepository.setBookmarked("question-bca-s1-dbms-topic", true)
        graph.bookmarkRepository.setBookmarked("question-bca-s1-dbms-direct", true)
        val request = ImportantPreparationRequest(scope, "saved-request", 1)
        val created = graph.preparationRepository.createImportantSession(request) as PreparationCreationResult.Created
        assertEquals(created, graph.preparationRepository.createImportantSession(request))
        val session = graph.preparationRepository.observeSession(created.sessionId).first()!!
        assertEquals(PreparationScopeType.IMPORTANT, session.scopeType)
        assertEquals(PreparationOriginType.IMPORTANT, session.originType)
        assertEquals(scope.subjectId, session.subjectId)
        assertEquals(scope.chapterId, session.chapterId)
        assertEquals(listOf("question-bca-s1-dbms-direct"), session.items.map { it.questionId })
        graph.bookmarkRepository.setBookmarked("question-bca-s1-dbms-direct", false)
        assertEquals(session.items, graph.preparationRepository.observeSession(created.sessionId).first()!!.items)
    }

    @Test fun invalidHierarchyAndInactiveScopeFailWithoutSessionWrites() = runBlocking {
        val graph = graph()
        graph.bookmarkRepository.setBookmarked("question-bca-s1-dbms-direct", true)
        assertEquals(PreparationCreationResult.MissingScope, graph.preparationRepository.createImportantSession(
            ImportantPreparationRequest(scope.copy(academicPeriodId = "period-bca-semester-5"), "wrong-period")))
        database!!.openHelper.writableDatabase.execSQL("UPDATE chapters SET isActive=0 WHERE id='chapter-bca-s1-dbms-01'")
        assertEquals(PreparationCreationResult.MissingScope, graph.preparationRepository.createImportantSession(ImportantPreparationRequest(scope, "inactive")))
        assertNull(database!!.preparationDao().getSessionByCreationRequestId("wrong-period"))
        assertNull(database!!.preparationDao().getSessionByCreationRequestId("inactive"))
    }

    @Test fun emptySavedPoolDoesNotCreateSession() = runBlocking {
        val graph = graph()
        assertEquals(PreparationCreationResult.NoQuestions, graph.preparationRepository.createImportantSession(ImportantPreparationRequest(scope, "empty")))
        assertNull(database!!.preparationDao().getSessionByCreationRequestId("empty"))
    }

    @Test fun chapterSubjectAndPeriodFiltersExcludeCompetingSavedQuestions() = runBlocking {
        val graph = graph()
        seedSavedCompetitors(graph)
        suspend fun selected(scope: SavedQuestionScope, requestId: String): Set<String> {
            val result = graph.preparationRepository.createImportantSession(ImportantPreparationRequest(scope, requestId, 30)) as PreparationCreationResult.Created
            return graph.preparationRepository.observeSession(result.sessionId).first()!!.items.map { it.questionId }.toSet()
        }
        assertEquals(setOf("question-bca-s1-dbms-direct"), selected(scope, "chapter"))
        assertEquals(setOf("question-bca-s1-dbms-direct", "question-bca-s1-dbms-other"), selected(scope.copy(chapterId = null), "subject"))
        assertEquals(setOf("question-bca-s1-dbms-direct", "question-bca-s1-dbms-other", "question-bca-s1-web-01"),
            selected(scope.copy(subjectId = null, chapterId = null), "period"))
    }

    @After fun close() { database?.close() }
}
