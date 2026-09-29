package com.studysmart.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room requires at least one entity; MVP-0 intentionally inserts no marker row. */
@Entity(tableName = "mvp0_schema_marker")
class Mvp0SchemaMarkerEntity(
    @PrimaryKey val id: Int = 1,
)
