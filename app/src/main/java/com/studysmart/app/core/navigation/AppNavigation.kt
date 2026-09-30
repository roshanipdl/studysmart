package com.studysmart.app.core.navigation

import androidx.navigation.NavHostController

private const val BROWSE_ORIGIN_KEY = "browse_origin"

fun NavHostController.navigateBackOrFallback(fallbackRoute: String) {
    if (!popBackStack()) {
        val originRoute = currentBackStackEntry?.savedStateHandle?.get<String>(BROWSE_ORIGIN_KEY)
        navigate(originRoute ?: fallbackRoute) {
            launchSingleTop = true
            popUpTo(graph.startDestinationId) {
                inclusive = true
                saveState = false
            }
        }
    }
}

fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) {
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}

fun NavHostController.navigateToOrigin(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) {
            inclusive = true
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}

fun NavHostController.navigateAcademicPeriod(courseId: String, originRoute: String) {
    navigate(AppRoutes.academicPeriod(courseId))
    currentBackStackEntry?.savedStateHandle?.set(BROWSE_ORIGIN_KEY, originRoute)
}
