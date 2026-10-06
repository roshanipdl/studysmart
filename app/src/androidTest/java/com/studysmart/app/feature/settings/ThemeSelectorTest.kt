package com.studysmart.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.model.ThemePreference
import com.studysmart.app.core.ui.StudySmartTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThemeSelectorTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun selectionUsesRadioSemanticsAndConfirmedState() {
        val selected = mutableStateOf(ThemePreference.SYSTEM)
        var requested: ThemePreference? = null
        composeRule.setContent {
            StudySmartTheme { ThemeSelector(selected.value, true, { requested = it }) }
        }
        composeRule.onNodeWithTag("settings_theme_group")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.SelectableGroup, Unit))
        ThemePreference.entries.forEach { theme ->
            val tile = composeRule.onNodeWithTag("settings_theme_${theme.name}")
            tile.assertTextEquals(theme.label)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            if (theme == ThemePreference.SYSTEM) tile.assertIsSelected() else tile.assertIsNotSelected()
            composeRule.onNodeWithTag("settings_theme_icon_${theme.name}", useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("settings_theme_DARK").performClick().assertIsNotSelected()
        assertEquals(ThemePreference.DARK, requested)
        composeRule.runOnIdle { selected.value = ThemePreference.DARK }
        composeRule.onNodeWithTag("settings_theme_DARK").assertIsSelected()
        composeRule.onNodeWithTag("settings_theme_SYSTEM").assertIsNotSelected()
        composeRule.onNodeWithTag("settings_theme_check_DARK", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun savingAndSaveErrorDisableThemeChanges() {
        val state = mutableStateOf(SettingsUiState(isLoading = false, isSaving = true))
        var changes = 0
        composeRule.setContent {
            StudySmartTheme { ThemeSelector(state.value.theme, state.value.canMutate, { changes++ }) }
        }
        ThemePreference.entries.forEach { composeRule.onNodeWithTag("settings_theme_${it.name}").assertIsNotEnabled() }
        composeRule.runOnIdle { state.value = state.value.copy(isSaving = false, isSaveError = true) }
        ThemePreference.entries.forEach { composeRule.onNodeWithTag("settings_theme_${it.name}").assertIsNotEnabled() }
        assertEquals(0, changes)
    }

    @Test fun narrowRowAtDoubleFontScaleHasEqualNonOverlappingAccessibleTiles() {
        var density = 1f
        composeRule.setContent {
            val base = LocalDensity.current
            density = base.density
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) {
                StudySmartTheme {
                    // 320dp screen minus existing screen and card horizontal padding.
                    Box(Modifier.width(248.dp)) { ThemeSelector(ThemePreference.LIGHT, true, {}) }
                }
            }
        }
        val row = composeRule.onNodeWithTag("settings_theme_group").fetchSemanticsNode().boundsInRoot
        val bounds = ThemePreference.entries.map { theme ->
            composeRule.onNodeWithTag("settings_theme_${theme.name}").assertIsDisplayed().assertTextEquals(theme.label)
                .fetchSemanticsNode().boundsInRoot
        }
        bounds.forEach {
            assertTrue(it.width >= 48f * density)
            assertTrue(it.height >= 48f * density)
            assertEquals(bounds.first().width, it.width, 1f)
            assertEquals(bounds.first().top, it.top, 1f)
            assertTrue(it.left >= row.left && it.right <= row.right)
        }
        bounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right < right.left) }
        ThemePreference.entries.forEach { theme ->
            val tile = composeRule.onNodeWithTag("settings_theme_${theme.name}").fetchSemanticsNode().boundsInRoot
            val layouts = mutableListOf<TextLayoutResult>()
            val label = composeRule.onNodeWithTag("settings_theme_label_${theme.name}", true).assertIsDisplayed()
            label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            assertTrue(layouts.none { it.hasVisualOverflow })
            val labelBounds = label.fetchSemanticsNode().boundsInRoot
            assertTrue(labelBounds.left >= tile.left && labelBounds.right <= tile.right && labelBounds.bottom <= tile.bottom)
            val icon = composeRule.onNodeWithTag("settings_theme_icon_${theme.name}", true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(icon.left >= tile.left && icon.right <= tile.right && icon.bottom <= tile.bottom)
        }
    }
}
