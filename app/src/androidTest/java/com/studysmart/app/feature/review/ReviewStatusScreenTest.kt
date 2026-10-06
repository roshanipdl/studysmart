package com.studysmart.app.feature.review

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onNodeWithText
import com.studysmart.app.core.ui.StudySmartTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ReviewStatusScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun noDueStillShowsAllFourStatusSections() {
        compose.setContent {
            StudySmartTheme { ReviewScreen(ReviewUiState(isLoading = false, isNoDue = true,
                contextLabel = "Example programme • Year 1"), {}, {}) }
        }
        ReviewFilter.entries.forEach { compose.onNodeWithTag("review_${it.routeValue}").assertExists() }
    }

    @Test fun emptyFilteredPoolCannotStartSession() {
        compose.setContent {
            StudySmartTheme { ReviewQuestionsScreen(ReviewUiState(isLoading = false),
                ReviewFilter.GOOD, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("No questions").assertExists()
        compose.onNodeWithTag("review_filter_start").assertIsNotEnabled().assertHasNoClickAction()
    }
}
