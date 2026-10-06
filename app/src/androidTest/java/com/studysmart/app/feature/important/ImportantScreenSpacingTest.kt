package com.studysmart.app.feature.important

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.studysmart.app.core.database.SavedQuestionRow
import com.studysmart.app.core.model.*
import com.studysmart.app.core.ui.StudySmartTheme
import com.studysmart.app.testing.unclippedControlBoundsInRoot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Pure UI fixtures: no app database, preferences, device setup, or data clearing. */
class ImportantScreenSpacingTest {
    @get:Rule val compose = createComposeRule()
    private var density = 1f
    private var chosen = 0
    private var prepared = 0
    private var tested = 0
    private var refreshed = 0
    private var subject: String? = null
    private var chapter: String? = null
    private val context = PeriodSubjectsBrowseModel(
        CourseBrowseModel("course", "EX", "Example", "Example", AcademicPeriodType.SEMESTER, 1),
        AcademicPeriodBrowseModel("period", "course", 1, AcademicPeriodType.SEMESTER, "Semester 1", "S1", 1),
        listOf(SubjectBrowseModel("subject", "period", "SUB", "Subject", 1)),
    )
    private fun state(saved: Boolean = true) = ImportantUiState(
        isLoading = false, context = context,
        scope = SavedQuestionScope("course", "period", "subject"),
        chapters = listOf(ChapterBrowseModel("chapter", "subject", 1, "Chapter", 1)),
        rows = if (saved) listOf(SavedQuestionRow("question", "Question", "course", "EX", "period",
            "Semester 1", "subject", "SUB", "Subject", "chapter", "Chapter", null, Instant.EPOCH)) else emptyList(),
    )
    private fun content(state: ImportantUiState = state(), fontScale: Float = 1f) {
        compose.setContent {
            StudySmartTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    density = LocalDensity.current.density
                    Box(Modifier.width(320.dp)) {
                        ImportantScreen(state, { chosen++ }, { subject = it }, { chapter = it }, { refreshed++ },
                            {}, {}, {}, { prepared++ }, { tested++ }, {}, {})
                    }
                }
            }
        }
    }
    private fun gap(first: String, second: String) {
        compose.onNodeWithTag(second).performScrollTo()
        val a = compose.onNodeWithTag(first).unclippedControlBoundsInRoot()
        val b = compose.onNodeWithTag(second).unclippedControlBoundsInRoot()
        assertEquals(12 * density, b.top - a.bottom, 1f)
        assertEquals(a.left, b.left, 1f)
        assertEquals(a.right, b.right, 1f)
        assertEquals(280 * density, a.width, 1f)
    }
    private fun spacing(fontScale: Float) {
        content(fontScale = fontScale)
        gap("important_context", "important_subject_filter")
        gap("important_subject_filter", "important_chapter_filter")
        gap("important_prepare", "important_test")
    }
    @Test fun normalFontHasPhysical12DpControlGapsAt320Dp() = spacing(1f)
    @Test fun doubleFontHasPhysical12DpControlGapsAt320Dp() = spacing(2f)

    @Test fun actionsAndDropdownsKeepTheirCallbacks() {
        content()
        compose.onNodeWithTag("important_context").performClick()
        compose.onNodeWithTag("important_subject_filter").performClick()
        compose.onNode(hasText("Subject") and hasAnyAncestor(isPopup())).performClick()
        assertEquals("subject", subject)
        compose.onNodeWithTag("important_chapter_filter").performClick()
        compose.onNode(hasText("Chapter") and hasAnyAncestor(isPopup())).performClick()
        assertEquals("chapter", chapter)
        compose.onNodeWithTag("important_prepare").performScrollTo().performClick()
        compose.onNodeWithTag("important_test").performScrollTo().performClick()
        compose.onNodeWithTag("important_refresh").performScrollTo().performClick()
        assertEquals(1, chosen)
        assertEquals(1, prepared)
        assertEquals(1, tested)
        assertEquals(1, refreshed)
    }

    @Test fun emptySavedActionsHaveNoClickAndChapterWithoutSubjectIsDisabled() {
        content(state(saved = false).copy(scope = SavedQuestionScope("course", "period")))
        compose.onNodeWithTag("important_chapter_filter").assertIsNotEnabled()
        compose.onNodeWithTag("important_prepare").assertHasNoClickAction()
        compose.onNodeWithTag("important_test").assertHasNoClickAction()
    }

    @Test fun mutationDisablesContextAndBothDropdowns() {
        content(state().copy(removingId = "question"))
        listOf("important_context", "important_subject_filter", "important_chapter_filter").forEach {
            compose.onNodeWithTag(it).assertIsNotEnabled()
        }
    }
}
