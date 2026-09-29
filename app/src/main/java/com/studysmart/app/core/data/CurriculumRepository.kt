package com.studysmart.app.core.data

import com.studysmart.app.core.database.AcademicPeriodBrowseRow
import com.studysmart.app.core.database.AcademicPeriodContextRow
import com.studysmart.app.core.database.ChapterBrowseRow
import com.studysmart.app.core.database.CourseBrowseRow
import com.studysmart.app.core.database.CurriculumDao
import com.studysmart.app.core.database.QuestionEntity
import com.studysmart.app.core.database.SubjectBrowseRow
import com.studysmart.app.core.database.SubjectContextRow
import com.studysmart.app.core.model.AcademicPeriodBrowseModel
import com.studysmart.app.core.model.ChapterBrowseModel
import com.studysmart.app.core.model.CourseBrowseModel
import com.studysmart.app.core.model.CoursePeriodsBrowseModel
import com.studysmart.app.core.model.EligibleQuestionModel
import com.studysmart.app.core.model.PeriodSubjectsBrowseModel
import com.studysmart.app.core.model.SubjectBrowseModel
import com.studysmart.app.core.model.SubjectChaptersBrowseModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface CurriculumRepository {
    fun observeCourses(): Flow<List<CourseBrowseModel>>

    fun observeCoursePeriods(courseId: String): Flow<CoursePeriodsBrowseModel?>

    fun observePeriodSubjects(academicPeriodId: String): Flow<PeriodSubjectsBrowseModel?>

    fun observeSubjectChapters(subjectId: String): Flow<SubjectChaptersBrowseModel?>

    suspend fun getEligibleQuestionsForChapter(chapterId: String): List<EligibleQuestionModel>

    suspend fun getEligibleQuestionsForTopic(topicId: String): List<EligibleQuestionModel>

    suspend fun isValidPeriod(courseId: String, academicPeriodId: String): Boolean
}

class RoomCurriculumRepository(
    private val dao: CurriculumDao,
) : CurriculumRepository {
    override fun observeCourses(): Flow<List<CourseBrowseModel>> = dao.observeActiveCourses()
        .map { rows -> rows.map(CourseBrowseRow::toModel) }

    override fun observeCoursePeriods(courseId: String): Flow<CoursePeriodsBrowseModel?> = combine(
        dao.observeActiveCourse(courseId),
        dao.observeActivePeriods(courseId),
    ) { course, periods ->
        course?.let {
            CoursePeriodsBrowseModel(
                course = it.toModel(periods.size),
                periods = periods.map(AcademicPeriodBrowseRow::toModel),
            )
        }
    }

    override fun observePeriodSubjects(academicPeriodId: String): Flow<PeriodSubjectsBrowseModel?> = combine(
        dao.observeActiveAcademicPeriod(academicPeriodId),
        dao.observeActiveSubjects(academicPeriodId),
    ) { period, subjects ->
        period?.toModel(subjects.map(SubjectBrowseRow::toModel))
    }

    override fun observeSubjectChapters(subjectId: String): Flow<SubjectChaptersBrowseModel?> = combine(
        dao.observeActiveSubject(subjectId),
        dao.observeActiveChapters(subjectId),
    ) { subject, chapters ->
        subject?.toModel(chapters.map(ChapterBrowseRow::toModel))
    }

    override suspend fun getEligibleQuestionsForChapter(chapterId: String): List<EligibleQuestionModel> =
        dao.getEligibleQuestionsForChapter(chapterId).map(QuestionEntity::toEligibleModel)

    override suspend fun getEligibleQuestionsForTopic(topicId: String): List<EligibleQuestionModel> =
        dao.getEligibleQuestionsForTopic(topicId).map(QuestionEntity::toEligibleModel)

    override suspend fun isValidPeriod(courseId: String, academicPeriodId: String): Boolean =
        dao.isActivePeriodForCourse(courseId, academicPeriodId)
}

private fun CourseBrowseRow.toModel(): CourseBrowseModel = course.toModel(periodCount)

private fun com.studysmart.app.core.database.CourseEntity.toModel(periodCount: Int): CourseBrowseModel =
    CourseBrowseModel(id, code, name, shortName, structureType, periodCount)

private fun AcademicPeriodBrowseRow.toModel(): AcademicPeriodBrowseModel = period.toModel(subjectCount)

private fun com.studysmart.app.core.database.AcademicPeriodEntity.toModel(subjectCount: Int): AcademicPeriodBrowseModel =
    AcademicPeriodBrowseModel(id, courseId, periodNumber, periodType, name, shortName, subjectCount)

private fun SubjectBrowseRow.toModel(): SubjectBrowseModel =
    SubjectBrowseModel(subject.id, subject.academicPeriodId, subject.code, subject.name, chapterCount)

private fun ChapterBrowseRow.toModel(): ChapterBrowseModel =
    ChapterBrowseModel(chapter.id, chapter.subjectId, chapter.chapterNumber, chapter.title, questionCount)

private fun QuestionEntity.toEligibleModel(): EligibleQuestionModel =
    EligibleQuestionModel(id, chapterId, topicId, question, answer, questionType, maxScore, sortOrder)

private fun AcademicPeriodContextRow.toModel(subjects: List<SubjectBrowseModel>): PeriodSubjectsBrowseModel {
    val course = CourseBrowseModel(
        id = period.courseId,
        code = courseCode,
        name = courseName,
        shortName = courseShortName,
        structureType = courseStructureType,
        periodCount = coursePeriodCount,
    )
    return PeriodSubjectsBrowseModel(
        course = course,
        period = period.toModel(subjectCount),
        subjects = subjects,
    )
}

private fun SubjectContextRow.toModel(chapters: List<ChapterBrowseModel>): SubjectChaptersBrowseModel {
    val course = CourseBrowseModel(
        id = courseId,
        code = courseCode,
        name = courseName,
        shortName = courseShortName,
        structureType = courseStructureType,
        periodCount = coursePeriodCount,
    )
    val period = AcademicPeriodBrowseModel(
        id = subject.academicPeriodId,
        courseId = courseId,
        periodNumber = periodNumber,
        periodType = periodType,
        name = periodName,
        shortName = periodShortName,
        subjectCount = subjectCount,
    )
    val subjectModel = SubjectBrowseModel(
        id = subject.id,
        academicPeriodId = subject.academicPeriodId,
        code = subject.code,
        name = subject.name,
        chapterCount = chapterCount,
    )
    return SubjectChaptersBrowseModel(course, period, subjectModel, chapters)
}
