package com.studysmart.app.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun selectedCourseUsesShortNameAndSortsFirstWithoutLabel() {
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

        composeRule.onNodeWithText("Focus").assertIsDisplayed()
        composeRule.onAllNodesWithText("Bachelor of Computer Applications").assertCountEquals(0)
        composeRule.onAllNodesWithText("Selected").assertCountEquals(0)
        assertTrue(
            composeRule.onNodeWithTag("course_course-selected")
                .fetchSemanticsNode()
                .boundsInRoot.top <
                composeRule.onNodeWithTag("course_course-other")
                    .fetchSemanticsNode()
                    .boundsInRoot.top,
        )
    }
}
