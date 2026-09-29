package com.studysmart.app.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRoutesTest {
    @Test
    fun canonicalRouteSetContainsEveryMvpRoute() {
        assertEquals(19, AppRoutes.allRoutes.size)
        assertEquals(AppRoutes.allRoutes.size, AppRoutes.allRoutes.toSet().size)
        assertTrue(AppRoutes.allRoutes.contains(AppRoutes.PREPARE_SESSION))
        assertTrue(AppRoutes.allRoutes.contains(AppRoutes.TEST_DEFAULTS))
    }

    @Test
    fun everyKnownRouteShowsBottomNavigationWithoutSelectingContextualRoutes() {
        assertEquals(5, AppRoutes.topLevelDestinations.size)
        assertEquals(
            setOf(
                AppRoutes.HOME,
                AppRoutes.COURSES,
                AppRoutes.REVIEW,
                AppRoutes.PROGRESS,
                AppRoutes.IMPORTANT,
            ),
            AppRoutes.topLevelDestinations.map { it.route }.toSet(),
        )
        assertTrue(AppRoutes.shouldShowBottomNavigation(AppRoutes.HOME))
        assertTrue(AppRoutes.shouldShowBottomNavigation(AppRoutes.SETTINGS))
        assertTrue(AppRoutes.shouldShowBottomNavigation(AppRoutes.PREPARE_SESSION))
        assertFalse(AppRoutes.shouldShowBottomNavigation(null))
    }

    @Test
    fun routeBuildersEncodeStableIds() {
        assertEquals("subject/id%2Fwith%2Fslash", AppRoutes.subject("id/with/slash"))
        assertEquals("review/questions/needs_improvement", AppRoutes.reviewQuestions("needs_improvement"))
    }

    @Test
    fun reviewFilterValidationAcceptsOnlyCanonicalFilters() {
        AppRoutes.supportedReviewFilters.forEach { filter ->
            assertTrue(AppRoutes.isSupportedReviewFilter(filter))
        }
        assertFalse(AppRoutes.isSupportedReviewFilter("due"))
        assertFalse(AppRoutes.isSupportedReviewFilter(null))
    }
}
