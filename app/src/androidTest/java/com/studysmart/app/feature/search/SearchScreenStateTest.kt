package com.studysmart.app.feature.search

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.studysmart.app.core.ui.StudySmartTheme
import org.junit.Rule
import org.junit.Test

class SearchScreenStateTest {
    @get:Rule val compose = createComposeRule()
    private fun show(status: SearchStatus, query: String = "", retry: () -> Unit = {}) {
        compose.setContent { StudySmartTheme { SearchScreen(SearchUiState(query, status), {}, retry, {}, {}) } }
    }
    @Test fun emptyQueryFocusesInputWithoutRetry() {
        show(SearchStatus.EMPTY_QUERY)
        compose.onNodeWithTag("search_input").assertIsFocused()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }
    @Test fun noResultsRetainsQueryAndEditFocusesInput() {
        show(SearchStatus.NO_RESULTS, "unmatched")
        compose.onNodeWithTag("search_input").assertTextContains("unmatched")
        compose.onNodeWithText("Edit search").performClick()
        compose.onNodeWithTag("search_input").assertIsFocused()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }
    @Test fun loadingHasNoRetry() {
        show(SearchStatus.LOADING, "local")
        compose.onNodeWithText("Searching downloaded study content…").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }
    @Test fun actualErrorOffersRetry() {
        var retries = 0
        show(SearchStatus.ERROR, "local") { retries++ }
        compose.onNodeWithText("Retry").performClick()
        org.junit.Assert.assertEquals(1, retries)
    }

    @Test fun largeFontNarrowLayoutKeepsRetryAccessibleAndTouchSized() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, 2f),
            ) {
                StudySmartTheme {
                    androidx.compose.foundation.layout.Box(Modifier.width(320.dp)) {
                        SearchScreen(SearchUiState("local", SearchStatus.ERROR), {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Retry").performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
    }
}
