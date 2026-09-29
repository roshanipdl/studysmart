package com.studysmart.app.core.model

enum class AcademicPeriodType {
    SEMESTER,
    YEAR,
}

enum class QuestionType {
    WRITTEN,
}

fun AcademicPeriodType.screenTitle(): String = when (this) {
    AcademicPeriodType.SEMESTER -> "Choose Semester"
    AcademicPeriodType.YEAR -> "Choose Year"
}

fun AcademicPeriodType.structureLabel(): String = when (this) {
    AcademicPeriodType.SEMESTER -> "Semester-based course"
    AcademicPeriodType.YEAR -> "Year-based course"
}

data class CourseBrowseModel(
    val id: String,
    val code: String,
    val name: String,
    val shortName: String,
    val structureType: AcademicPeriodType,
    val periodCount: Int,
)

data class AcademicPeriodBrowseModel(
    val id: String,
    val courseId: String,
    val periodNumber: Int,
    val periodType: AcademicPeriodType,
    val name: String,
    val shortName: String,
    val subjectCount: Int,
)

data class SubjectBrowseModel(
    val id: String,
    val academicPeriodId: String,
    val code: String,
    val name: String,
    val chapterCount: Int,
)

data class ChapterBrowseModel(
    val id: String,
    val subjectId: String,
    val chapterNumber: Int,
    val title: String,
    val questionCount: Int,
)

data class EligibleQuestionModel(
    val id: String,
    val chapterId: String,
    val topicId: String?,
    val question: String,
    val answer: String,
    val questionType: QuestionType,
    val maxScore: Double,
    val sortOrder: Int,
)

data class CoursePeriodsBrowseModel(
    val course: CourseBrowseModel,
    val periods: List<AcademicPeriodBrowseModel>,
)

data class PeriodSubjectsBrowseModel(
    val course: CourseBrowseModel,
    val period: AcademicPeriodBrowseModel,
    val subjects: List<SubjectBrowseModel>,
)

data class SubjectChaptersBrowseModel(
    val course: CourseBrowseModel,
    val period: AcademicPeriodBrowseModel,
    val subject: SubjectBrowseModel,
    val chapters: List<ChapterBrowseModel>,
)
