package com.studysmart.app.feature.important

import com.studysmart.app.app.AppContainer

/** Competing saved questions make omitted candidate filters fail visibly. */
internal suspend fun seedSavedCompetitors(graph: AppContainer) {
    val template = graph.database.preparationDao().getChapterCandidates("local-user", "chapter-bca-s1-dbms-01").first().question
    graph.database.curriculumImportDao().insertQuestion(template.copy(id = "question-bca-s1-dbms-other", chapterId = "chapter-bca-s1-dbms-02", topicId = null))
    listOf("question-bca-s1-dbms-direct", "question-bca-s1-dbms-other", "question-bca-s1-web-01", "question-bca-s5-ai-01").forEach {
        graph.bookmarkRepository.setBookmarked(it, true)
    }
}
