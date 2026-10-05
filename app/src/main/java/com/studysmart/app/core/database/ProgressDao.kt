package com.studysmart.app.core.database

import androidx.room.Dao
import androidx.room.Query
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("""
        SELECT COUNT(DISTINCT rr.questionId) FROM review_records rr
        JOIN questions q ON q.id = rr.questionId
        JOIN chapters ch ON ch.id = q.chapterId
        JOIN subjects s ON s.id = ch.subjectId
        JOIN academic_periods p ON p.id = s.academicPeriodId
        JOIN courses c ON c.id = p.courseId
        LEFT JOIN topics t ON t.id = q.topicId
        WHERE rr.userId = :userId AND rr.reviewedAt >= :start AND rr.reviewedAt < :end
          AND c.id = :courseId AND p.id = :periodId
          AND q.isActive = 1 AND ch.isActive = 1 AND s.isActive = 1 AND p.isActive = 1 AND c.isActive = 1
          AND (q.topicId IS NULL OR (t.id IS NOT NULL AND t.chapterId = ch.id AND t.isActive = 1))
    """)
    fun observeDailyRatedCount(userId: String, courseId: String, periodId: String, start: Instant, end: Instant): Flow<Int>

    @Query("""
        SELECT a.id FROM test_attempts a
        WHERE a.userId = :userId AND a.status = 'submitted' AND a.submittedAt IS NOT NULL
          AND a.courseId = :courseId AND a.academicPeriodId = :periodId
          AND EXISTS (
              SELECT 1 FROM test_attempt_questions aq
              JOIN questions q ON q.id = aq.questionId
              JOIN chapters ch ON ch.id = q.chapterId
              JOIN subjects s ON s.id = ch.subjectId
              JOIN academic_periods p ON p.id = s.academicPeriodId
              JOIN courses c ON c.id = p.courseId
              LEFT JOIN topics t ON t.id = q.topicId
              WHERE aq.testAttemptId = a.id AND c.id = :courseId AND p.id = :periodId
                AND q.isActive = 1 AND ch.isActive = 1 AND s.isActive = 1 AND p.isActive = 1 AND c.isActive = 1
                AND (q.topicId IS NULL OR (t.id IS NOT NULL AND t.chapterId = ch.id AND t.isActive = 1))
          )
        ORDER BY a.submittedAt DESC, a.id DESC LIMIT 1
    """)
    fun observeLatestSubmittedAttemptId(userId: String, courseId: String, periodId: String): Flow<String?>
}
