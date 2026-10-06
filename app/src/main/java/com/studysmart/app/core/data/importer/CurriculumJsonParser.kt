package com.studysmart.app.core.data.importer

import com.studysmart.app.core.database.*
import com.studysmart.app.core.model.AcademicPeriodType
import com.studysmart.app.core.model.AnswerFormat
import com.studysmart.app.core.model.QuestionType
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

class CurriculumJsonParser {
    fun read(source: CurriculumBundleSource): CurriculumImportPayload {
        val metadata = json(source.read("bundle-manifest.json"))
        val files = linkedMapOf<String, String>()
        metadata.rows("files").forEach { row ->
            val path = checkedBundlePath(row.text("path"))
            require(path != "bundle-manifest.json" && files.put(path, row.text("sha256")) == null) { "Duplicate/invalid manifest path: $path" }
        }
        val schemaVersion = metadata.integer("schemaVersion")
        require(schemaVersion in 1L..2L) { "Unsupported bundle schema version" }
        val manifest = CurriculumBundleManifest(metadata.text("datasetId"), metadata.integer("contentVersion"),
            schemaVersion.toInt(), metadata.text("bundleDigest"), files)
        require(manifest.contentVersion > 0 && manifest.datasetId.isNotBlank()) { "Unsupported bundle version" }
        require(manifest.bundleDigest == sha256(files.toSortedMap().entries.joinToString("") { "${it.key}\t${it.value}\n" }.toByteArray(StandardCharsets.UTF_8))) { "Bundle digest mismatch" }
        val documents = files.mapValues { (path, expected) ->
            val raw = source.read(path)
            require(expected.matches(Regex("[a-f0-9]{64}")) && sha256(raw) == expected) { "Hash mismatch: $path" }
            json(raw)
        }
        val catalog = requireNotNull(documents["curriculum.json"]) { "Catalog missing" }
        checkEnvelope(catalog, schemaVersion)
        val inventory = catalog.rows("subjectFiles")
        require(inventory.map { it.text("path") }.toSet().size == inventory.size) { "Duplicate subject paths" }
        require(documents.keys == setOf("curriculum.json") + inventory.map { checkedBundlePath(it.text("path")) }) { "Manifest/catalog inventory mismatch" }
        val subjects = mutableListOf<SubjectEntity>()
        val chapters = mutableListOf<ChapterEntity>()
        val topics = mutableListOf<TopicEntity>()
        val questions = mutableListOf<QuestionEntity>()
        val keywords = mutableListOf<QuestionKeywordEntity>()
        inventory.forEach { entry ->
            val path = entry.text("path")
            val doc = requireNotNull(documents[path]) { "Missing subject file: $path" }
            checkEnvelope(doc, schemaVersion)
            val subject = doc.objectValue("subject").let { r -> SubjectEntity(r.text("id"), r.text("academicPeriodId"), r.text("code"), r.text("name"), r.text("description"), r.int("sortOrder"), r.bool("isActive"), r.instant("createdAt"), r.instant("updatedAt")) }
            require(subject.id == entry.text("subjectId") && subject.academicPeriodId == entry.text("academicPeriodId")) { "Subject inventory mismatch: $path" }
            subjects += subject
            doc.rows("chapters").forEach { r ->
                require(r.text("subjectId") == subject.id) { "Chapter belongs to another subject: $path" }
                chapters += ChapterEntity(r.text("id"), r.text("subjectId"), r.int("chapterNumber"), r.text("title"), r.text("description"), r.int("sortOrder"), r.bool("isActive"), r.instant("createdAt"), r.instant("updatedAt"))
            }
            val localChapters = doc.rows("chapters").map { it.text("id") }.toSet()
            doc.rows("topics").forEach { r ->
                require(r.text("chapterId") in localChapters) { "Topic outside subject: $path" }
                topics += TopicEntity(r.text("id"), r.text("chapterId"), r.text("title"), r.text("description"), r.int("sortOrder"), r.bool("isActive"), r.instant("createdAt"), r.instant("updatedAt"))
            }
            doc.rows("questions").forEach { r ->
                require(r.text("chapterId") in localChapters && r.text("questionType") == "written") { "Invalid question owner/type: $path" }
                val format = if (schemaVersion == 1L) {
                    require(!r.has("answerFormat")) { "Version 1 forbids answerFormat" }
                    AnswerFormat.PLAIN_TEXT
                } else {
                    require(r.has("answerFormat")) { "Version 2 requires answerFormat" }
                    AnswerFormat.fromStoredValue(r.text("answerFormat"))
                }
                questions += QuestionEntity(r.text("id"), r.text("chapterId"), r.nullableText("topicId"), r.text("question"), r.text("answer"), QuestionType.WRITTEN, r.text("difficulty"), r.number("maxScore"), r.text("explanation"), r.bool("isActive"), r.int("sortOrder"), r.instant("createdAt"), r.instant("updatedAt"), format)
            }
            val localQuestions = doc.rows("questions").map { it.text("id") }.toSet()
            doc.rows("questionKeywords").forEach { r ->
                require(r.text("questionId") in localQuestions) { "Keyword outside subject: $path" }
                keywords += QuestionKeywordEntity(r.text("questionId"), r.text("normalizedKeyword"), r.number("weight"), r.int("sortOrder"))
            }
            doc.rows("provenance").forEach { p ->
                p.text("entityType"); p.text("entityId"); if (p.has("notes")) p.text("notes")
                p.rows("sources").forEach { s -> s.text("path"); s.text("locator") }
            }
        }
        val payload = CurriculumImportPayload(manifest,
            catalog.rows("courses").map { r -> CourseEntity(r.text("id"), r.text("code"), r.text("name"), r.text("shortName"), periodType(r.text("structureType")), r.text("summary"), r.bool("isActive"), r.int("sortOrder"), r.instant("createdAt"), r.instant("updatedAt")) },
            catalog.rows("academicPeriods").map { r -> AcademicPeriodEntity(r.text("id"), r.text("courseId"), r.int("periodNumber"), periodType(r.text("periodType")), r.text("name"), r.text("shortName"), r.int("sortOrder"), r.bool("isActive"), r.instant("createdAt"), r.instant("updatedAt")) },
            subjects, chapters, topics, questions, keywords)
        CurriculumImportValidator.validate(payload)
        return payload
    }

