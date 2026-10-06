package com.studysmart.app.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/** Called only inside the import transaction, after the validated version-2 bundle applies. */
internal fun cleanupImportedVariant(db: SupportSQLiteDatabase, datasetId: String, version: Long) {
    if (datasetId != "bca-local-curriculum" || version != 2L) return
    val questions = "SELECT q.id FROM questions q JOIN chapters c ON c.id=q.chapterId " +
        "JOIN subjects s ON s.id=c.subjectId JOIN academic_periods p ON p.id=s.academicPeriodId " +
        "WHERE p.courseId='course-yearly-example'"
    fun reject(sql: String) {
        db.query(sql).use { cursor ->
            check(!cursor.moveToFirst()) { "Inconsistent cross-course history; refusing yearly cleanup" }
        }
    }
    // Validate both directions before CASCADE/SET_NULL can touch BCA evidence.
    for ((parent, children, parentKey) in listOf(
        Triple("preparation_sessions", "preparation_session_items", "sessionId"),
        Triple("test_attempts", "test_attempt_questions", "testAttemptId"),
    )) {
        reject("SELECT 1 FROM $parent p JOIN $children i ON i.$parentKey=p.id " +
            "WHERE (p.courseId='course-yearly-example' AND i.questionId NOT IN ($questions)) " +
            "OR (p.courseId!='course-yearly-example' AND i.questionId IN ($questions)) LIMIT 1")
        reject("SELECT 1 FROM $parent p JOIN academic_periods a ON a.id=p.academicPeriodId " +
            "WHERE (p.courseId='course-yearly-example' OR a.courseId='course-yearly-example') " +
            "AND p.courseId!=a.courseId LIMIT 1")
        reject("SELECT 1 FROM $parent p JOIN subjects s ON s.id=p.subjectId " +
            "JOIN academic_periods a ON a.id=s.academicPeriodId " +
            "WHERE (p.courseId='course-yearly-example' OR a.courseId='course-yearly-example') " +
            "AND p.courseId!=a.courseId LIMIT 1")
        reject("SELECT 1 FROM $parent p JOIN chapters c ON c.id=p.chapterId " +
            "JOIN subjects s ON s.id=c.subjectId JOIN academic_periods a ON a.id=s.academicPeriodId " +
            "WHERE (p.courseId='course-yearly-example' OR a.courseId='course-yearly-example') " +
            "AND p.courseId!=a.courseId LIMIT 1")
        reject("SELECT 1 FROM $parent p JOIN topics t ON t.id=p.topicId JOIN chapters c ON c.id=t.chapterId " +
            "JOIN subjects s ON s.id=c.subjectId JOIN academic_periods a ON a.id=s.academicPeriodId " +
            "WHERE (p.courseId='course-yearly-example' OR a.courseId='course-yearly-example') " +
            "AND p.courseId!=a.courseId LIMIT 1")
    }
    reject("SELECT 1 FROM review_records r JOIN preparation_sessions p ON p.id=r.preparationSessionId " +
        "WHERE (p.courseId='course-yearly-example' AND r.questionId NOT IN ($questions)) " +
        "OR (p.courseId!='course-yearly-example' AND r.questionId IN ($questions)) LIMIT 1")
    // Cross-course malformed learning references fail closed via restrictive FKs,
    // rolling back the entire import rather than deleting unrelated history.
    db.execSQL("DELETE FROM test_attempts WHERE courseId='course-yearly-example'")
    db.execSQL("DELETE FROM preparation_sessions WHERE courseId='course-yearly-example'")
    listOf("review_records", "review_schedules", "user_question_flags", "question_keywords").forEach { table ->
        db.execSQL("DELETE FROM $table WHERE questionId IN ($questions)")
    }
    db.execSQL("DELETE FROM courses WHERE id='course-yearly-example'")
    db.execSQL("DELETE FROM search_documents WHERE courseId='course-yearly-example'")
}
