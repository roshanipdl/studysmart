package com.studysmart.app.core.data

import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.database.UserDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock

interface LocalUserRepository {
    val displayName: Flow<String?>
    suspend fun updateDisplayName(name: String)
}

fun normalizedDisplayName(name: String): String = name.trim().also {
    require(it.isNotBlank()) { "Name must not be blank." }
}

class RoomLocalUserRepository(
    private val dao: UserDao,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : LocalUserRepository {
    override val displayName = dao.observeLocalUser().map { it?.displayName }
    override suspend fun updateDisplayName(name: String) {
        val normalized = normalizedDisplayName(name)
        withContext(dispatchers.io) {
            check(dao.updateLocalName(normalized, clock.instant()) == 1) { "Local user is unavailable." }
        }
    }
}
