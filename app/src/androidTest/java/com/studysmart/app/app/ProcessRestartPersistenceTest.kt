package com.studysmart.app.app

import android.content.Context
import android.os.Process
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.studysmart.app.core.data.DataStorePreferencesRepository
import com.studysmart.app.core.data.SelectedAcademicContext
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.core.model.Rating
import com.studysmart.app.feature.preparation.PreparationCreationResult
import com.studysmart.app.feature.preparation.PreparationRatingRequest
import com.studysmart.app.feature.preparation.PreparationSessionRequest
import com.studysmart.app.feature.test.ChapterTestAttemptRequest
import com.studysmart.app.feature.test.TestAnswerDraft
import com.studysmart.app.feature.test.TestAttemptCreationResult
import com.studysmart.app.feature.test.TestSubmissionResult
import com.studysmart.app.feature.test.TestSubmitAction
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.testing.createPersistentStudySmartDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Host runs setup, force-stops ONLY the target app, then runs verify without reinstalling.
 * Opt-in argument: -e restartPhase setup|verify. Ordinary suite runs explicitly skip this test.
 * Never uses the normal application DB or user_preferences DataStore.
 */
class ProcessRestartPersistenceTest {
    @Test fun committedDataSurvivesARealProcessRestartAndTransientWorkIsCleaned() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("restartPhase")
        assumeTrue("Requires host-controlled setup/force-stop/verify", phase == "setup" || phase == "verify")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "mvp10-test-process-restart.db"
        val marker = File(context.filesDir, "mvp10-test-process-restart.pid")
        val preferencesFile = File(context.filesDir, "mvp10-test-process-restart.preferences_pb")
        if (phase == "setup") {
            check(!context.getDatabasePath(name).exists() && !marker.exists() && !preferencesFile.exists()) {
                "Prior fixture exists; do not overwrite unverified evidence"
            }
        } else {
            check(marker.exists() && context.getDatabasePath(name).exists() && preferencesFile.exists()) {
                "Setup artifacts are missing; do not create replacement fixtures"
            }
            assertNotEquals("Verify must execute in another process", marker.readText().trim().toInt(), Process.myPid())
        }
        val database = createPersistentStudySmartDatabase(context, name)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = DataStorePreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { preferencesFile })
        val graph = coreAlphaTestContainer(context, database)
        var verified = false
        try {
            if (phase == "setup") {
                preferences.setSelectedContext("course-bca", "period-bca-semester-1")
                preferences.updateDailyCardGoal(17)
                val sessionId = (graph.preparationRepository.createSession(PreparationSessionRequest(
                    PreparationScopeType.CHAPTER, "chapter-bca-s1-dbms-01", PreparationOriginType.CHAPTER,
                    "chapter-bca-s1-dbms-01", "restart-preparation",
                )) as PreparationCreationResult.Created).sessionId
                val item = graph.preparationRepository.observeSession(sessionId).first()!!.currentItem!!
                assertTrue(graph.preparationRepository.revealCurrent(sessionId, item.questionId))
                graph.preparationRepository.rateCurrent(PreparationRatingRequest(
                    sessionId, item.questionId, item.presentationCount, Rating.AGAIN, "restart-rating",
                ))
                val submittedId = (graph.testAttemptRepository.createChapterAttempt(ChapterTestAttemptRequest(
                    "chapter-bca-s1-dbms-01", creationRequestId = "restart-submitted",
                )) as TestAttemptCreationResult.Created).attemptId
                graph.testAttemptRepository.startAttempt(submittedId)
                val question = graph.testAttemptRepository.observeAttempt(submittedId).first()!!.questions.first()
                assertEquals(TestSubmissionResult.Submitted, graph.testAttemptRepository.submitAttempt(
                    submittedId, TestSubmitAction.MANUAL, TestAnswerDraft(question.id, "database", true, 1500L),
                ))
                graph.testAttemptRepository.createChapterAttempt(ChapterTestAttemptRequest(
                    "chapter-bca-s1-dbms-01", creationRequestId = "restart-transient",
                ))
                marker.writeText(Process.myPid().toString())
            } else {
                assertEquals(1, count(database, "SELECT COUNT(*) FROM preparation_sessions WHERE status = 'active'"))
                assertEquals(2, count(database, "SELECT COUNT(*) FROM test_attempts"))
                graph.startupInitializationCoordinator.initialize()
                assertEquals(0, count(database, "SELECT COUNT(*) FROM preparation_sessions WHERE status = 'active'"))
                assertEquals(0, count(database, "SELECT COUNT(*) FROM test_attempts WHERE status != 'submitted'"))
                assertEquals(1, count(database, "SELECT COUNT(*) FROM test_attempts WHERE status = 'submitted'"))
                assertEquals(1, count(database, "SELECT COUNT(*) FROM review_records WHERE id = 'restart-rating' AND preparationSessionId IS NULL"))
                val stored = preferences.preferences.first()
                assertEquals(SelectedAcademicContext("course-bca", "period-bca-semester-1"), stored.selectedContext)
                assertEquals(17, stored.dailyCardGoal)
                verified = true
            }
        } finally {
            database.close()
            scope.cancel()
            // Failed verification retains evidence. Delete only this test's named artifacts.
            if (verified) {
                assertTrue(context.deleteDatabase(name))
                assertTrue(preferencesFile.delete())
                assertTrue(marker.delete())
            }
        }
    }

    private fun count(database: com.studysmart.app.core.database.StudySmartDatabase, sql: String): Int =
        database.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }
}
