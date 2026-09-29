package com.studysmart.app.core.data

import com.studysmart.app.core.database.AcademicPeriodBrowseRow
import com.studysmart.app.core.database.AcademicPeriodContextRow
import com.studysmart.app.core.database.ChapterBrowseRow
import com.studysmart.app.core.database.CourseBrowseRow
import com.studysmart.app.core.database.CourseEntity
import com.studysmart.app.core.database.CurriculumDao
import com.studysmart.app.core.database.AcademicPeriodEntity
import com.studysmart.app.core.database.SubjectBrowseRow
import com.studysmart.app.core.database.SubjectContextRow
import com.studysmart.app.core.database.SubjectEntity
import com.studysmart.app.core.database.ChapterEntity
import com.studysmart.app.core.database.QuestionEntity
import com.studysmart.app.core.model.AcademicPeriodType
import com.studysmart.app.core.model.QuestionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class CurriculumRepositoryTest {
    @Test
    fun browseRowsBecomeOrderedContextualModels() = runTest {
        val repository = RoomCurriculumRepository(FakeCurriculumDao())

        val courses = repository.observeCourses().first()
        assertEquals(listOf("course-bca"), courses.map { it.id })
        assertEquals(8, courses.single().periodCount)

        val subjects = repository.observePeriodSubjects("period-1").first()
        assertNotNull(subjects)
        assertEquals("course-bca", subjects!!.course.id)
        assertEquals("Semester 1", subjects.period.name)
        assertEquals(1, subjects.subjects.single().chapterCount)

        val chapters = repository.observeSubjectChapters("subject-1").first()
        assertNotNull(chapters)
        assertEquals("course-bca", chapters!!.course.id)
        assertEquals("BCA", chapters.course.code)
        assertEquals(2, chapters.chapters.single().questionCount)

        val questions = repository.getEligibleQuestionsForChapter("chapter-1")
        assertEquals(listOf("question-direct", "question-topic"), questions.map { it.id })
        assertEquals("chapter-1", questions.first().chapterId)
        assertEquals(null, questions.first().topicId)
        assertEquals(listOf("question-topic"), repository.getEligibleQuestionsForTopic("topic-1").map { it.id })
    }
}

private class FakeCurriculumDao : CurriculumDao {
    private val time = Instant.EPOCH
    private val course = CourseEntity(
        id = "course-bca",
        code = "BCA",
        name = "Bachelor of Computer Applications",
        shortName = "BCA",
        structureType = AcademicPeriodType.SEMESTER,
        summary = "",
        isActive = true,
        sortOrder = 1,
        createdAt = time,
        updatedAt = time,
    )
    private val period = AcademicPeriodEntity(
        id = "period-1",
        courseId = course.id,
        periodNumber = 1,
        periodType = AcademicPeriodType.SEMESTER,
        name = "Semester 1",
        shortName = "Sem 1",
        sortOrder = 1,
        isActive = true,
        createdAt = time,
        updatedAt = time,
    )
    private val subject = SubjectEntity(
        id = "subject-1",
        academicPeriodId = period.id,
        code = "DBMS",
        name = "Database Management System",
        description = "",
        sortOrder = 1,
        isActive = true,
        createdAt = time,
        updatedAt = time,
    )
    private val chapter = ChapterEntity(
        id = "chapter-1",
        subjectId = subject.id,
        chapterNumber = 1,
        title = "Introduction",
        description = "",
        sortOrder = 1,
        isActive = true,
        createdAt = time,
        updatedAt = time,
    )
    private val questions = listOf(
        QuestionEntity(
            id = "question-direct",
            chapterId = chapter.id,
            topicId = null,
            question = "What is a database?",
            answer = "An organised collection of data.",
            questionType = QuestionType.WRITTEN,
            difficulty = "normal",
            maxScore = 2.0,
            explanation = "",
            isActive = true,
            sortOrder = 1,
            createdAt = time,
            updatedAt = time,
        ),
        QuestionEntity(
            id = "question-topic",
            chapterId = chapter.id,
            topicId = "topic-1",
            question = "What is a DBMS?",
            answer = "A database management system.",
            questionType = QuestionType.WRITTEN,
            difficulty = "normal",
            maxScore = 2.0,
            explanation = "",
            isActive = true,
            sortOrder = 2,
            createdAt = time,
            updatedAt = time,
        ),
    )

    override fun observeActiveCourses(): Flow<List<CourseBrowseRow>> = flowOf(
        listOf(CourseBrowseRow(course, 8)),
    )

    override fun observeActiveCourse(courseId: String): Flow<CourseEntity?> = flowOf(
        course.takeIf { it.id == courseId },
    )

    override fun observeActivePeriods(courseId: String): Flow<List<AcademicPeriodBrowseRow>> = flowOf(
        listOf(AcademicPeriodBrowseRow(period, 3)).takeIf { course.id == courseId } ?: emptyList(),
    )

    override fun observeActiveAcademicPeriod(periodId: String): Flow<AcademicPeriodContextRow?> = flowOf(
        if (period.id != periodId) {
            null
        } else {
            AcademicPeriodContextRow(
                period = period,
                courseCode = course.code,
                courseName = course.name,
                courseShortName = course.shortName,
                courseStructureType = course.structureType,
                coursePeriodCount = 8,
                subjectCount = 1,
            )
        },
    )

    override fun observeActiveSubjects(academicPeriodId: String): Flow<List<SubjectBrowseRow>> = flowOf(
        listOf(SubjectBrowseRow(subject, 1)).takeIf { period.id == academicPeriodId } ?: emptyList(),
    )

    override fun observeActiveSubject(subjectId: String): Flow<SubjectContextRow?> = flowOf(
        if (subject.id != subjectId) {
            null
        } else {
            SubjectContextRow(
                subject = subject,
                courseId = course.id,
                periodName = period.name,
                periodShortName = period.shortName,
                periodNumber = period.periodNumber,
                periodType = period.periodType,
                courseCode = course.code,
                courseName = course.name,
                courseShortName = course.shortName,
                courseStructureType = course.structureType,
                coursePeriodCount = 8,
                subjectCount = 1,
                chapterCount = 1,
            )
        },
    )

    override fun observeActiveChapters(subjectId: String): Flow<List<ChapterBrowseRow>> = flowOf(
        listOf(ChapterBrowseRow(chapter, 2)).takeIf { subject.id == subjectId } ?: emptyList(),
    )

    override suspend fun getEligibleQuestionsForChapter(chapterId: String): List<QuestionEntity> =
        questions.filter { it.chapterId == chapterId }

    override suspend fun getEligibleQuestionsForTopic(topicId: String): List<QuestionEntity> =
        questions.filter { it.topicId == topicId }

    override suspend fun isActivePeriodForCourse(courseId: String, periodId: String): Boolean =
        course.id == courseId && period.id == periodId
}
