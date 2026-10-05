package com.studysmart.app.core.data

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.studysmart.app.core.common.DefaultDispatcherProvider
import com.studysmart.app.core.database.SearchIndexSynchronizer
import com.studysmart.app.core.database.SEARCH_RANKING_SQL
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

/**
 * Test-owned in-memory schema 7, including production FTS indexes and joins.
 * Measures repository subscription through first projected result, off main; excludes fixture
 * creation, three warm-ups, UI and its 300ms debounce. Thirty observations per query, nearest-rank
 * p95 strictly below 200ms is the gate. Median/max and every sample are reported, including failures;
 * no trimming, retries, forced GC, or timing adjustment. Debug/device characteristics affect results.
 * No production database, preferences, device settings, network, or shared storage is touched.
 */
class SearchPerformanceTest {
    @Test fun tenThousandQuestionsFullRepositoryWarmP95UnderTwoHundredMillis() = runBlocking {
        val db = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext<Context>())
        try {
            withContext(Dispatchers.IO) {
                fixture(db)
                val repository = RoomSearchRepository(db.searchDao(), DefaultDispatcherProvider, db)
                val cases = listOf("exact" to "Database", "short-prefix" to "da",
                    "multi-token" to "database transaction", "no-match" to "zzunfindable",
                    "case-diacritics" to "CAFÉ RÉSUMÉ")
                // Confirm the broad prefix actually materializes >20 candidates before ranking/limit.
                val candidates = db.searchDao().observeCandidates(requireNotNull(SearchQuery.parse("da")).expression).first()
                assertTrue("Broad prefix must cover the 10k fixture", candidates.size > 9_000)
                val failures = mutableListOf<String>()
                for ((label, query) in cases) {
                    repeat(3) { repository.observeResults(query).first() }
                    val samples = mutableListOf<Double>()
                    repeat(30) {
                        val start = System.nanoTime()
                        val result = repository.observeResults(query).first()
                        samples += (System.nanoTime() - start) / 1_000_000.0
                        when (label) {
                            "no-match" -> assertEquals(SearchResults(), result)
                            "exact", "short-prefix" -> {
                                assertEquals(20, result.questions.size)
                                if (label == "exact") assertEquals("perf-q-9993", result.questions.first().id)
                            }
                            "multi-token" -> assertEquals(20, result.questions.size)
                            "case-diacritics" -> assertEquals(listOf("perf-q-9992"), result.questions.map { it.id })
                        }
                    }
                    val sorted = samples.sorted()
                    val median = (sorted[14] + sorted[15]) / 2
                    val p95 = sorted[ceil(sorted.size * .95).toInt() - 1]
                    val report = "$label n=${samples.size} medianMs=$median p95Ms=$p95 maxMs=${sorted.last()} samplesMs=$samples"
                    report(report)
                    if (p95 >= 200.0) failures += report
                }
                // Run diagnostics only AFTER every acceptance sample, so this extra work cannot
                // warm up or alter the measured gate. These are not repository acceptance timings.
                for ((label, query) in cases) profilePhases(db, label, query)
                for ((label, query) in cases) profileCursor(db, label, query)
                assertTrue("Repository p95 must be <200ms:\n${failures.joinToString("\n")}", failures.isEmpty())
            }
        } finally { db.close() }
    }

    private fun report(message: String) {
        Log.i("SearchPerformanceTest", message)
        println(message)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("stream", "\nSearchPerformanceTest: $message\n")
        })
    }

    /** Includes Room cursor/materialization in candidates; no production timing hooks. */
    private suspend fun profilePhases(db: StudySmartDatabase, label: String, text: String) {
        val query = requireNotNull(SearchQuery.parse(text))
        repeat(5) { sample ->
            val start = System.nanoTime()
            var entered = start
            var candidatesDone = start
            var rankingDone = start
            var hydrationDone = start
            var candidateCount = 0
            var winnerCount = 0
            db.withTransaction {
                entered = System.nanoTime()
                val rows = db.searchDao().rankingCandidates(query.expression)
                candidatesDone = System.nanoTime()
                val winners = SearchRanking.topPerType(rows, query)
                rankingDone = System.nanoTime()
                if (winners.isNotEmpty()) db.searchDao().loadDisplayCandidates(winners.map { it.entityId })
                hydrationDone = System.nanoTime()
                candidateCount = rows.size
                winnerCount = winners.size
            }
            val end = System.nanoTime()
            fun millis(from: Long, to: Long) = (to - from) / 1_000_000.0
            report("diagnostic-only $label sample=$sample candidates=$candidateCount winners=$winnerCount " +
                "transactionEntryMs=${millis(start, entered)} candidatesMs=${millis(entered, candidatesDone)} " +
                "rankingMs=${millis(candidatesDone, rankingDone)} hydrationMs=${millis(rankingDone, hydrationDone)} " +
                "transactionExitMs=${millis(hydrationDone, end)} totalMs=${millis(start, end)}")
        }
    }

    /**
     * Same production SQL, binding and read transaction, without Room row construction.
     * count() executes SQL and fills a cursor window; walking may refill windows, so these
     * are NOT pure SQL/JNI timings. The second pass additionally reads every projected
     * value like the generated DAO, but retains no row objects. Both remain diagnostic-only.
     */
    private suspend fun profileCursor(db: StudySmartDatabase, label: String, text: String) {
        val query = requireNotNull(SearchQuery.parse(text))
        val binding = arrayOf<Any?>(query.expression)
        db.withTransaction {
            val plan = db.query(SimpleSQLiteQuery("EXPLAIN QUERY PLAN $SEARCH_RANKING_SQL", binding)).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }
            }
            report("diagnostic-only $label queryPlan=$plan")
        }
        repeat(5) { sample ->
            for (readValues in listOf(false, true)) {
                db.withTransaction {
                    val start = System.nanoTime()
                    db.query(SimpleSQLiteQuery(SEARCH_RANKING_SQL, binding)).use { cursor ->
                        val opened = System.nanoTime()
                        val count = cursor.count
                        val counted = System.nanoTime()
                        var walked = 0
                        var checksum = 0L
                        while (cursor.moveToNext()) {
                            walked++
                            if (readValues) {
                                // Same two reads as the packed production DAO projection.
                                checksum += cursor.getString(0).length
                                checksum += cursor.getBlob(1).size
                            }
                        }
                        val finished = System.nanoTime()
                        assertEquals("Raw cursor must walk every MATCH", count, walked)
                        fun millis(from: Long, to: Long) = (to - from) / 1_000_000.0
                        report("diagnostic-only $label cursor sample=$sample readValues=$readValues " +
                            "rows=$walked checksum=$checksum openMs=${millis(start, opened)} " +
                            "countFillMs=${millis(opened, counted)} walkMs=${millis(counted, finished)} " +
                            "totalMs=${millis(start, finished)}")
                    }
                }
            }
        }
    }

    private suspend fun fixture(db: StudySmartDatabase) = db.withTransaction {
        val sql = db.openHelper.writableDatabase
        // Retain the six baseline questions; add 9,994 across semester/year courses and six chapters.
        val chapters = listOf("chapter-bca-s1-dbms-01", "chapter-bca-s1-dbms-02",
            "chapter-bca-s1-web-01", "chapter-bca-s5-ai-01", "chapter-bca-s5-net-01",
            "chapter-year1-foundations-01")
        sql.compileStatement("INSERT INTO questions(id, chapterId, topicId, question, answer, questionType, difficulty, maxScore, explanation, isActive, sortOrder, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, 'written', 'normal', 2.0, '', 1, ?, 0, 0)").use { insert ->
            repeat(9_994) { index ->
                insert.clearBindings()
                insert.bindString(1, "perf-q-$index")
                insert.bindString(2, chapters[index % chapters.size])
                if (index % 12 == 0) insert.bindString(3, "topic-bca-s1-dbms-01") else insert.bindNull(3)
                insert.bindString(4, when (index) {
                    9_993 -> "Database" // Late insertion/high sort order must still rank before prefixes.
                    9_992 -> "Café résumé"
                    else -> "Database transaction principles question $index"
                })
                insert.bindString(5, "Transactions provide atomicity, consistency, isolation and durability.")
                insert.bindLong(6, index.toLong())
                insert.executeInsert()
            }
        }
        sql.compileStatement("INSERT INTO review_records(id, userId, questionId, rating, reviewedAt) VALUES (?, 'local-user', ?, 'good', ?)").use { insert ->
            repeat(1_000) { index ->
                insert.bindString(1, "perf-review-$index")
                insert.bindString(2, "perf-q-$index")
                insert.bindLong(3, 1_700_000_000_000L + index)
                insert.executeInsert()
            }
        }
        sql.execSQL("INSERT INTO review_schedules(userId, questionId, learningState, lastReviewedAt, nextReviewAt, intervalSeconds, repetitionCount, lapseCount) SELECT userId, questionId, 'learning', reviewedAt, reviewedAt + 259200000, 259200, 1, 0 FROM review_records")
        repeat(100) { attempt ->
            val yearly = attempt % 2 == 1
            sql.execSQL("INSERT INTO test_attempts(id, userId, courseId, academicPeriodId, scopeType, originType, creationRequestId, requestedQuestionCount, actualQuestionCount, questionSource, isTimed, startedAt, submittedAt, completedAt, timeTakenSeconds, score, maxScore, percentage, status) VALUES (?, 'local-user', ?, ?, 'chapter', 'chapter', ?, 10, 10, 'all', 0, 1700000000000, 1700000060000, 1700000060000, 60, 10, 20, 50, 'submitted')",
                arrayOf<Any?>("perf-attempt-$attempt", if (yearly) "course-yearly-example" else "course-bca",
                    if (yearly) "period-example-year-1" else "period-bca-semester-1", "perf-request-$attempt"))
            repeat(10) { position ->
                val index = (attempt * 10 + position) * 6 + if (yearly) 5 else 0
                val id = "perf-snapshot-$attempt-$position"
                sql.execSQL("INSERT INTO test_attempt_questions(id, testAttemptId, questionId, displayOrder, questionTextSnapshot, referenceAnswerSnapshot, questionTypeSnapshot, maxScoreSnapshot, keywordSnapshotJson, keywordSnapshotVersion) SELECT ?, ?, id, ?, question, answer, questionType, maxScore, '[]', 1 FROM questions WHERE id = ?",
                    arrayOf<Any?>(id, "perf-attempt-$attempt", position, "perf-q-$index"))
                sql.execSQL("INSERT INTO test_answers(id, testAttemptQuestionId, answerText, isFlagged, timeSpentSeconds, accumulatedTimeMillis, keywordMatchPercent, score, maxScore, requiresReview, updatedAt) VALUES (?, ?, 'Atomicity and isolation', 0, 6, 6000, 50, 1, 2, 1, 1700000060000)",
                    arrayOf<Any?>("perf-answer-$attempt-$position", id))
            }
        }
        SearchIndexSynchronizer.synchronize(sql)
        sql.query("SELECT COUNT(*) FROM questions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(10_000, cursor.getInt(0))
        }
        sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
    }
}
