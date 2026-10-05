package com.studysmart.app.core.data.importer

import com.studysmart.app.core.database.*
import java.time.Instant

/** Caller must execute apply inside one Room transaction, including the version marker. */
internal class CurriculumImportEngine(private val dao: CurriculumImportDao) {
    suspend fun apply(p: CurriculumImportPayload, now: Instant): CurriculumImportResult {
        CurriculumImportValidator.validate(p)
        val previous = dao.state(p.manifest.datasetId)
        previous?.let {
            if (it.contentVersion > p.manifest.contentVersion) return CurriculumImportResult.NEWER_ALREADY_INSTALLED
            if (it.contentVersion == p.manifest.contentVersion) {
                require(it.bundleDigest == p.manifest.bundleDigest) { "Content version already exists with a different digest" }
                return CurriculumImportResult.ALREADY_CURRENT
            }
        }
        val courses = dao.courses().associateBy { it.id }
        val periods = dao.periods().associateBy { it.id }
        val subjects = dao.subjects().associateBy { it.id }
        val chapters = dao.chapters().associateBy { it.id }
        val topics = dao.topics().associateBy { it.id }
        val questions = dao.questions().associateBy { it.id }
        val existingTypes = mutableMapOf<String, String>()
        fun record(ids: Set<String>, type: String) {
            ids.forEach { id -> require(existingTypes.put(id, type) == null) { "Existing cross-type identity collision: $id" } }
        }
        record(courses.keys, "course"); record(periods.keys, "period"); record(subjects.keys, "subject")
        record(chapters.keys, "chapter"); record(topics.keys, "topic"); record(questions.keys, "question")
        fun identity(id: String, type: String) {
            require(existingTypes[id] == null || existingTypes[id] == type) { "Cross-type identity collision: $id" }
        }
        p.courses.forEach { r ->
            identity(r.id, "course")
            courses[r.id]?.let { require(it.code == r.code && it.structureType == r.structureType) { "Course identity changed: ${r.id}" } }
        }
        p.periods.forEach { r ->
            identity(r.id, "period")
            periods[r.id]?.let { require(it.courseId == r.courseId && it.periodNumber == r.periodNumber && it.periodType == r.periodType) { "Period identity changed: ${r.id}" } }
            require(periods.values.none { it.id != r.id && it.courseId == r.courseId && it.periodNumber == r.periodNumber }) { "Period natural-key collision: ${r.id}" }
        }
        p.subjects.forEach { r ->
            identity(r.id, "subject")
            subjects[r.id]?.let { require(it.academicPeriodId == r.academicPeriodId && it.code == r.code) { "Subject identity changed: ${r.id}" } }
            require(subjects.values.none { it.id != r.id && it.academicPeriodId == r.academicPeriodId && it.code == r.code }) { "Subject natural-key collision: ${r.id}" }
        }
        p.chapters.forEach { r ->
            identity(r.id, "chapter")
            chapters[r.id]?.let { require(it.subjectId == r.subjectId && it.chapterNumber == r.chapterNumber) { "Chapter identity changed: ${r.id}" } }
            require(chapters.values.none { it.id != r.id && it.subjectId == r.subjectId && it.chapterNumber == r.chapterNumber }) { "Chapter natural-key collision: ${r.id}" }
        }
        p.topics.forEach { r ->
            identity(r.id, "topic")
            topics[r.id]?.let { require(it.chapterId == r.chapterId) { "Topic identity changed: ${r.id}" } }
            require(topics.values.none { it.id != r.id && it.chapterId == r.chapterId && it.title == r.title }) { "Topic natural-key collision: ${r.id}" }
        }
        p.questions.forEach { r ->
            identity(r.id, "question")
            questions[r.id]?.let { require(it.chapterId == r.chapterId && it.questionType == r.questionType) { "Question identity changed: ${r.id}" } }
        }
        // Omitted rows survive an update, so their ancestry must also remain valid.
        CurriculumImportValidator.validate(p.copy(
            courses = (courses + p.courses.associateBy { it.id }).values.toList(),
            periods = (periods + p.periods.associateBy { it.id }).values.toList(),
            subjects = (subjects + p.subjects.associateBy { it.id }).values.toList(),
            chapters = (chapters + p.chapters.associateBy { it.id }).values.toList(),
            topics = (topics + p.topics.associateBy { it.id }).values.toList(),
            questions = (questions + p.questions.associateBy { it.id }).values.toList(),
        ))
        p.courses.forEach { r -> courses[r.id]?.let { old -> dao.updateCourse(r.copy(createdAt = old.createdAt, updatedAt = maxOf(r.updatedAt, old.updatedAt))) } ?: dao.insertCourse(r) }
        p.periods.forEach { r -> periods[r.id]?.let { old -> dao.updatePeriod(r.copy(createdAt = old.createdAt, updatedAt = maxOf(r.updatedAt, old.updatedAt))) } ?: dao.insertPeriod(r) }
        p.subjects.forEach { r -> subjects[r.id]?.let { old -> dao.updateSubject(r.copy(createdAt = old.createdAt, updatedAt = maxOf(r.updatedAt, old.updatedAt))) } ?: dao.insertSubject(r) }
        p.chapters.forEach { r -> chapters[r.id]?.let { old -> dao.updateChapter(r.copy(createdAt = old.createdAt, updatedAt = maxOf(r.updatedAt, old.updatedAt))) } ?: dao.insertChapter(r) }
        p.topics.forEach { r -> topics[r.id]?.let { old -> dao.updateTopic(r.copy(createdAt = old.createdAt, updatedAt = maxOf(r.updatedAt, old.updatedAt))) } ?: dao.insertTopic(r) }
        p.questions.forEach { r -> questions[r.id]?.let { old -> dao.updateQuestion(r.copy(createdAt = old.createdAt, updatedAt = maxOf(r.updatedAt, old.updatedAt))) } ?: dao.insertQuestion(r) }
        p.questions.forEach { dao.deleteKeywords(it.id) }
        if (p.keywords.isNotEmpty()) dao.insertKeywords(p.keywords)
        val state = CurriculumImportStateEntity(p.manifest.datasetId, p.manifest.contentVersion, p.manifest.bundleDigest, now)
        if (previous == null) dao.insertState(state) else dao.updateState(state)
        return CurriculumImportResult.IMPORTED
    }
}
