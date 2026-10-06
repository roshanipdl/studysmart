package com.studysmart.app.core.database

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = 'local-user'")
    fun observeLocalUser(): Flow<UserEntity?>

    @Query("UPDATE users SET displayName = :name, updatedAt = :updatedAt WHERE id = 'local-user'")
    suspend fun updateLocalName(name: String, updatedAt: Instant): Int
}
