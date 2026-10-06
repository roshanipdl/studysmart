package com.studysmart.app.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.studysmart.app.core.ui.StudySmartTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun errorStateExposesRetryAction() {
        var retried = false
        composeRule.setContent {
            StudySmartTheme {
                HomeScreen(
                    uiState = HomeUiState(
                        isLoading = false,
                        error = "Courses failed",
                    ),
                    onRetry = { retried = true },
                    onSearch = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Courses failed").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun selectedCompactCourseUsesCodeAndSortsFirstWithoutLabel() {
        composeRule.setContent {
            StudySmartTheme {
                HomeScreen(
                    uiState = HomeUiState(
                        isLoading = false,
                        courses = listOf(
                            HomeCourseUiModel(
                                id = "course-other",
                                code = "OTHER",
                                shortName = "Other",
                                name = "Other Course",
                                periodCount = 3,
                            ),
                            HomeCourseUiModel(
                                id = "course-selected",
                                code = "BCA-CODE",
                                shortName = "Focus",
                                name = "Bachelor of Computer Applications",
                                periodCount = 8,
                            ),
                        ),
                        selectedCourseId = "course-selected",
                    ),
                    onRetry = {},
                    onSearch = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("BCA-CODE").assertIsDisplayed()
        composeRule.onNodeWithTag("course_course-selected").assertIsSelected()
        composeRule.onAllNodesWithText("Bachelor of Computer Applications").assertCountEquals(0)
        composeRule.onAllNodesWithText("Selected").assertCountEquals(0)
        assertTrue(
            composeRule.onNodeWithTag("course_course-selected")
                .fetchSemanticsNode()
                .boundsInRoot.left <
                composeRule.onNodeWithTag("course_course-other")
                    .fetchSemanticsNode()
                    .boundsInRoot.left,
        )
    }

    @Test
    fun emptyQuickActionsHaveNoClickSemanticsAndRecentStudyIsHidden() {
        composeRule.setContent {
            StudySmartTheme {
                HomeScreen(HomeUiState(isLoading = false, displayName = "Ada"), {}, {}, {})
            }
        }
        composeRule.onNodeWithText("Ada").assertIsDisplayed()
        composeRule.onNodeWithTag("home_due").assertIsNotEnabled().assertHasNoClickAction()
        composeRule.onNodeWithTag("home_saved").assertIsNotEnabled().assertHasNoClickAction()
        composeRule.onAllNodesWithText("Continue studying").assertCountEquals(0)
    }

    @Test
    fun recentStudyIsInformationalAndAvailableActionsNavigate() {
        var due = false
        var saved = false
        composeRule.setContent {
            StudySmartTheme {
                HomeScreen(HomeUiState(isLoading = false, dueCount = 2, savedCount = 1,
                    recentContext = com.studysmart.app.core.database.RecentCompletedStudyRow("s", "Subject", "C", "Year 1", 1, 2)),
                    {}, {}, {}, onDueReview = { due = true }, onSaved = { saved = true })
            }
        }
        composeRule.onNodeWithTag("home_recent").assertHasNoClickAction()
        composeRule.onNodeWithText("50% studied").assertIsDisplayed()
        composeRule.onNodeWithTag("home_due").performScrollTo().performClick()
        composeRule.onNodeWithTag("home_saved").performScrollTo().performClick()
        assertTrue(due && saved)
    }
}
