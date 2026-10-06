package com.studysmart.app.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class LearningDataDao {
    // Children of sessions/attempts cascade. Delete review history first, including
    // history whose session was already discarded (nullable session reference).
    @Transaction
    open suspend fun clearForUser(userId: String) {
        deleteRecords(userId)
        deleteSchedules(userId)
        deleteFlags(userId)
        deleteSessions(userId)
        deleteAttempts(userId)
    }

    @Query("DELETE FROM review_records WHERE userId = :userId")
    abstract suspend fun deleteRecords(userId: String)
    @Query("DELETE FROM review_schedules WHERE userId = :userId")
    abstract suspend fun deleteSchedules(userId: String)
    @Query("DELETE FROM user_question_flags WHERE userId = :userId")
    abstract suspend fun deleteFlags(userId: String)
    @Query("DELETE FROM preparation_sessions WHERE userId = :userId")
    abstract suspend fun deleteSessions(userId: String)
    @Query("DELETE FROM test_attempts WHERE userId = :userId")
    abstract suspend fun deleteAttempts(userId: String)
}
