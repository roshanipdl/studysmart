package com.studysmart.app.core.database

import androidx.room.RoomDatabase

internal fun applicationDatabaseSeed(): RoomDatabase.Callback = DatabaseSeed.callback()
