package com.studysmart.app.core.data

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.common.DefaultDispatcherProvider
import com.studysmart.app.core.database.SearchIndexSynchronizer
import com.studysmart.app.core.database.SearchDao
import com.studysmart.app.core.database.SearchCandidateRow
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

/** Fresh in-memory Room only. Never touches installed app data or device settings. */
class SearchRepositoryTest {
    private suspend fun fixture(db: StudySmartDatabase) {
        val dao = db.curriculumImportDao()
        db.withTransaction {
            dao.insertCourse(dao.courses().first().copy(id = "search-c", code = "SC", name = "Live course", isActive = true))
            dao.insertPeriod(dao.periods().first().copy(id = "search-p", courseId = "search-c", name = "Live period", isActive = true))
            dao.insertSubject(dao.subjects().first().copy(id = "search-s", academicPeriodId = "search-p", code = "Needle", name = "Needle subject", isActive = true))
            dao.insertChapter(dao.chapters().first().copy(id = "search-ch", subjectId = "search-s", title = "Needle chapter", isActive = true))
            dao.insertTopic(dao.topics().first().copy(id = "search-t", chapterId = "search-ch", title = "Needle topic", isActive = true))
            val base = dao.questions().first()
            repeat(26) { index -> dao.insertQuestion(base.copy(id = "search-q-$index", chapterId = "search-ch",
                topicId = if (index % 2 == 0) null else "search-t", question = "Needle question $index",
                sortOrder = index, isActive = true)) }
            dao.insertQuestion(base.copy(id = "search-exact", chapterId = "search-ch", topicId = null,
                question = "Needle", sortOrder = 999, isActive = true))
            dao.insertQuestion(base.copy(id = "search-literal", chapterId = "search-ch", topicId = null,
                question = "Needle OR quoted", isActive = true))
            SearchIndexSynchronizer.synchronize(db.openHelper.writableDatabase)
        }
    }
    private fun repository(db: StudySmartDatabase) = RoomSearchRepository(db.searchDao(), DefaultDispatcherProvider, db)