    private fun checkEnvelope(doc: JSONObject, schemaVersion: Long) {
        require(doc.integer("schemaVersion") == schemaVersion && doc.integer("contentRevision") > 0) { "Unsupported curriculum envelope" }
    }

    private fun periodType(value: String) = when (value) {
        "semester" -> AcademicPeriodType.SEMESTER
        "year" -> AcademicPeriodType.YEAR
        else -> error("Unsupported academic period type: $value")
    }

    private fun json(raw: ByteArray): JSONObject {
        require(raw.size <= 16 * 1024 * 1024) { "Curriculum file too large" }
        val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()
        validateStrictJson(text)
        return JSONObject(text)
    }

    companion object {
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

private fun JSONObject.text(key: String): String = get(key).let { require(it is String) { "Expected text: $key" }; it }
private fun JSONObject.nullableText(key: String): String? = get(key).let { if (it === JSONObject.NULL) null else { require(it is String) { "Expected nullable text: $key" }; it } }
private fun JSONObject.bool(key: String): Boolean = get(key).let { require(it is Boolean) { "Expected Boolean: $key" }; it }
private fun JSONObject.integer(key: String): Long = get(key).let { require(it is Int || it is Long) { "Expected integer: $key" }; (it as Number).toLong() }
private fun JSONObject.int(key: String): Int = integer(key).also { require(it in 0..Int.MAX_VALUE.toLong()) { "Integer out of range: $key" } }.toInt()
private fun JSONObject.number(key: String): Double = get(key).let { require(it is Number) { "Expected number: $key" }; it.toDouble() }
private fun JSONObject.instant(key: String): Instant = integer(key).also { require(it >= 0) { "Negative timestamp: $key" } }.let(Instant::ofEpochMilli)
private fun JSONObject.objectValue(key: String): JSONObject = get(key).let { require(it is JSONObject) { "Expected object: $key" }; it }
private fun JSONObject.rows(key: String): List<JSONObject> = get(key).let { value ->
    require(value is JSONArray) { "Expected array: $key" }
    (0 until value.length()).map { value.get(it).let { row -> require(row is JSONObject) { "Expected array object: $key[$it]" }; row } }
}
