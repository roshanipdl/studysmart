package com.studysmart.app.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.core.model.PreparationSessionStatus
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.model.TestAttemptStatus
import com.studysmart.app.feature.preparation.PreparationCreationResult
import com.studysmart.app.feature.preparation.PreparationRatingRequest
import com.studysmart.app.feature.preparation.PreparationSessionRequest
import com.studysmart.app.feature.test.ChapterTestAttemptRequest
import com.studysmart.app.feature.test.TestAnswerDraft
import com.studysmart.app.feature.test.TestAttemptCreationResult
import com.studysmart.app.feature.test.TestAttemptStartResult
import com.studysmart.app.feature.test.TestResultProjection
import com.studysmart.app.feature.test.TestResultProjector
import com.studysmart.app.feature.test.TestSubmissionResult
import com.studysmart.app.feature.test.TestSubmitAction
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.testing.createPersistentStudySmartDatabase
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoreAlphaPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "mvp10-test-${UUID.randomUUID()}.db"
    private lateinit var database: StudySmartDatabase
    private lateinit var container: AppContainer

    @Before fun openDatabase() {
        database = createPersistentStudySmartDatabase(context, databaseName)
        container = coreAlphaTestContainer(context, database)
    }

    @After fun deleteTestDatabase() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun restartRetainsCommittedHistoryAndFrozenResultsButDeletesAllTransientWork() = runBlocking {
        withTimeout(20_000) {
            val completedId = createPreparation("completed")
            repeat(2) { index -> rateCurrent(completedId, Rating.GOOD, "completed-rating-$index") }
            val completedBefore = container.preparationRepository.observeSession(completedId).first()!!
            assertEquals(PreparationSessionStatus.COMPLETED, completedBefore.status)

            val activeId = createPreparation("active")
            rateCurrent(activeId, Rating.AGAIN, "active-committed-rating")
            val committedBefore = database.preparationDao().getReviewRecord("active-committed-rating")!!
            val scheduleBefore = database.preparationDao()
                .getReviewSchedule("local-user", committedBefore.questionId)

            val submittedId = createSubmittedTest("submitted")
            val submittedBefore = container.testAttemptRepository.observeAttempt(submittedId).first()!!
            val reportBefore = TestResultProjector.project(submittedBefore)
            assertTrue(reportBefore is TestResultProjection.Available)

            val createdId = createTest("created")
            val inProgressId = createTest("in-progress")
            assertEquals(TestAttemptStartResult.Started, container.testAttemptRepository.startAttempt(inProgressId))
            val submittingId = createTest("submitting")
            assertEquals(TestAttemptStartResult.Started, container.testAttemptRepository.startAttempt(submittingId))
            // Deliberate interrupted-lifecycle fixture; retained results use the real submit transaction.
            val submitting = database.testAttemptDao().getAttempt(submittingId)!!
            database.testAttemptDao().updateAttempt(submitting.copy(status = TestAttemptStatus.SUBMITTING))

            reopenDatabase()
            assertEquals(submittedBefore, container.testAttemptRepository.observeAttempt(submittedId).first())
            container.startupCleanupCoordinator.cleanup()
            assertNull(database.preparationDao().getSession(activeId))
            assertTrue(database.preparationDao().getSessionItems(activeId).isEmpty())
            assertEquals(committedBefore.copy(preparationSessionId = null),
                database.preparationDao().getReviewRecord("active-committed-rating"))
            assertEquals(scheduleBefore, database.preparationDao()
                .getReviewSchedule("local-user", committedBefore.questionId))
            assertEquals(completedBefore, container.preparationRepository.observeSession(completedId).first())
            listOf(createdId, inProgressId, submittingId).forEach { id ->
                assertNull(database.testAttemptDao().getAttempt(id))
                assertTrue(database.testAttemptDao().observeAttemptQuestions(id).first().isEmpty())
                assertTrue(database.testAttemptDao().observeAttemptAnswers(id).first().isEmpty())
            }
            assertEquals(submittedBefore, container.testAttemptRepository.observeAttempt(submittedId).first())

            database.openHelper.writableDatabase.execSQL(
                "UPDATE questions SET question = 'Edited after restart', answer = 'Edited answer' WHERE chapterId = ?",
                arrayOf("chapter-bca-s1-dbms-01"),
            )
            database.openHelper.writableDatabase.execSQL("DELETE FROM question_keywords")
            assertEquals(reportBefore, TestResultProjector.project(
                container.testAttemptRepository.observeAttempt(submittedId).first()!!,
            ))
            container.startupCleanupCoordinator.cleanup()
            reopenDatabase()
            assertEquals(submittedBefore, container.testAttemptRepository.observeAttempt(submittedId).first())
            assertEquals(completedBefore, container.preparationRepository.observeSession(completedId).first())
            assertNotNull(database.preparationDao().getReviewRecord("completed-rating-0"))
            assertEquals(2, database.curriculumDao().observeActiveCourses().first().size)
        }
    }

    @Test
    fun cleanupFailureRollsBackBothFamiliesAndRetryIsSafe() = runBlocking {
        withTimeout(20_000) {
            val activeId = createPreparation("rollback-active")
            rateCurrent(activeId, Rating.AGAIN, "rollback-rating")
            val activeBefore = container.preparationRepository.observeSession(activeId).first()
            val recordBefore = database.preparationDao().getReviewRecord("rollback-rating")
            val testId = createTest("rollback-test")
            val submittedId = createSubmittedTest("rollback-submitted")
            val submittedBefore = container.testAttemptRepository.observeAttempt(submittedId).first()
            val sql = database.openHelper.writableDatabase
            sql.execSQL("""
                CREATE TRIGGER mvp10_cleanup_failure BEFORE DELETE ON test_attempts
                WHEN OLD.status != 'submitted'
                BEGIN SELECT RAISE(ABORT, 'injected cleanup failure'); END
            """.trimIndent())
            try {
                val result = runCatching { container.startupCleanupCoordinator.cleanup() }
                assertTrue(result.isFailure)
                assertEquals(activeBefore, container.preparationRepository.observeSession(activeId).first())
                assertEquals(recordBefore, database.preparationDao().getReviewRecord("rollback-rating"))
                assertNotNull(database.testAttemptDao().getAttempt(testId))
                assertEquals(submittedBefore, container.testAttemptRepository.observeAttempt(submittedId).first())
            } finally {
                sql.execSQL("DROP TRIGGER mvp10_cleanup_failure")
            }
            container.startupCleanupCoordinator.cleanup()
            assertNull(database.preparationDao().getSession(activeId))
            assertNull(database.testAttemptDao().getAttempt(testId))
            assertEquals(recordBefore!!.copy(preparationSessionId = null),
                database.preparationDao().getReviewRecord("rollback-rating"))
            assertEquals(submittedBefore, container.testAttemptRepository.observeAttempt(submittedId).first())
        }
    }

    private fun reopenDatabase() {
        database.close()
        openDatabase()
    }

    private suspend fun createPreparation(requestId: String): String =
        (container.preparationRepository.createSession(PreparationSessionRequest(
            PreparationScopeType.CHAPTER, "chapter-bca-s1-dbms-01", PreparationOriginType.CHAPTER,
            "chapter-bca-s1-dbms-01", requestId,
        )) as PreparationCreationResult.Created).sessionId

    private suspend fun rateCurrent(sessionId: String, rating: Rating, recordId: String) {
        val item = container.preparationRepository.observeSession(sessionId).first()!!.currentItem!!
        assertTrue(container.preparationRepository.revealCurrent(sessionId, item.questionId))
        container.preparationRepository.rateCurrent(PreparationRatingRequest(
            sessionId, item.questionId, item.presentationCount, rating, recordId,
        ))
    }

    private suspend fun createTest(requestId: String): String =
        (container.testAttemptRepository.createChapterAttempt(ChapterTestAttemptRequest(
            "chapter-bca-s1-dbms-01", creationRequestId = requestId,
        )) as TestAttemptCreationResult.Created).attemptId

    private suspend fun createSubmittedTest(requestId: String): String {
        val id = createTest(requestId)
        assertEquals(TestAttemptStartResult.Started, container.testAttemptRepository.startAttempt(id))
        val question = container.testAttemptRepository.observeAttempt(id).first()!!.questions.first()
        assertEquals(TestSubmissionResult.Submitted, container.testAttemptRepository.submitAttempt(
            id, TestSubmitAction.MANUAL,
            TestAnswerDraft(question.id, "database organised collection DBMS storage access", true, 1_500L),
        ))
        return id
    }
}
