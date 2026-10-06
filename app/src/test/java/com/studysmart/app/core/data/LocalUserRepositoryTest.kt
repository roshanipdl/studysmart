package com.studysmart.app.core.data

import com.studysmart.app.core.database.UserDao
import com.studysmart.app.core.database.UserEntity
import com.studysmart.app.testing.TestDispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LocalUserRepositoryTest {
    @Test fun trimsNameAndPreservesIdentityAndCreationTime() = runTest {
        val original = UserEntity("local-user", "Student", Instant.EPOCH, Instant.EPOCH)
        val users = MutableStateFlow<UserEntity?>(original)
        val now = Instant.parse("2026-10-06T12:00:00Z")
        val dao = object : UserDao {
            override fun observeLocalUser() = users
            override suspend fun updateLocalName(name: String, updatedAt: Instant): Int {
                users.value = users.value?.copy(displayName = name, updatedAt = updatedAt)
                return if (users.value == null) 0 else 1
            }
        }
        val repository = RoomLocalUserRepository(dao, Clock.fixed(now, ZoneOffset.UTC), TestDispatcherProvider())
        repository.updateDisplayName("  Ada  ")
        assertEquals("Ada", repository.displayName.first())
        assertEquals(original.id, users.value!!.id)
        assertEquals(original.createdAt, users.value!!.createdAt)
        assertEquals(now, users.value!!.updatedAt)
        try { repository.updateDisplayName("  \n "); fail("Blank name accepted") } catch (_: IllegalArgumentException) { }
        assertEquals("Ada", repository.displayName.first())
    }

    @Test fun themeModesResolveSystemOnlyForSystem() {
        val light = com.studysmart.app.core.model.ThemePreference.LIGHT
        val dark = com.studysmart.app.core.model.ThemePreference.DARK
        val system = com.studysmart.app.core.model.ThemePreference.SYSTEM
        assertFalse(light.isDark(true))
        assertTrue(dark.isDark(false))
        assertTrue(system.isDark(true))
        assertFalse(system.isDark(false))
    }
}
