package com.studysmart.app.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextSelectionTest {
    @Test fun chapterBackChoosesNearestActiveAcademicParent() {
        val parent = com.studysmart.app.core.database.ChapterAcademicParent("subject", "period", "course")
        assertEquals(AppRoutes.subject("subject"), chapterParentRoute(parent))
        assertEquals(AppRoutes.subjects("period"), chapterParentRoute(parent.copy(subjectId = null)))
        assertEquals(AppRoutes.academicPeriod("course"), chapterParentRoute(parent.copy(subjectId = null, academicPeriodId = null)))
        assertEquals(AppRoutes.COURSES, chapterParentRoute(null))
    }

    @Test fun contextualChapterEntryReconstructsAcademicBackChain() {
        val parent = com.studysmart.app.core.database.ChapterAcademicParent("subject", "period", "course")
        assertEquals(listOf(AppRoutes.academicPeriod("course"), AppRoutes.subjects("period"), AppRoutes.subject("subject")),
            chapterParentRoutes(parent))
        assertEquals(listOf(AppRoutes.academicPeriod("course"), AppRoutes.subjects("period")),
            chapterParentRoutes(parent.copy(subjectId = null)))
        assertEquals(emptyList<String>(), chapterParentRoutes(null))
    }
    @Test fun supportedTabsReturnAfterReselection() {
        listOf(AppRoutes.PROGRESS, AppRoutes.IMPORTANT).forEach { route ->
            assertEquals(route, destinationAfterPeriodSelection("period-1", route))
        }
    }

    @Test fun ordinaryBrowsingContinuesToSubjects() {
        listOf(null, AppRoutes.HOME, AppRoutes.COURSES).forEach { origin ->
            assertEquals(AppRoutes.subjects("period-1"), destinationAfterPeriodSelection("period-1", origin))
        }
    }

    @Test fun malformedOrUnscopedOriginsCannotRedirectSelection() {
        listOf("https://example.com", "../important", AppRoutes.MOCK_TEST, "progress/extra").forEach {
            assertNull(contextReturnDestination(it))
        }
    }

    @Test fun normalSelectionKeepsEncodedPeriodIds() {
        assertEquals("subjects/period%2Fone", destinationAfterPeriodSelection("period/one", null))
    }
}
