package com.studysmart.app.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.studysmart.app.core.model.KeywordNormalizer

object DatabaseSeed {
    fun callback(): RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            seedReleaseCurriculum(db)
            seedReleaseTestContent(db)
            // Variant-specific history and demo data stays outside the release path.
            applyVariantSeed(db)
        }
    }

    internal fun seedReleaseCurriculum(db: SupportSQLiteDatabase) {
        val timestamp = 0L
        db.execSQL(
            "INSERT OR IGNORE INTO users(id, displayName, createdAt, updatedAt) VALUES (?, ?, ?, ?)",
            arrayOf<Any?>("local-user", "Student", timestamp, timestamp),
        )
        insertCourse(
            db,
            id = "course-bca",
            code = "BCA",
            name = "Bachelor of Computer Applications",
            shortName = "BCA",
            structureType = "semester",
            sortOrder = 1,
            timestamp = timestamp,
        )
        insertCourse(
            db,
            id = "course-yearly-example",
            code = "YEARLY",
            name = "Example Yearly Programme",
            shortName = "Yearly",
            structureType = "year",
            sortOrder = 2,
            timestamp = timestamp,
        )

        (1..8).forEach { number ->
            insertPeriod(
                db,
                id = "period-bca-semester-$number",
                courseId = "course-bca",
                number = number,
                type = "semester",
                name = "Semester $number",
                shortName = "Sem $number",
                sortOrder = number,
                timestamp = timestamp,
            )
        }
        (1..3).forEach { number ->
            insertPeriod(
                db,
                id = "period-example-year-$number",
                courseId = "course-yearly-example",
                number = number,
                type = "year",
                name = "Year $number",
                shortName = "Year $number",
                sortOrder = number,
                timestamp = timestamp,
            )
        }

        seedSemesterOne(db, timestamp)
        seedSemesterFive(db, timestamp)
        seedYearOne(db, timestamp)
    }

    internal fun seedReleaseTestContent(db: SupportSQLiteDatabase) {
        insertKeyword(db, "question-bca-s1-dbms-direct", "database", 1.0, 1)
        insertKeyword(db, "question-bca-s1-dbms-direct", "organised collection", 1.0, 2)
        insertKeyword(db, "question-bca-s1-dbms-topic", "dbms", 1.0, 1)
        insertKeyword(db, "question-bca-s1-dbms-topic", "storage", 1.0, 2)
        insertKeyword(db, "question-bca-s1-dbms-topic", "access", 1.0, 3)
        insertKeyword(db, "question-bca-s1-web-01", "http", 1.0, 1)
        insertKeyword(db, "question-bca-s1-web-01", "application protocol", 1.0, 2)
        insertKeyword(db, "question-bca-s5-ai-01", "agent", 1.0, 1)
        insertKeyword(db, "question-bca-s5-ai-01", "environment", 1.0, 2)
        insertKeyword(db, "question-bca-s5-net-01", "network protocol", 1.0, 1)
        insertKeyword(db, "question-year1-foundations-01", "algorithm", 1.0, 1)
        insertKeyword(db, "question-year1-foundations-01", "steps", 1.0, 2)
    }

    private fun seedSemesterOne(db: SupportSQLiteDatabase, timestamp: Long) {
        insertSubject(db, "subject-bca-s1-dbms", "period-bca-semester-1", "DBMS", "Database Management System", 1, timestamp)
        insertSubject(db, "subject-bca-s1-web", "period-bca-semester-1", "WEB", "Web Technology", 2, timestamp)
        insertChapter(db, "chapter-bca-s1-dbms-01", "subject-bca-s1-dbms", 1, "Introduction to DBMS", 1, timestamp)
        insertChapter(db, "chapter-bca-s1-dbms-02", "subject-bca-s1-dbms", 2, "Relational Model", 2, timestamp)
        insertChapter(db, "chapter-bca-s1-web-01", "subject-bca-s1-web", 1, "Web Foundations", 1, timestamp)
        insertChapter(db, "chapter-bca-s1-web-02", "subject-bca-s1-web", 2, "Web Protocols", 2, timestamp)
        insertTopic(db, "topic-bca-s1-dbms-01", "chapter-bca-s1-dbms-01", "Core Concepts", 1, timestamp)
        insertQuestion(db, "question-bca-s1-dbms-direct", "chapter-bca-s1-dbms-01", null, "What is a database?", "A database is an organised collection of data.", 1, timestamp)
        insertQuestion(db, "question-bca-s1-dbms-topic", "chapter-bca-s1-dbms-01", "topic-bca-s1-dbms-01", "What is a DBMS?", "A DBMS manages storage, access, and manipulation of data.", 2, timestamp)
        insertQuestion(db, "question-bca-s1-web-01", "chapter-bca-s1-web-01", null, "What does HTTP provide?", "HTTP provides an application protocol for web communication.", 1, timestamp)
    }

    private fun seedSemesterFive(db: SupportSQLiteDatabase, timestamp: Long) {
        insertSubject(db, "subject-bca-s5-ai", "period-bca-semester-5", "AI", "Artificial Intelligence", 1, timestamp)
        insertSubject(db, "subject-bca-s5-net", "period-bca-semester-5", "NET", "Computer Networks", 2, timestamp)
        insertChapter(db, "chapter-bca-s5-ai-01", "subject-bca-s5-ai", 1, "Intelligent Agents", 1, timestamp)
        insertChapter(db, "chapter-bca-s5-ai-02", "subject-bca-s5-ai", 2, "Search Strategies", 2, timestamp)
        insertChapter(db, "chapter-bca-s5-net-01", "subject-bca-s5-net", 1, "Network Models", 1, timestamp)
        insertChapter(db, "chapter-bca-s5-net-02", "subject-bca-s5-net", 2, "Transport Services", 2, timestamp)
        insertQuestion(db, "question-bca-s5-ai-01", "chapter-bca-s5-ai-01", null, "What is an intelligent agent?", "It is a system that perceives its environment and acts on it.", 1, timestamp)
        insertQuestion(db, "question-bca-s5-net-01", "chapter-bca-s5-net-01", null, "What is a network protocol?", "It is a defined set of rules for communication.", 1, timestamp)
    }

    private fun seedYearOne(db: SupportSQLiteDatabase, timestamp: Long) {
        insertSubject(db, "subject-year1-foundations", "period-example-year-1", "FOUND", "Computing Foundations", 1, timestamp)
        insertChapter(db, "chapter-year1-foundations-01", "subject-year1-foundations", 1, "Computing Basics", 1, timestamp)
        insertChapter(db, "chapter-year1-foundations-02", "subject-year1-foundations", 2, "Problem Solving", 2, timestamp)
        insertQuestion(db, "question-year1-foundations-01", "chapter-year1-foundations-01", null, "What is an algorithm?", "An algorithm is a finite sequence of steps for solving a problem.", 1, timestamp)
    }

    private fun insertCourse(
        db: SupportSQLiteDatabase,
        id: String,
        code: String,
        name: String,
        shortName: String,
        structureType: String,
        sortOrder: Int,
        timestamp: Long,
    ) {
        db.execSQL(
            "INSERT OR IGNORE INTO courses(id, code, name, shortName, structureType, summary, isActive, sortOrder, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?)",
            arrayOf<Any?>(id, code, name, shortName, structureType, "", sortOrder, timestamp, timestamp),
        )
    }

    private fun insertPeriod(
        db: SupportSQLiteDatabase,
        id: String,
        courseId: String,
        number: Int,
        type: String,
        name: String,
        shortName: String,
        sortOrder: Int,
        timestamp: Long,
    ) {
        db.execSQL(
            "INSERT OR IGNORE INTO academic_periods(id, courseId, periodNumber, periodType, name, shortName, sortOrder, isActive, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)",
            arrayOf<Any?>(id, courseId, number, type, name, shortName, sortOrder, timestamp, timestamp),
        )
    }

    private fun insertSubject(
        db: SupportSQLiteDatabase,
        id: String,
        periodId: String,
        code: String,
        name: String,
        sortOrder: Int,
        timestamp: Long,
    ) {
        db.execSQL(
            "INSERT OR IGNORE INTO subjects(id, academicPeriodId, code, name, description, sortOrder, isActive, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)",
            arrayOf<Any?>(id, periodId, code, name, "", sortOrder, timestamp, timestamp),
        )
    }

    private fun insertChapter(
        db: SupportSQLiteDatabase,
        id: String,
        subjectId: String,
        number: Int,
        title: String,
        sortOrder: Int,
        timestamp: Long,
    ) {
        db.execSQL(
            "INSERT OR IGNORE INTO chapters(id, subjectId, chapterNumber, title, description, sortOrder, isActive, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)",
            arrayOf<Any?>(id, subjectId, number, title, "", sortOrder, timestamp, timestamp),
        )
    }

    private fun insertTopic(
        db: SupportSQLiteDatabase,
        id: String,
        chapterId: String,
        title: String,
        sortOrder: Int,
        timestamp: Long,
    ) {
        db.execSQL(
            "INSERT OR IGNORE INTO topics(id, chapterId, title, description, sortOrder, isActive, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, 1, ?, ?)",
            arrayOf<Any?>(id, chapterId, title, "", sortOrder, timestamp, timestamp),
        )
    }

    private fun insertQuestion(
        db: SupportSQLiteDatabase,
        id: String,
        chapterId: String,
        topicId: String?,
        question: String,
        answer: String,
        sortOrder: Int,
        timestamp: Long,
    ) {
        db.execSQL(
            "INSERT OR IGNORE INTO questions(id, chapterId, topicId, question, answer, questionType, difficulty, maxScore, explanation, isActive, sortOrder, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, 'written', 'normal', 2.0, ?, 1, ?, ?, ?)",
            arrayOf<Any?>(id, chapterId, topicId, question, answer, "", sortOrder, timestamp, timestamp),
        )
    }

    private fun insertKeyword(
        db: SupportSQLiteDatabase,
        questionId: String,
        keyword: String,
        weight: Double,
        sortOrder: Int,
    ) {
        db.execSQL(
            "INSERT OR IGNORE INTO question_keywords(questionId, normalizedKeyword, weight, sortOrder) VALUES (?, ?, ?, ?)",
            arrayOf<Any?>(questionId, KeywordNormalizer.normalize(keyword), weight, sortOrder),
        )
    }
}