    @Test fun packedProjectionPreservesUnicodeSubjectTiersAndStableIdTies() = runBlocking {
        val db = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext<Context>())
        try {
            fixture(db)
            val curriculum = db.curriculumImportDao()
            db.withTransaction {
                val subject = curriculum.subjects().single { it.id == "search-s" }
                curriculum.insertSubject(subject.copy(code = "NÉÉDLE", name = "Needle café 😀"))
                val base = curriculum.questions().first()
                for (id in listOf("tie-😀", "tie-\uE000", "tie-:a")) {
                    curriculum.insertQuestion(base.copy(id = id, chapterId = "search-ch",
                        topicId = null, question = "Needle", sortOrder = -7, isActive = true))
                }
                SearchIndexSynchronizer.synchronize(db.openHelper.writableDatabase)
            }
            for (text in listOf("needle", "nee", "needle cafe")) {
                val query = requireNotNull(SearchQuery.parse(text))
                val full = db.searchDao().observeCandidates(query.expression).first()
                val packed = db.searchDao().rankingCandidates(query.expression)
                assertEquals(full.size, packed.size)
                fun identities(rows: List<SearchCandidateRow>) =
                    SearchRanking.topPerType(rows, query).map { it.type to it.entityId }
                assertEquals(identities(full), identities(packed))
            }
        } finally { db.close() }
    }

    @Test fun eachGroupHasIndependentTwentyResultLimitAfterRanking() = runBlocking {
        val db = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext<Context>())
        try {
            fixture(db)
            val dao = db.curriculumImportDao()
            db.withTransaction {
                val subject = dao.subjects().single { it.id == "search-s" }
                val chapter = dao.chapters().single { it.id == "search-ch" }
                val topic = dao.topics().single { it.id == "search-t" }
                repeat(26) { index ->
                    dao.insertSubject(subject.copy(id = "limit-s-$index", code = "LIMIT-$index", name = "Needle subject $index", sortOrder = index))
                    dao.insertChapter(chapter.copy(id = "limit-ch-$index", chapterNumber = 100 + index, title = "Needle chapter $index", sortOrder = index))
                    dao.insertTopic(topic.copy(id = "limit-t-$index", title = "Needle topic $index", sortOrder = index))
                }
                dao.insertSubject(subject.copy(id = "limit-exact-s", code = "EXACT", name = "Needle", sortOrder = 999))
                dao.insertChapter(chapter.copy(id = "limit-exact-ch", chapterNumber = 999, title = "Needle", sortOrder = 999))
                dao.insertTopic(topic.copy(id = "limit-exact-t", title = "Needle", sortOrder = 999))
                SearchIndexSynchronizer.synchronize(db.openHelper.writableDatabase)
            }
            val results = repository(db).observeResults("needle").first()
            assertEquals(listOf(20, 20, 20, 20), listOf(results.subjects.size, results.chapters.size, results.topics.size, results.questions.size))
            assertEquals("limit-exact-ch", results.chapters.first().id)
            assertEquals("limit-exact-t", results.topics.first().id)
            assertEquals("search-exact", results.questions.first().id)
            assertTrue(results.subjects.take(2).map { it.id }.contains("limit-exact-s")) // Original subject code is also exact.
        } finally { db.close() }
    }

    @Test fun allGroupsPrefixContextsNullableTopicsAndPostRankingLimit() = runBlocking {
        val db = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext<Context>())
        try {
            fixture(db)
            val results = repository(db).observeResults("NEED").first()
            assertEquals("search-s", results.subjects.single().id)
            assertEquals("search-ch", results.chapters.single().id)
            assertEquals("search-t", results.topics.single().id)
            assertEquals(20, results.questions.size)
            assertEquals("Live course", results.questions.first().context.courseName)
            assertTrue(results.questions.any { it.topicId == null })
            assertTrue(results.questions.any { it.topicId == "search-t" && it.topicTitle == "Needle topic" })
            assertEquals("search-exact", repository(db).observeResults("needle").first().questions.first().id)
            assertEquals("search-s", repository(db).observeResults("needle subject").first().subjects.single().id)
        } finally { db.close() }
    }

    @Test fun punctuationOperatorsAreLiteralAllTokensRequired() = runBlocking {
        val db = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext<Context>())
        try {
            fixture(db)
            assertEquals(listOf("search-literal"), repository(db).observeResults("“needle” OR quoted*").first().questions.map { it.id })
            assertEquals(listOf("search-literal"), repository(db).observeResults("need quo").first().questions.map { it.id })
            assertEquals(listOf("search-literal"), repository(db).observeResults("quo need").first().questions.map { it.id })
            assertTrue(repository(db).observeResults("needle absent").first().questions.isEmpty())
            assertEquals(SearchResults(), repository(db).observeResults("***").first())
        } finally { db.close() }
    }

    @Test fun indexedUnicodeMatchesAccentsNonLatinAndSupplementaryTokens() = runBlocking {
        val db = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext<Context>())
        try {
            fixture(db)
            val dao = db.curriculumImportDao()
            db.withTransaction {
                val base = dao.questions().first()
                listOf("Café résumé", "数据库 学习", "𐐀𐐁 lesson").forEachIndexed { index, text ->
                    dao.insertQuestion(base.copy(id = "unicode-$index", chapterId = "search-ch", topicId = null,
                        question = text, isActive = true))
                }
                SearchIndexSynchronizer.synchronize(db.openHelper.writableDatabase)
            }
            listOf("CAFE resume", "数据库", "𐐨𐐩").forEachIndexed { index, query ->
                assertEquals(listOf("unicode-$index"), repository(db).observeResults(query).first().questions.map { it.id })
            }
        } finally { db.close() }
    }

    @Test fun activeAncestryAndInvalidTopicMembershipAreFilteredBeforeLimit() = runBlocking {
        val db = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext<Context>())
        try {
            fixture(db)
            val dao = db.curriculumImportDao()
            val topic = dao.topics().single { it.id == "search-t" }
            dao.updateTopic(topic.copy(isActive = false)) // Deliberately stale index; live join must exclude it.
            val results = repository(db).observeResults("needle").first()
            assertTrue(results.topics.isEmpty())
            assertTrue(results.questions.all { it.topicId == null })
            dao.updateTopic(topic.copy(chapterId = dao.chapters().first { it.id != "search-ch" }.id))
            assertTrue(repository(db).observeResults("needle").first().questions.all { it.topicId == null })
            // The moved topic is legitimately in the active baseline course now.
            val moved = repository(db).observeResults("needle").first().topics.single()
            assertNotEquals("search-c", moved.context.courseId)
            // Restore this test's ancestry before disabling its course.
            dao.updateTopic(topic)
            dao.updateCourse(dao.courses().single { it.id == "search-c" }.copy(isActive = false))
            assertEquals(SearchResults(), repository(db).observeResults("needle").first())
        } finally { db.close() }
    }

    @Test fun existingSubscriptionReactsToLiveLabelsAndIndexedTextUpdates() = runBlocking {
        val db = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext<Context>())
        try {
            fixture(db)
            val events = Channel<SearchResults>(Channel.UNLIMITED)
            val job = launch { repository(db).observeResults("needle").collect { events.send(it) } }
            try {
                withTimeout(5000) { events.receive() }
                val dao = db.curriculumImportDao()
                dao.updateCourse(dao.courses().single { it.id == "search-c" }.copy(name = "Renamed live course"))
                withTimeout(5000) { while (events.receive().subjects.single().context.courseName != "Renamed live course") Unit }
                db.withTransaction {
                    dao.updateSubject(dao.subjects().single { it.id == "search-s" }.copy(code = "OTHER", name = "Other subject"))
                    SearchIndexSynchronizer.synchronize(db.openHelper.writableDatabase)
                }
                withTimeout(5000) { while (events.receive().subjects.isNotEmpty()) Unit }
            } finally { job.cancel(); job.join(); events.close() }
        } finally { db.close() }
    }

    @Test fun rankingAndHydrationUseOneSnapshotAndObserveTheRacingCommit() = runBlocking {
        val db = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext<Context>())
        try {
            fixture(db)
            val curriculum = db.curriculumImportDao()
            val exact = curriculum.questions().single { it.id == "search-exact" }
            val atHydration = CompletableDeferred<Unit>()
            val writerAttempting = CompletableDeferred<Unit>()
            // Launched outside the repository's transaction context: a genuinely competing writer.
            val writer = launch(Dispatchers.IO) {
                atHydration.await()
                writerAttempting.complete(Unit)
                db.withTransaction {
                    curriculum.updateQuestion(exact.copy(question = "Unrelated replacement"))
                    SearchIndexSynchronizer.synchronize(db.openHelper.writableDatabase)
                }
            }
            var intercepted = false
            val dao = object : SearchDao by db.searchDao() {
                override suspend fun loadDisplayCandidates(ids: List<String>): List<SearchCandidateRow> {
                    if (!intercepted) {
                        intercepted = true
                        atHydration.complete(Unit)
                        writerAttempting.await()
                        // Without the encompassing transaction this writer commits before hydration.
                        // With it, the writer waits and the entire first result uses the old snapshot.
                        withTimeoutOrNull(500) { writer.join() }
                    }
                    return db.searchDao().loadDisplayCandidates(ids)
                }
            }
            val events = Channel<SearchResults>(Channel.UNLIMITED)
            val observer = launch {
                RoomSearchRepository(dao, DefaultDispatcherProvider, db)
                    .observeResults("needle").collect { events.send(it) }
            }
            try {
                val before = withTimeout(5000) { events.receive() }
                assertEquals(20, before.questions.size)
                assertEquals("search-exact", before.questions.first().id)
                assertEquals("Needle", before.questions.first().title)
                withTimeout(5000) { writer.join() }
                val after = withTimeout(5000) {
                    var next = events.receive()
                    while (next.questions.any { it.id == "search-exact" }) next = events.receive()
                    next
                }
                assertEquals(20, after.questions.size)
                assertTrue(after.questions.all { it.title.contains("Needle") })
                assertEquals(repository(db).observeResults("needle").first(), after)
            } finally {
                observer.cancel(); observer.join()
                writer.cancel(); writer.join()
                events.close()
            }
        } finally { db.close() }
    }
}
