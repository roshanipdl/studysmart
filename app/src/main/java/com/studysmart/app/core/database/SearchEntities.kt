package com.studysmart.app.core.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "search_documents", indices = [Index(value = ["type", "entityId"], unique = true)])
data class SearchDocumentEntity(
    @PrimaryKey(autoGenerate = true) val rowid: Long = 0,
    val type: String,
    val entityId: String,
    val courseId: String,
    val academicPeriodId: String,
    val subjectId: String,
    val chapterId: String?,
    val topicId: String?,
    val text: String,
)

@Fts4(contentEntity = SearchDocumentEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "search_fts")
data class SearchFtsEntity(val text: String)
