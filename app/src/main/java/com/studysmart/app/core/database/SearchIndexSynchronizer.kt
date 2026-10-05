package com.studysmart.app.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/** Caller owns the transaction (Room import, creation, migration, or debug seed). */
object SearchIndexSynchronizer {
    fun synchronize(db: SupportSQLiteDatabase) {
        check(db.inTransaction()) { "Search synchronization requires a transaction" }
        db.execSQL("CREATE TEMP TABLE IF NOT EXISTS search_projection (type TEXT NOT NULL, entityId TEXT NOT NULL, courseId TEXT NOT NULL, academicPeriodId TEXT NOT NULL, subjectId TEXT NOT NULL, chapterId TEXT, topicId TEXT, text TEXT NOT NULL, PRIMARY KEY(type, entityId))")
        db.execSQL("DELETE FROM search_projection")
        try {
            db.compileStatement("INSERT INTO search_projection VALUES (?, ?, ?, ?, ?, ?, ?, ?)").use { insert ->
                // Resolve and order ancestry once; normalize one row at a time without
                // materializing the curriculum or repeating OFFSET scans per batch.
                db.query("SELECT * FROM (${SearchDocumentProjector.sourceSql}) ORDER BY type, entityId").use { cursor ->
                    while (cursor.moveToNext()) {
                        val doc = SearchDocumentProjector.project(
                            cursor.getString(0), cursor.getString(1), cursor.getString(2),
                            cursor.getString(3), cursor.getString(4),
                            if (cursor.isNull(5)) null else cursor.getString(5),
                            if (cursor.isNull(6)) null else cursor.getString(6), cursor.getString(7),
                        )
                        insert.clearBindings()
                        listOf(doc.type, doc.entityId, doc.courseId, doc.academicPeriodId,
                            doc.subjectId, doc.chapterId, doc.topicId, doc.text).forEachIndexed { index, value ->
                            if (value == null) insert.bindNull(index + 1) else insert.bindString(index + 1, value)
                        }
                        insert.executeInsert()
                    }
                }
            }
            db.execSQL("DELETE FROM search_documents WHERE NOT EXISTS (SELECT 1 FROM search_projection p WHERE p.type = search_documents.type AND p.entityId = search_documents.entityId)")
            val columns = listOf("courseId", "academicPeriodId", "subjectId", "chapterId", "topicId", "text")
            val identity = "p.type = search_documents.type AND p.entityId = search_documents.entityId"
            val assignments = columns.joinToString { "$it = (SELECT p.$it FROM search_projection p WHERE $identity)" }
            val changed = columns.joinToString(" OR ") { "search_documents.$it IS NOT p.$it" }
            db.execSQL("UPDATE search_documents SET $assignments WHERE EXISTS (SELECT 1 FROM search_projection p WHERE $identity AND ($changed))")
            db.execSQL("INSERT INTO search_documents(type, entityId, courseId, academicPeriodId, subjectId, chapterId, topicId, text) SELECT p.* FROM search_projection p WHERE NOT EXISTS (SELECT 1 FROM search_documents d WHERE d.type = p.type AND d.entityId = p.entityId)")
        } finally {
            db.execSQL("DROP TABLE IF EXISTS search_projection")
        }
    }
}
