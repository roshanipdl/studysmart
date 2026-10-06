package com.studysmart.app.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.model.PreparationDefaults
import com.studysmart.app.core.ui.StudySmartTheme
import com.studysmart.app.feature.settings.SettingsScreen
import com.studysmart.app.feature.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ResponsiveLayoutTest(private val width: Int, private val height: Int, private val rtl: Boolean) {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsPrimaryActionRemainsReachableAtDoubleFontScale() {
        var opened = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                StudySmartTheme {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        SettingsScreen(
                            SettingsUiState(isLoading = false, preparationDefaults = PreparationDefaults(10), dailyCardGoal = 10),
                            onBack = {}, onOpenTestDefaults = { opened++ }, onRetry = {},
                            onDecrementCount = {}, onIncrementCount = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Test defaults").performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}, rtl={2}")
        fun sizes(): List<Array<Any>> = listOf(
            arrayOf(320, 480, false), arrayOf(320, 480, true), arrayOf(360, 640, false),
        )
    }
}
