package com.studysmart.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.studysmart.app.core.common.DefaultDispatcherProvider
import com.studysmart.app.core.data.importer.*
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReviewedStageOneStartupTest {
    @Test fun applicationSeedImportsOnlyReviewedContentAndRepeatIsStable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, StudySmartDatabase::class.java)
            .addCallback(applicationDatabaseSeed()).build()
        try {
            assertTrue(db.curriculumImportDao().questions().isEmpty())
            val importer = RoomCurriculumImporter(db, AssetCurriculumBundleSource(context.assets), DefaultDispatcherProvider, Clock.systemUTC())
            assertEquals(CurriculumImportResult.IMPORTED, importer.importIfNeeded())
            val dao = db.curriculumImportDao()
            assertEquals(listOf("course-bca"), dao.courses().map { it.id })
            assertEquals(434, dao.questions().size)
            assertEquals(427, dao.questions().count { it.isActive })
            assertTrue(dao.questions().all { it.answerFormat == com.studysmart.app.core.model.AnswerFormat.HTML })
            assertNotNull(dao.state("bca-stage1-formatted"))
            assertFalse(dao.questions().any { it.id.startsWith("topic-recall-v2-") || it.id.startsWith("debug-") })
            val before = dao.questions()
            assertEquals(CurriculumImportResult.ALREADY_CURRENT, importer.importIfNeeded())
            assertEquals(before, dao.questions())
        } finally { db.close() }
    }
}
