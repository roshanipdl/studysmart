package com.studysmart.app.core.data.importer

import com.studysmart.app.core.database.*

data class CurriculumBundleManifest(
    val datasetId: String,
    val contentVersion: Long,
    val schemaVersion: Int,
    val bundleDigest: String,
    val files: Map<String, String>,
)

/** Validated import payload; provenance/envelope metadata is never a Room column. */
data class CurriculumImportPayload(
    val manifest: CurriculumBundleManifest,
    val courses: List<CourseEntity>,
    val periods: List<AcademicPeriodEntity>,
    val subjects: List<SubjectEntity>,
    val chapters: List<ChapterEntity>,
    val topics: List<TopicEntity>,
    val questions: List<QuestionEntity>,
    val keywords: List<QuestionKeywordEntity>,
)

fun interface CurriculumImporter {
    suspend fun importIfNeeded(): CurriculumImportResult
}

enum class CurriculumImportResult { IMPORTED, ALREADY_CURRENT, NEWER_ALREADY_INSTALLED, DISABLED }

object NoOpCurriculumImporter : CurriculumImporter {
    override suspend fun importIfNeeded() = CurriculumImportResult.DISABLED
}
