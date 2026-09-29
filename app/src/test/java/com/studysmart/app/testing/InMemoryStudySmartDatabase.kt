package com.studysmart.app.testing

import android.content.Context
import androidx.room.Room
import com.studysmart.app.core.database.StudySmartDatabase

fun createInMemoryStudySmartDatabase(context: Context): StudySmartDatabase =
    Room.inMemoryDatabaseBuilder(context, StudySmartDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun StudySmartDatabase.resetForTest() {
    clearAllTables()
}
