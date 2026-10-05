package com.studysmart.app.feature.important

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.app.AppContainer
import com.studysmart.app.core.navigation.AppRoutes
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.testing.awaitNodeWithTag
import com.studysmart.app.testing.awaitNodeWithText
import com.studysmart.app.testing.coreAlphaTestContainer
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import com.studysmart.app.testing.setStudySmartContent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Mutations are limited to an isolated in-memory database and test preferences. */
class ImportantNavigationTest {
    @get:Rule val compose = createComposeRule()
    private var database: StudySmartDatabase? = null
    private fun container(saved: Boolean = false): AppContainer {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = createInMemoryStudySmartDatabase(context).also { database = it }
        return coreAlphaTestContainer(context, db).also { graph ->
            if (saved) runBlocking { graph.bookmarkRepository.setBookmarked("question-bca-s1-dbms-direct", true) }
        }
    }

    @Test fun emptyImportantKeepsFiltersAndDisablesSavedActions() {
        compose.setStudySmartContent(container(), AppRoutes.IMPORTANT)
        compose.awaitNodeWithTag("important_subject_filter")
        compose.onNodeWithTag("important_prepare").assertHasNoClickAction()
        compose.onNodeWithTag("important_test").assertHasNoClickAction()
    }

    @Test fun savedQuestionOpensHighlightedChapterDetail() {
        compose.setStudySmartContent(container(saved = true), AppRoutes.IMPORTANT)
        compose.awaitNodeWithTag("important_open_question-bca-s1-dbms-direct")
        compose.onNodeWithTag("important_open_question-bca-s1-dbms-direct").performScrollTo().performClick()
        compose.awaitNodeWithTag("question_question-bca-s1-dbms-direct")
        compose.onNodeWithTag("question_question-bca-s1-dbms-direct").assertIsSelected()
    }

    @Test fun removedRowPersistsUntilRefresh() {
        compose.setStudySmartContent(container(saved = true), AppRoutes.IMPORTANT)
        compose.awaitNodeWithTag("important_remove_question-bca-s1-dbms-direct")
        compose.onNodeWithTag("important_remove_question-bca-s1-dbms-direct").performScrollTo().performClick()
        compose.awaitNodeWithText("Removed")
        compose.onNodeWithTag("important_prepare").assertHasNoClickAction()
        compose.onNodeWithTag("important_refresh").performScrollTo().performClick()
        compose.awaitNodeWithText("No saved questions")
    }

    @Test fun prepareSavedOpensPersistedSession() {
        compose.setStudySmartContent(container(saved = true), AppRoutes.IMPORTANT)
        compose.awaitNodeWithTag("important_prepare")
        compose.onNodeWithTag("important_prepare").performScrollTo().performClick()
        compose.awaitNodeWithTag("preparation_session_screen")
    }

    @Test fun testSavedOpensPersistedAttempt() {
        compose.setStudySmartContent(container(saved = true), AppRoutes.IMPORTANT)
        compose.awaitNodeWithTag("important_test")
        compose.onNodeWithTag("important_test").performScrollTo().performClick()
        compose.awaitNodeWithTag("test_attempt_screen")
    }

    @Test fun filteredSavedSummaryNeverOffersChapterContinueAndReturnsToImportant() {
        val graph = container(saved = true)
        val sessionId = runBlocking {
            val created = graph.preparationRepository.createImportantSession(com.studysmart.app.feature.preparation.ImportantPreparationRequest(
                com.studysmart.app.core.model.SavedQuestionScope("course-bca", "period-bca-semester-1", "subject-bca-s1-dbms", "chapter-bca-s1-dbms-01"), "summary", 1))
                as com.studysmart.app.feature.preparation.PreparationCreationResult.Created
            val item = graph.preparationRepository.observeSession(created.sessionId).first()!!.currentItem!!
            graph.preparationRepository.revealCurrent(created.sessionId, item.questionId)
            graph.preparationRepository.rateCurrent(com.studysmart.app.feature.preparation.PreparationRatingRequest(created.sessionId,
                item.questionId, item.presentationCount, com.studysmart.app.core.model.Rating.GOOD, "rating"))
            created.sessionId
        }
        compose.setStudySmartContent(graph, AppRoutes.preparationSummary(sessionId))
        compose.awaitNodeWithTag("preparation_summary_back")
        compose.onNodeWithTag("preparation_summary_continue").assertDoesNotExist()
        compose.onNodeWithTag("preparation_summary_back").performScrollTo().performClick()
        compose.awaitNodeWithTag("important_screen")
    }

    @After fun close() { database?.close() }
}
