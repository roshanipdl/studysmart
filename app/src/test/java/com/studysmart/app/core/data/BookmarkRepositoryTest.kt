package com.studysmart.app.core.data

import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.database.BookmarkDao
import com.studysmart.app.core.database.SavedQuestionRow
import com.studysmart.app.core.database.UserQuestionFlagEntity
import com.studysmart.app.core.model.SavedQuestionScope
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BookmarkRepositoryTest {
    private val now = Instant.parse("2026-10-04T12:00:00Z")

    @Test fun explicitSaveIsIdempotentAndKeepsCreationTime() = runTest {
        val dao = FakeBookmarkDao()
        dao.setBookmarked("user", "question", true, now)
        dao.setBookmarked("user", "question", true, now.plusSeconds(10))
        assertEquals(1, dao.flags.size)
        assertEquals(now, dao.flags.getValue("user" to "question").createdAt)
        assertEquals(1, dao.upserts)
    }

    @Test fun removalIsIdempotentAndAResaveGetsNewOrderingTime() = runTest {
        val dao = FakeBookmarkDao()
        dao.setBookmarked("user", "question", true, now)
        dao.setBookmarked("user", "question", false, now)
        dao.setBookmarked("user", "question", false, now)
        assertTrue(dao.flags.isEmpty())
        dao.setBookmarked("user", "question", true, now.plusSeconds(1))
        assertEquals(now.plusSeconds(1), dao.flags.values.single().createdAt)
    }

    @Test fun inactiveQuestionCannotBeSavedButItsExistingFlagCanBeRemoved() = runTest {
        val dao = FakeBookmarkDao()
        dao.setBookmarked("user", "question", true, now)
        dao.active = false
        assertFalse(dao.setBookmarked("user", "question", true, now.plusSeconds(1)))
        assertEquals(now, dao.flags.values.single().createdAt)
        assertTrue(dao.setBookmarked("user", "question", false, now))
        assertTrue(dao.flags.isEmpty())
    }

    @Test fun flagsAreIsolatedByUser() = runTest {
        val dao = FakeBookmarkDao()
        dao.setBookmarked("first", "question", true, now)
        dao.setBookmarked("second", "question", false, now)
        assertEquals(1, dao.flags.size)
        assertNull(dao.getFlag("second", "question"))
    }

    @Test fun existingFalseFlagIsActivatedWithNewTime() = runTest {
        val dao = FakeBookmarkDao()
        dao.upsertFlag(UserQuestionFlagEntity("user", "question", false, Instant.EPOCH))
        dao.setBookmarked("user", "question", true, now)
        assertEquals(UserQuestionFlagEntity("user", "question", true, now), dao.flags.values.single())
    }

    @Test fun repositoryUsesInjectedClockUserAndScopedFilters() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider { override val io = dispatcher; override val main = dispatcher }
        val dao = FakeBookmarkDao()
        val repository = RoomBookmarkRepository(dao, Clock.fixed(now, ZoneOffset.UTC), dispatchers, "user")
        assertTrue(repository.setBookmarked("question", true))
        assertEquals(setOf("question"), repository.observeBookmarkedIds().first())
        repository.observeSavedQuestions(SavedQuestionScope("course", "period", "subject", "chapter")).first()
        assertEquals(listOf("user", "course", "period", "subject", "chapter"), dao.lastScope)
        assertEquals(now, dao.flags.values.single().createdAt)
    }

    @Test fun writeFailureIsNotReportedAsSuccess() = runTest {
        val dao = FakeBookmarkDao().apply { failWrites = true }
        try { dao.setBookmarked("user", "question", true, now); fail("Expected failure") }
        catch (_: IllegalStateException) { assertTrue(dao.flags.isEmpty()) }
    }

    @Test fun invalidScopesAreRejected() {
        listOf(
            { SavedQuestionScope("", "period") },
            { SavedQuestionScope("course", "") },
            { SavedQuestionScope("course", "period", chapterId = "chapter") },
            { SavedQuestionScope("course", "period", subjectId = "") },
        ).forEach { invalid ->
            try { invalid(); fail("Expected invalid scope") } catch (_: IllegalArgumentException) { }
        }
    }
}

private class FakeBookmarkDao : BookmarkDao {
    val flags = mutableMapOf<Pair<String, String>, UserQuestionFlagEntity>()
    private val ids = MutableStateFlow<List<String>>(emptyList())
    var active = true
    var upserts = 0
    var failWrites = false
    var lastScope: List<String?>? = null
    override fun observeBookmarkedIds(userId: String): Flow<List<String>> = ids.map {
        flags.values.filter { flag -> flag.userId == userId && flag.isImportant }.map { flag -> flag.questionId }
    }
    override suspend fun getFlag(userId: String, questionId: String) = flags[userId to questionId]
    override suspend fun upsertFlag(flag: UserQuestionFlagEntity) {
        check(!failWrites)
        flags[flag.userId to flag.questionId] = flag
        upserts++
        ids.value = flags.values.filter { it.isImportant }.map { it.questionId }
    }
    override suspend fun deleteFlag(userId: String, questionId: String) {
        check(!failWrites)
        flags.remove(userId to questionId)
        ids.value = flags.values.filter { it.isImportant }.map { it.questionId }
    }
    override suspend fun isActiveQuestion(questionId: String) = active
    override fun observeSavedQuestions(userId: String, courseId: String, academicPeriodId: String, subjectId: String?, chapterId: String?): Flow<List<SavedQuestionRow>> {
        lastScope = listOf(userId, courseId, academicPeriodId, subjectId, chapterId)
        return flowOf(emptyList())
    }
}
