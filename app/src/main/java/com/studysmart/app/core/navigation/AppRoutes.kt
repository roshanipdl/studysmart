package com.studysmart.app.core.navigation

data class TopLevelDestination(
    val route: String,
    val label: String,
)

object AppRoutes {
    const val HOME = "home"
    const val COURSES = "courses"
    const val ACADEMIC_PERIOD = "academic-period/{courseId}"
    const val SUBJECTS = "subjects/{academicPeriodId}"
    const val SUBJECT = "subject/{subjectId}"
    const val PREPARE_CHAPTER = "prepare/chapter/{chapterId}"
    const val PREPARE_TOPIC = "prepare/topic/{topicId}"
    const val PREPARE_SESSION = "prepare/session/{sessionId}"
    const val PREPARATION_SUMMARY = "preparation-summary/{sessionId}"
    const val MOCK_TEST = "mock-test/{testAttemptId}"
    const val TEST_RESULT = "test-result/{testAttemptId}"
    const val REVIEW = "review"
    const val PROGRESS = "progress"
    const val IMPORTANT = "important"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val TEST_DEFAULTS = "settings/test-defaults"
    const val CHAPTER = "chapter/{chapterId}"
    const val REVIEW_QUESTIONS = "review/questions/{filter}"

    val topLevelDestinations = listOf(
        TopLevelDestination(HOME, "Home"),
        TopLevelDestination(COURSES, "Courses"),
        TopLevelDestination(REVIEW, "Review"),
        TopLevelDestination(PROGRESS, "Progress"),
        TopLevelDestination(IMPORTANT, "Important"),
    )

    val supportedReviewFilters = setOf(
        "needs_improvement",
        "not_studied",
        "good",
        "mastered",
    )

    val allRoutes = listOf(
        HOME,
        COURSES,
        ACADEMIC_PERIOD,
        SUBJECTS,
        SUBJECT,
        PREPARE_CHAPTER,
        PREPARE_TOPIC,
        PREPARE_SESSION,
        PREPARATION_SUMMARY,
        MOCK_TEST,
        TEST_RESULT,
        REVIEW,
        PROGRESS,
        IMPORTANT,
        SEARCH,
        SETTINGS,
        TEST_DEFAULTS,
        CHAPTER,
        REVIEW_QUESTIONS,
    )

    fun academicPeriod(courseId: String): String =
        "academic-period/${encodeRouteSegment(courseId)}"

    fun subjects(academicPeriodId: String): String =
        "subjects/${encodeRouteSegment(academicPeriodId)}"

    fun subject(subjectId: String): String = "subject/${encodeRouteSegment(subjectId)}"

    fun prepareChapter(chapterId: String): String =
        "prepare/chapter/${encodeRouteSegment(chapterId)}"

    fun prepareTopic(topicId: String): String =
        "prepare/topic/${encodeRouteSegment(topicId)}"

    fun prepareSession(sessionId: String): String =
        "prepare/session/${encodeRouteSegment(sessionId)}"

    fun preparationSummary(sessionId: String): String =
        "preparation-summary/${encodeRouteSegment(sessionId)}"

    fun mockTest(testAttemptId: String): String =
        "mock-test/${encodeRouteSegment(testAttemptId)}"

    fun testResult(testAttemptId: String): String =
        "test-result/${encodeRouteSegment(testAttemptId)}"

    fun chapter(chapterId: String): String = "chapter/${encodeRouteSegment(chapterId)}"

    fun reviewQuestions(filter: String): String =
        "review/questions/${encodeRouteSegment(filter)}"

    fun isSupportedReviewFilter(filter: String?): Boolean =
        filter != null && filter in supportedReviewFilters

    fun shouldShowBottomNavigation(route: String?): Boolean =
        route != null
}

private fun encodeRouteSegment(value: String): String {
    val encoded = StringBuilder(value.length)
    value.toByteArray(Charsets.UTF_8).forEach { byte ->
        val unsignedByte = byte.toInt() and 0xFF
        if (unsignedByte.isAllowedRouteByte()) {
            encoded.append(unsignedByte.toChar())
        } else {
            encoded.append('%')
            encoded.append(HEX_DIGITS[unsignedByte ushr 4])
            encoded.append(HEX_DIGITS[unsignedByte and 0x0F])
        }
    }
    return encoded.toString()
}

private fun Int.isAllowedRouteByte(): Boolean =
    this in 'A'.code..'Z'.code ||
        this in 'a'.code..'z'.code ||
        this in '0'.code..'9'.code ||
        this == '-'.code ||
        this == '_'.code ||
        this == '.'.code ||
        this == '!'.code ||
        this == '~'.code ||
        this == '*'.code ||
        this == '\''.code ||
        this == '('.code ||
        this == ')'.code

private const val HEX_DIGITS = "0123456789ABCDEF"
