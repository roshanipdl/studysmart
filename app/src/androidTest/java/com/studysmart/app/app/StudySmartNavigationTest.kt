package com.studysmart.app.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.studysmart.app.core.navigation.AppRoutes
import com.studysmart.app.core.ui.StudySmartTheme
import com.studysmart.app.testing.awaitNodeWithTag
import com.studysmart.app.testing.awaitNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudySmartNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomNavigationSwitchesBetweenTopLevelDestinations() {
        composeRule.setContent {
            StudySmartTheme {
                StudySmartApp()
            }
        }

        composeRule.awaitNodeWithTag("bottom_navigation")

        composeRule.onNodeWithTag("bottom_navigation_courses").performClick()
        composeRule.onNodeWithTag("bottom_navigation_courses").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom_navigation_review").performClick()
        composeRule.onNodeWithTag("bottom_navigation_review").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom_navigation_progress").performClick()
        composeRule.onNodeWithTag("bottom_navigation_progress").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom_navigation_important").performClick()
        composeRule.onNodeWithTag("bottom_navigation_important").assertIsDisplayed()
    }

    @Test
    fun contextualRouteKeepsBottomNavigationAvailable() {
        composeRule.setContent {
            StudySmartTheme {
                StudySmartApp(startRoute = AppRoutes.SETTINGS)
            }
        }

        composeRule.awaitNodeWithText("Settings")

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_navigation").assertIsDisplayed()
    }

    @Test
    fun unsupportedReviewFilterShowsRecoveryState() {
        composeRule.setContent {
            StudySmartTheme {
                StudySmartApp(startRoute = AppRoutes.reviewQuestions("unsupported"))
            }
        }

        composeRule.awaitNodeWithText("This review filter is not available.")
        composeRule.onNodeWithText("This review filter is not available.").assertIsDisplayed()
    }
}
