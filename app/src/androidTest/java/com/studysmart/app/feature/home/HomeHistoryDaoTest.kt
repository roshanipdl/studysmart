package com.studysmart.app.feature.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.studysmart.app.testing.createInMemoryStudySmartDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Only private in-memory curriculum and learning fixtures are changed. */
@RunWith(AndroidJUnit4::class)
class HomeHistoryDaoTest {
    @Test
    fun completedHistoryUsesEligibleCoverageAndDisappearsWhenContextIsArchived() = runBlocking {
        val db = createInMemoryStudySmartDatabase(ApplicationProvider.getApplicationContext<Context>())
        try {
            val sql = db.openHelper.writableDatabase
            val dao = db.preparationDao()
            assertNull(dao.observeRecentCompletedStudy("local-user").first())
            sql.execSQL("""
                INSERT INTO preparation_sessions(id,userId,courseId,academicPeriodId,subjectId,chapterId,
                    scopeType,originType,creationRequestId,status,startedAt,updatedAt,uniqueQuestionCount,reviewCount)
                VALUES ('home-session','local-user','course-bca','period-bca-semester-1','subject-bca-s1-dbms',
                    'chapter-bca-s1-dbms-01','chapter','chapter','home-request','active',0,0,1,1)
            """)
            sql.execSQL("""
                INSERT INTO review_records(id,userId,preparationSessionId,questionId,rating,reviewedAt)
                VALUES ('home-rating','local-user','home-session','question-bca-s1-dbms-direct','good',1)
            """)
            assertNull(dao.observeRecentCompletedStudy("local-user").first())
            sql.execSQL("UPDATE preparation_sessions SET status = 'completed', completedAt = 2 WHERE id = 'home-session'")
            val recent = dao.observeRecentCompletedStudy("local-user").first()!!
            assertEquals("Database Management System", recent.subjectName)
            assertEquals(1, recent.studied)
            assertTrue(recent.total >= recent.studied)
            assertNull(dao.observeRecentCompletedStudy("other-user").first())
            sql.execSQL("UPDATE subjects SET isActive = 0 WHERE id = 'subject-bca-s1-dbms'")
            assertNull(dao.observeRecentCompletedStudy("local-user").first())
        } finally {
            db.close()
        }
    }
}
