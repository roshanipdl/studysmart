package com.studysmart.app.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import com.studysmart.app.core.model.AnswerFormat
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedAnswerContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun paragraphsEmphasisAndNestedListsRenderWithoutMarkupAtLargeFontScale() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    FormattedAnswerContent("<p>First <strong>bold</strong> <em>italic</em>.</p><ol><li>Outer<ul><li>Inner</li></ul></li></ol>", AnswerFormat.HTML)
                }
            }
        }
        compose.onNodeWithText("First bold italic.").assertIsDisplayed()
        compose.onNodeWithText("Outer").assertIsDisplayed()
        compose.onNodeWithText("Inner").assertIsDisplayed()
        compose.onNodeWithText("1.").assertIsDisplayed()
    }

    @Test fun codeKeepsNewlinesAndCanScrollHorizontally() {
        val code = "  " + "long_code ".repeat(40) + "\n\n    tail"
        compose.setContent { MaterialTheme { FormattedAnswerContent("<pre><code>$code</code></pre>", AnswerFormat.HTML) } }
        val node = compose.onNodeWithText(code).assertIsDisplayed()
        val before = node.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        node.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        val after = node.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertTrue("Code viewport must actually scroll", after > before)
        compose.onNodeWithText(code).assertIsDisplayed()
    }

    @Test fun invalidPersistedHtmlShowsErrorRatherThanMarkup() {
        compose.setContent { MaterialTheme { FormattedAnswerContent("<script>bad</script>", AnswerFormat.HTML) } }
        compose.onNodeWithText("This answer cannot be displayed because its formatting is invalid.").assertIsDisplayed()
    }

    @Test fun explicitPlainTextNeverGuessesHtml() {
        compose.setContent { MaterialTheme { FormattedAnswerContent("<p>literal</p>", AnswerFormat.PLAIN_TEXT) } }
        compose.onNodeWithText("<p>literal</p>").assertIsDisplayed()
    }
}
