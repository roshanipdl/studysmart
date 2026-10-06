package com.studysmart.app.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/** Draft curriculum replacement is never applied in release builds. */
@Suppress("UNUSED_PARAMETER")
internal fun cleanupImportedVariant(db: SupportSQLiteDatabase, datasetId: String, version: Long) = Unit
