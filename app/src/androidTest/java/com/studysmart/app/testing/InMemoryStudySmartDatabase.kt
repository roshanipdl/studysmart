package com.studysmart.app.testing

import android.content.Context
import androidx.room.Room
import com.studysmart.app.core.database.DatabaseSeed
import com.studysmart.app.core.database.StudySmartDatabase

fun createInMemoryStudySmartDatabase(
    context: Context,
    seed: Boolean = true,
): StudySmartDatabase =
    (Room.inMemoryDatabaseBuilder(context, StudySmartDatabase::class.java).let { builder ->
        if (seed) builder.addCallback(DatabaseSeed.callback()) else builder
    })
        .allowMainThreadQueries()
        .build()

fun StudySmartDatabase.resetForTest() {
    clearAllTables()
}
