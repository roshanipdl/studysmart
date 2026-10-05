package com.studysmart.app.core.navigation

import androidx.navigation.NavHostController

private const val BROWSE_ORIGIN_KEY = "browse_origin"
const val CONTEXT_RETURN_KEY = "context_return"
const val CHAPTER_HIGHLIGHT_TOPIC_KEY = "chapter_highlight_topic"
const val CHAPTER_HIGHLIGHT_QUESTION_KEY = "chapter_highlight_question"
const val CHAPTER_HIGHLIGHT_HEADER_KEY = "chapter_highlight_header"

/** Only scoped tab selection can return instead of continuing ordinary browsing. */
fun contextReturnDestination(route: String?): String? = route?.takeIf {
    it == AppRoutes.PROGRESS || it == AppRoutes.IMPORTANT
}

fun destinationAfterPeriodSelection(periodId: String, returnRoute: String?): String =
    contextReturnDestination(returnRoute) ?: AppRoutes.subjects(periodId)

fun chapterParentRoute(parent: com.studysmart.app.core.database.ChapterAcademicParent?): String = when {
    parent?.subjectId != null -> AppRoutes.subject(parent.subjectId)
    parent?.academicPeriodId != null -> AppRoutes.subjects(parent.academicPeriodId)
    parent?.courseId != null -> AppRoutes.academicPeriod(parent.courseId)
    else -> AppRoutes.COURSES
}

fun chapterParentRoutes(parent: com.studysmart.app.core.database.ChapterAcademicParent?): List<String> = buildList {
    parent?.courseId?.let { add(AppRoutes.academicPeriod(it)) }
    parent?.academicPeriodId?.let { add(AppRoutes.subjects(it)) }
    parent?.subjectId?.let { add(AppRoutes.subject(it)) }
}

fun NavHostController.navigateToAcademicParent(parent: com.studysmart.app.core.database.ChapterAcademicParent?) {
    val routes = chapterParentRoutes(parent)
    if (routes.isEmpty()) {
        navigateTopLevel(AppRoutes.COURSES)
    } else if (!popBackStack(routes.last(), inclusive = false)) {
        // Contextual entry may have no academic stack. Recreate its ID-only ancestry
        // so subsequent Back follows Chapters -> Subjects -> Academic Period -> Home.
        navigateTopLevel(AppRoutes.HOME)
        routes.forEach { route -> navigate(route) { launchSingleTop = true } }
    }
}

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
    currentBackStackEntry?.savedStateHandle?.remove<String>(CONTEXT_RETURN_KEY)
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

fun NavHostController.navigateAcademicPeriod(courseId: String, originRoute: String, returnRoute: String? = null) {
    navigate(AppRoutes.academicPeriod(courseId))
    currentBackStackEntry?.savedStateHandle?.set(BROWSE_ORIGIN_KEY, originRoute)
    contextReturnDestination(returnRoute)?.let {
        currentBackStackEntry?.savedStateHandle?.set(CONTEXT_RETURN_KEY, it)
    }
}

fun NavHostController.navigateContextSelection(returnRoute: String) {
    val destination = requireNotNull(contextReturnDestination(returnRoute))
    navigate(AppRoutes.COURSES)
    currentBackStackEntry?.savedStateHandle?.set(CONTEXT_RETURN_KEY, destination)
}

fun NavHostController.navigateChapterDetail(
    chapterId: String,
    topicId: String? = null,
    questionId: String? = null,
    highlightChapter: Boolean = false,
) {
    navigate(AppRoutes.chapter(chapterId))
    currentBackStackEntry?.savedStateHandle?.apply {
        set(CHAPTER_HIGHLIGHT_TOPIC_KEY, topicId)
        set(CHAPTER_HIGHLIGHT_QUESTION_KEY, questionId)
        set(CHAPTER_HIGHLIGHT_HEADER_KEY, highlightChapter)
    }
}
