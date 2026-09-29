package com.studysmart.app.testing

import java.time.Instant
import java.time.temporal.ChronoUnit

data class FixtureCourse(
    val id: String,
    val code: String,
    val name: String,
    val periodType: FixturePeriodType,
    val periodIds: List<String>,
)

enum class FixturePeriodType {
    SEMESTER,
    YEAR,
}

data class FixtureQuestion(
    val id: String,
    val chapterId: String,
    val topicId: String?,
)

data class FixtureReviewSchedule(
    val questionId: String,
    val nextReviewAt: Instant,
)

data class FixtureAttempt(
    val id: String,
    val status: FixtureAttemptStatus,
)

enum class FixtureAttemptStatus {
    ACTIVE,
    SUBMITTED,
}

data class Mvp1FixtureMatrix(
    val localUserId: String,
    val semesterCourse: FixtureCourse,
    val yearlyCourse: FixtureCourse,
    val questions: List<FixtureQuestion>,
    val ratings: List<String>,
    val schedules: List<FixtureReviewSchedule>,
    val bookmarkedQuestionIds: Set<String>,
    val attempts: List<FixtureAttempt>,
)

object Mvp1TestFixtures {
    const val localUserId = "local-user"
    const val seedRevision = 1
    const val chapterId = "chapter-bca-s1-01"
    const val secondChapterId = "chapter-bca-s5-01"

    fun matrix(now: Instant = Instant.parse("2026-01-01T00:00:00Z")): Mvp1FixtureMatrix {
        val semesterPeriods = (1..8).map { "period-bca-semester-$it" }
        val yearlyPeriods = (1..3).map { "period-example-year-$it" }
        val questions = (1..32).map { number ->
            FixtureQuestion(
                id = "question-$number",
                chapterId = if (number > 20) secondChapterId else chapterId,
                topicId = if (number == 1) null else "topic-${number % 4}",
            )
        }
        return Mvp1FixtureMatrix(
            localUserId = localUserId,
            semesterCourse = FixtureCourse(
                id = "course-bca",
                code = "BCA",
                name = "Bachelor of Computer Applications",
                periodType = FixturePeriodType.SEMESTER,
                periodIds = semesterPeriods,
            ),
            yearlyCourse = FixtureCourse(
                id = "course-yearly-example",
                code = "YEARLY",
                name = "Example Yearly Programme",
                periodType = FixturePeriodType.YEAR,
                periodIds = yearlyPeriods,
            ),
            questions = questions,
            ratings = listOf("again", "hard", "good", "easy"),
            schedules = listOf(
                FixtureReviewSchedule("question-1", now.minus(2, ChronoUnit.DAYS)),
                FixtureReviewSchedule("question-2", now.minus(1, ChronoUnit.HOURS)),
                FixtureReviewSchedule("question-3", now.plus(3, ChronoUnit.DAYS)),
            ),
            bookmarkedQuestionIds = setOf("question-1", "question-21"),
            attempts = listOf(
                FixtureAttempt("attempt-active", FixtureAttemptStatus.ACTIVE),
                FixtureAttempt("attempt-submitted", FixtureAttemptStatus.SUBMITTED),
            ),
        )
    }
}
