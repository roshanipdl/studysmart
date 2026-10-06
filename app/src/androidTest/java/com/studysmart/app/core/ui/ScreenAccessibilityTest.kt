package com.studysmart.app.core.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import com.studysmart.app.core.navigation.AppRoutes
import com.studysmart.app.feature.search.SearchScreen
import com.studysmart.app.feature.search.SearchStatus
import com.studysmart.app.feature.search.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScreenAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bottomTabsExposeLabelsSelectionAndMinimumTargets() {
        var selected: String? = null
        compose.setContent { StudySmartTheme { StudyBottomNavigation(AppRoutes.REVIEW) { selected = it } } }
        AppRoutes.topLevelDestinations.forEach { destination ->
            val node = compose.onNodeWithTag("bottom_navigation_${destination.route}")
            node.assertHasClickAction().assertIsEnabled()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            if (destination.route == AppRoutes.REVIEW) node.assertIsSelected() else node.assertIsNotSelected()
            node.assertTextContains(destination.label)
            node.performClick()
            compose.runOnIdle { assertEquals(destination.route, selected) }
        }
    }

    @Test fun searchAcceptsKeyboardInputFromItsInitiallyFocusedField() {
        val query = mutableStateOf("")
        compose.setContent {
            StudySmartTheme { SearchScreen(SearchUiState(query.value, SearchStatus.EMPTY_QUERY), { query.value = it }, {}, {}, {}) }
        }
        compose.onNodeWithTag("search_input").assertIsFocused().performTextInput("database")
        compose.onNodeWithTag("search_input").assertTextContains("database")
        compose.runOnIdle { assertEquals("database", query.value) }
    }
}
