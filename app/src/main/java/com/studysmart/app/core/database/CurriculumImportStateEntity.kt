package com.studysmart.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "curriculum_import_state")
data class CurriculumImportStateEntity(
    @PrimaryKey val datasetId: String,
    val contentVersion: Long,
    val bundleDigest: String,
    val importedAt: Instant,
)
