package com.studysmart.app.core.data.importer

import com.studysmart.app.core.model.KeywordNormalizer
import java.time.Instant

object CurriculumImportValidator {
    fun validate(p: CurriculumImportPayload) {
        fun unique(values: List<Any>, label: String) {
            require(values.size == values.toSet().size) { "Duplicate $label" }
        }
        val allIds = p.courses.map { it.id } + p.periods.map { it.id } + p.subjects.map { it.id } +
            p.chapters.map { it.id } + p.topics.map { it.id } + p.questions.map { it.id }
        require(allIds.all { it.matches(Regex("[A-Za-z0-9_-]+")) }) { "Invalid entity ID" }
        unique(allIds, "entity ID")
        val courses = p.courses.associateBy { it.id }
        val periods = p.periods.associateBy { it.id }
        val subjects = p.subjects.associateBy { it.id }
        val chapters = p.chapters.associateBy { it.id }
        val topics = p.topics.associateBy { it.id }
        val questions = p.questions.associateBy { it.id }
        unique(p.periods.map { it.courseId to it.periodNumber }, "period number")
        unique(p.subjects.map { it.academicPeriodId to it.code }, "subject code")
        unique(p.chapters.map { it.subjectId to it.chapterNumber }, "chapter number")
        unique(p.topics.map { it.chapterId to it.title }, "topic title")
        fun row(id: String, order: Int, created: Instant, updated: Instant, title: String) {
            require(order > 0 && title.isNotBlank() && !created.isBefore(Instant.EPOCH) && updated >= created) { "Invalid content fields: $id" }
        }
        p.courses.forEach { row(it.id, it.sortOrder, it.createdAt, it.updatedAt, it.name); require(it.code.isNotBlank()) }
        p.periods.forEach {
            val parent = requireNotNull(courses[it.courseId]) { "Missing course: ${it.id}" }
            require(it.periodNumber > 0 && it.periodType == parent.structureType && (!it.isActive || parent.isActive)) { "Invalid period ancestry: ${it.id}" }
            row(it.id, it.sortOrder, it.createdAt, it.updatedAt, it.name)
        }
        p.subjects.forEach {
            val parent = requireNotNull(periods[it.academicPeriodId]) { "Missing period: ${it.id}" }
            require(it.code.isNotBlank() && (!it.isActive || parent.isActive)) { "Invalid subject: ${it.id}" }
            row(it.id, it.sortOrder, it.createdAt, it.updatedAt, it.name)
        }
        p.chapters.forEach {
            val parent = requireNotNull(subjects[it.subjectId]) { "Missing subject: ${it.id}" }
            require(it.chapterNumber > 0 && (!it.isActive || parent.isActive)) { "Invalid chapter: ${it.id}" }
            row(it.id, it.sortOrder, it.createdAt, it.updatedAt, it.title)
        }
        p.topics.forEach {
            val parent = requireNotNull(chapters[it.chapterId]) { "Missing chapter: ${it.id}" }
            require(!it.isActive || parent.isActive) { "Invalid topic ancestry: ${it.id}" }
            row(it.id, it.sortOrder, it.createdAt, it.updatedAt, it.title)
        }
        p.questions.forEach {
            val chapter = requireNotNull(chapters[it.chapterId]) { "Missing chapter: ${it.id}" }
            require(!it.isActive || chapter.isActive) { "Invalid question ancestry: ${it.id}" }
            it.topicId?.let { id ->
                val topic = requireNotNull(topics[id]) { "Missing topic: ${it.id}" }
                require(topic.chapterId == it.chapterId && (!it.isActive || topic.isActive)) { "Invalid question topic: ${it.id}" }
            }
            require(it.answer.isNotBlank() && it.difficulty == "normal" && it.maxScore.isFinite() && it.maxScore > 0) { "Invalid answer/score: ${it.id}" }
            row(it.id, it.sortOrder, it.createdAt, it.updatedAt, it.question)
        }
        unique(p.keywords.map { it.questionId to it.normalizedKeyword }, "keyword key")
        unique(p.keywords.map { it.questionId to it.sortOrder }, "keyword order")
        p.keywords.forEach {
            require(it.questionId in questions && it.normalizedKeyword.isNotBlank() &&
                it.normalizedKeyword == KeywordNormalizer.normalize(it.normalizedKeyword) &&
                it.weight.isFinite() && it.weight > 0 && it.sortOrder > 0) { "Invalid keyword: ${it.questionId}" }
        }
    }
}
