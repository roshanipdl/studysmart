package com.studysmart.app.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Real debug app imports its curriculum from the reviewed bundle, not sample seeds. */
internal fun applicationDatabaseSeed(): RoomDatabase.Callback = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO users(id,displayName,createdAt,updatedAt) VALUES ('local-user','Student',0,0)")
    }
}
