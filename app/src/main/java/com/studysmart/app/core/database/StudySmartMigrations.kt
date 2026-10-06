package com.studysmart.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE questions ADD COLUMN answerFormat TEXT NOT NULL DEFAULT 'plain_text'")
        db.execSQL("ALTER TABLE preparation_session_items ADD COLUMN answerFormatSnapshot TEXT NOT NULL DEFAULT 'plain_text'")
        db.execSQL("ALTER TABLE test_attempt_questions ADD COLUMN referenceAnswerFormatSnapshot TEXT NOT NULL DEFAULT 'plain_text'")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `search_documents` (`rowid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `entityId` TEXT NOT NULL, `courseId` TEXT NOT NULL, `academicPeriodId` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `chapterId` TEXT, `topicId` TEXT, `text` TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_search_documents_type_entityId` ON `search_documents` (`type`, `entityId`)")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `search_fts` USING FTS4(`text` TEXT NOT NULL, tokenize=unicode61, content=`search_documents`)")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_search_fts_BEFORE_UPDATE BEFORE UPDATE ON `search_documents` BEGIN DELETE FROM `search_fts` WHERE `docid`=OLD.`rowid`; END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_search_fts_BEFORE_DELETE BEFORE DELETE ON `search_documents` BEGIN DELETE FROM `search_fts` WHERE `docid`=OLD.`rowid`; END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_search_fts_AFTER_UPDATE AFTER UPDATE ON `search_documents` BEGIN INSERT INTO `search_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_search_fts_AFTER_INSERT AFTER INSERT ON `search_documents` BEGIN INSERT INTO `search_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END")
        SearchIndexSynchronizer.synchronize(db)
        // Triggers match Room's generated schema and index the backfill exactly once.
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS curriculum_import_state (datasetId TEXT NOT NULL, contentVersion INTEGER NOT NULL, bundleDigest TEXT NOT NULL, importedAt INTEGER NOT NULL, PRIMARY KEY(datasetId))")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS users (id TEXT NOT NULL, displayName TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS courses (id TEXT NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, shortName TEXT NOT NULL, structureType TEXT NOT NULL, summary TEXT NOT NULL, isActive INTEGER NOT NULL, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS academic_periods (id TEXT NOT NULL, courseId TEXT NOT NULL, periodNumber INTEGER NOT NULL, periodType TEXT NOT NULL, name TEXT NOT NULL, shortName TEXT NOT NULL, sortOrder INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(courseId) REFERENCES courses(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS subjects (id TEXT NOT NULL, academicPeriodId TEXT NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, sortOrder INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(academicPeriodId) REFERENCES academic_periods(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS chapters (id TEXT NOT NULL, subjectId TEXT NOT NULL, chapterNumber INTEGER NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, sortOrder INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(subjectId) REFERENCES subjects(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS topics (id TEXT NOT NULL, chapterId TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, sortOrder INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(chapterId) REFERENCES chapters(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS questions (id TEXT NOT NULL, chapterId TEXT NOT NULL, topicId TEXT, question TEXT NOT NULL, answer TEXT NOT NULL, questionType TEXT NOT NULL, difficulty TEXT NOT NULL, maxScore REAL NOT NULL, explanation TEXT NOT NULL, isActive INTEGER NOT NULL, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(chapterId) REFERENCES chapters(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(topicId) REFERENCES topics(id) ON UPDATE NO ACTION ON DELETE SET NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_courses_isActive_sortOrder ON courses(isActive, sortOrder)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_academic_periods_courseId_isActive_sortOrder ON academic_periods(courseId, isActive, sortOrder)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_academic_periods_courseId_periodNumber ON academic_periods(courseId, periodNumber)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_subjects_academicPeriodId_isActive_sortOrder ON subjects(academicPeriodId, isActive, sortOrder)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_subjects_academicPeriodId_code ON subjects(academicPeriodId, code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chapters_subjectId_isActive_sortOrder ON chapters(subjectId, isActive, sortOrder)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chapters_subjectId_chapterNumber ON chapters(subjectId, chapterNumber)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_topics_chapterId_isActive_sortOrder ON topics(chapterId, isActive, sortOrder)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_topics_chapterId_title ON topics(chapterId, title)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_questions_chapterId_isActive_sortOrder ON questions(chapterId, isActive, sortOrder)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_questions_topicId ON questions(topicId)")
        DatabaseSeed.seedReleaseCurriculum(db)
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS preparation_sessions (" +
                "id TEXT NOT NULL, userId TEXT NOT NULL, courseId TEXT NOT NULL, " +
                "academicPeriodId TEXT NOT NULL, subjectId TEXT, chapterId TEXT, topicId TEXT, " +
                "scopeType TEXT NOT NULL, scopeReferenceId TEXT, originType TEXT NOT NULL, " +
                "originReferenceId TEXT, creationRequestId TEXT NOT NULL, selectionSeed INTEGER, " +
                "status TEXT NOT NULL, startedAt INTEGER NOT NULL, completedAt INTEGER, " +
                "updatedAt INTEGER NOT NULL, uniqueQuestionCount INTEGER NOT NULL, " +
                "reviewCount INTEGER NOT NULL, PRIMARY KEY(id), " +
                "FOREIGN KEY(userId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(courseId) REFERENCES courses(id) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(academicPeriodId) REFERENCES academic_periods(id) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(subjectId) REFERENCES subjects(id) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(chapterId) REFERENCES chapters(id) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(topicId) REFERENCES topics(id) ON UPDATE NO ACTION ON DELETE NO ACTION" +
                ")",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS preparation_session_items (" +
                "id TEXT NOT NULL, sessionId TEXT NOT NULL, questionId TEXT NOT NULL, " +
                "questionSnapshot TEXT NOT NULL, answerSnapshot TEXT NOT NULL, " +
                "questionTypeSnapshot TEXT NOT NULL, originalOrder INTEGER NOT NULL, " +
                "queueSequence INTEGER NOT NULL, presentationCount INTEGER NOT NULL, " +
                "hardRequeueUsed INTEGER NOT NULL, isRevealed INTEGER NOT NULL, " +
                "isCompleted INTEGER NOT NULL, lastRating TEXT, lastPresentedAt INTEGER, " +
                "PRIMARY KEY(id), " +
                "FOREIGN KEY(sessionId) REFERENCES preparation_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(questionId) REFERENCES questions(id) ON UPDATE NO ACTION ON DELETE NO ACTION" +
                ")",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS review_records (" +
                "id TEXT NOT NULL, userId TEXT NOT NULL, preparationSessionId TEXT, " +
                "questionId TEXT NOT NULL, rating TEXT NOT NULL, responseTimeMs INTEGER, " +
                "reviewedAt INTEGER NOT NULL, PRIMARY KEY(id), " +
                "FOREIGN KEY(userId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(preparationSessionId) REFERENCES preparation_sessions(id) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                "FOREIGN KEY(questionId) REFERENCES questions(id) ON UPDATE NO ACTION ON DELETE NO ACTION" +
                ")",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS review_schedules (" +
                "userId TEXT NOT NULL, questionId TEXT NOT NULL, learningState TEXT NOT NULL, " +
                "lastReviewedAt INTEGER, nextReviewAt INTEGER, intervalSeconds INTEGER NOT NULL, " +
                "repetitionCount INTEGER NOT NULL, lapseCount INTEGER NOT NULL, " +
                "PRIMARY KEY(userId, questionId), " +
                "FOREIGN KEY(userId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(questionId) REFERENCES questions(id) ON UPDATE NO ACTION ON DELETE NO ACTION" +
                ")",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_preparation_sessions_creationRequestId " +
                "ON preparation_sessions(creationRequestId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_preparation_sessions_userId_status " +
                "ON preparation_sessions(userId, status)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_preparation_sessions_courseId_status " +
                "ON preparation_sessions(courseId, status)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_preparation_sessions_academicPeriodId_status " +
                "ON preparation_sessions(academicPeriodId, status)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_preparation_sessions_subjectId_status " +
                "ON preparation_sessions(subjectId, status)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_preparation_sessions_chapterId_status " +
                "ON preparation_sessions(chapterId, status)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_preparation_sessions_topicId_status " +
                "ON preparation_sessions(topicId, status)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_preparation_session_items_sessionId_queueSequence_isCompleted " +
                "ON preparation_session_items(sessionId, queueSequence, isCompleted)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_preparation_session_items_sessionId_questionId " +
                "ON preparation_session_items(sessionId, questionId)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_preparation_session_items_sessionId_originalOrder " +
                "ON preparation_session_items(sessionId, originalOrder)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_preparation_session_items_questionId " +
                "ON preparation_session_items(questionId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_review_records_userId_questionId_reviewedAt " +
                "ON review_records(userId, questionId, reviewedAt)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_review_records_preparationSessionId " +
                "ON review_records(preparationSessionId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_review_records_questionId " +
                "ON review_records(questionId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_review_schedules_questionId " +
                "ON review_schedules(questionId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_review_schedules_userId_nextReviewAt " +
                "ON review_schedules(userId, nextReviewAt)",
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS question_keywords (" +
                "questionId TEXT NOT NULL, normalizedKeyword TEXT NOT NULL, " +
                "weight REAL NOT NULL, sortOrder INTEGER NOT NULL, " +
                "PRIMARY KEY(questionId, normalizedKeyword), " +
                "FOREIGN KEY(questionId) REFERENCES questions(id) ON UPDATE NO ACTION ON DELETE NO ACTION" +
                ")",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS user_question_flags (" +
                "userId TEXT NOT NULL, questionId TEXT NOT NULL, isImportant INTEGER NOT NULL, " +
                "createdAt INTEGER NOT NULL, PRIMARY KEY(userId, questionId), " +
                "FOREIGN KEY(userId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(questionId) REFERENCES questions(id) ON UPDATE NO ACTION ON DELETE NO ACTION" +
                ")",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS test_attempts (" +
                "id TEXT NOT NULL, userId TEXT NOT NULL, courseId TEXT NOT NULL, " +
                "academicPeriodId TEXT NOT NULL, subjectId TEXT, chapterId TEXT, topicId TEXT, " +
                "scopeType TEXT NOT NULL, scopeReferenceId TEXT, originType TEXT NOT NULL, " +
                "originReferenceId TEXT, creationRequestId TEXT NOT NULL, " +
                "requestedQuestionCount INTEGER NOT NULL, actualQuestionCount INTEGER NOT NULL, " +
                "questionSource TEXT NOT NULL, selectionSeed INTEGER, isTimed INTEGER NOT NULL, " +
                "timeLimitSeconds INTEGER, startedAt INTEGER, submittedAt INTEGER, completedAt INTEGER, " +
                "timeTakenSeconds INTEGER, score REAL, maxScore REAL, percentage REAL, " +
                "status TEXT NOT NULL, completionReason TEXT, evaluatorVersion TEXT, PRIMARY KEY(id), " +
                "FOREIGN KEY(userId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(courseId) REFERENCES courses(id) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(academicPeriodId) REFERENCES academic_periods(id) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(subjectId) REFERENCES subjects(id) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(chapterId) REFERENCES chapters(id) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(topicId) REFERENCES topics(id) ON UPDATE NO ACTION ON DELETE NO ACTION" +
                ")",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS test_attempt_questions (" +
                "id TEXT NOT NULL, testAttemptId TEXT NOT NULL, questionId TEXT NOT NULL, " +
                "displayOrder INTEGER NOT NULL, questionTextSnapshot TEXT NOT NULL, " +
                "referenceAnswerSnapshot TEXT NOT NULL, questionTypeSnapshot TEXT NOT NULL, " +
                "maxScoreSnapshot REAL NOT NULL, keywordSnapshotJson TEXT NOT NULL, " +
                "keywordSnapshotVersion INTEGER NOT NULL, PRIMARY KEY(id), " +
                "FOREIGN KEY(testAttemptId) REFERENCES test_attempts(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(questionId) REFERENCES questions(id) ON UPDATE NO ACTION ON DELETE NO ACTION" +
                ")",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS test_answers (" +
                "id TEXT NOT NULL, testAttemptQuestionId TEXT NOT NULL, answerText TEXT NOT NULL, " +
                "selectedOption TEXT, isFlagged INTEGER NOT NULL, timeSpentSeconds INTEGER, " +
                "keywordMatchPercent REAL, normalizedAnswer TEXT, matchedKeywordsJson TEXT, " +
                "missingKeywordsJson TEXT, score REAL, maxScore REAL, requiresReview INTEGER, " +
                "updatedAt INTEGER NOT NULL, PRIMARY KEY(id), " +
                "FOREIGN KEY(testAttemptQuestionId) REFERENCES test_attempt_questions(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE" +
                ")",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_question_keywords_questionId_sortOrder " +
                "ON question_keywords(questionId, sortOrder)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_user_question_flags_questionId " +
                "ON user_question_flags(questionId)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_test_attempts_creationRequestId " +
                "ON test_attempts(creationRequestId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_test_attempts_userId_status " +
                "ON test_attempts(userId, status)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_test_attempts_courseId_academicPeriodId_status " +
                "ON test_attempts(courseId, academicPeriodId, status)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_test_attempts_subjectId_status " +
                "ON test_attempts(subjectId, status)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_test_attempts_chapterId_status " +
                "ON test_attempts(chapterId, status)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_test_attempts_topicId_status " +
                "ON test_attempts(topicId, status)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_test_attempts_scopeType_scopeReferenceId " +
                "ON test_attempts(scopeType, scopeReferenceId)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_test_attempt_questions_testAttemptId_questionId " +
                "ON test_attempt_questions(testAttemptId, questionId)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_test_attempt_questions_testAttemptId_displayOrder " +
                "ON test_attempt_questions(testAttemptId, displayOrder)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_test_attempt_questions_questionId " +
                "ON test_attempt_questions(questionId)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_test_answers_testAttemptQuestionId " +
                "ON test_answers(testAttemptQuestionId)",
        )
        DatabaseSeed.seedReleaseTestContent(db)
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE test_attempts ADD COLUMN startedAtElapsedRealtimeMillis INTEGER",
        )
        db.execSQL("ALTER TABLE test_attempts ADD COLUMN startedBootId TEXT")
        db.execSQL("ALTER TABLE test_attempts ADD COLUMN deadlineAt INTEGER")
        db.execSQL(
            "ALTER TABLE test_answers ADD COLUMN accumulatedTimeMillis INTEGER NOT NULL DEFAULT 0",
        )
    }
}
