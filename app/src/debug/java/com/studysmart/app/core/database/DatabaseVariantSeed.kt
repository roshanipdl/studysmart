package com.studysmart.app.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import com.studysmart.app.core.model.KeywordNormalizer

internal fun applyVariantSeed(db: SupportSQLiteDatabase) {
    val chapters = DebugSampleCurriculum.chapters
    db.beginTransaction()
    try {
        chapters.forEach { chapter ->
            db.execSQL(
                "INSERT OR IGNORE INTO chapters(id, subjectId, chapterNumber, title, description, sortOrder, isActive, createdAt, updatedAt) VALUES (?, ?, 3, ?, ?, 3, 1, 0, 0)",
                arrayOf<Any?>(chapter.id, chapter.subjectId, chapter.title, "Debug-only sample practice; not official examination material."),
            )
            chapter.topics.forEachIndexed { index, topic ->
                db.execSQL(
                    "INSERT OR IGNORE INTO topics(id, chapterId, title, description, sortOrder, isActive, createdAt, updatedAt) VALUES (?, ?, ?, '', ?, 1, 0, 0)",
                    arrayOf<Any?>(topic.id, chapter.id, topic.title, index + 1),
                )
            }
            chapter.questions.forEachIndexed { index, question ->
                db.execSQL(
                    "INSERT OR IGNORE INTO questions(id, chapterId, topicId, question, answer, questionType, difficulty, maxScore, explanation, isActive, sortOrder, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, 'written', 'normal', 2.0, '', 1, ?, 0, 0)",
                    arrayOf<Any?>(question.id, chapter.id, question.topicId, question.prompt, question.answer, index + 1),
                )
                question.keywords.forEachIndexed { keywordIndex, keyword ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO question_keywords(questionId, normalizedKeyword, weight, sortOrder) VALUES (?, ?, ?, ?)",
                        arrayOf<Any?>(question.id, KeywordNormalizer.normalize(keyword.text), keyword.weight, keywordIndex + 1),
                    )
                }
            }
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}
