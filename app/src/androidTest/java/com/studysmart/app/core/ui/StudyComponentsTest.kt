package com.studysmart.app.core.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primaryButtonHasAccessibleMinimumTouchTarget() {
        composeRule.setContent {
            StudySmartTheme {
                PrimaryButton(text = "Continue", onClick = {})
            }
        }

        composeRule.onNodeWithText("Continue")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }
}
