package com.studysmart.app.core.data.importer

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test

class AnswerFormatImportContractTest {
    private fun altered(property: String, change: (String, JSONObject) -> Unit) {
        val root = File(requireNotNull(System.getProperty(property)))
        val manifest = JSONObject(File(root, "bundle-manifest.json").readText())
        val rows = manifest.getJSONArray("files")
        val data = linkedMapOf<String, ByteArray>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val path = row.getString("path")
            val doc = JSONObject(File(root, checkedBundlePath(path)).readText())
            change(path, doc)
            data[path] = doc.toString().toByteArray(Charsets.UTF_8)
            row.put("sha256", CurriculumJsonParser.sha256(data.getValue(path)))
        }
        val digestRows = data.keys.sorted().joinToString("") {
            "$it\t${CurriculumJsonParser.sha256(data.getValue(it))}\n"
        }
        manifest.put("bundleDigest", CurriculumJsonParser.sha256(digestRows.toByteArray(Charsets.UTF_8)))
        data["bundle-manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
        CurriculumJsonParser().read { data.getValue(it) }
    }

    @Test fun versionOneForbidsAnswerFormatEvenWhenPlainText() {
        assertThrows(IllegalArgumentException::class.java) {
            altered("curriculum.reviewed") { path, doc ->
                if (path.startsWith("subjects/")) doc.getJSONArray("questions").getJSONObject(0).put("answerFormat", "plain_text")
            }
        }
    }

    @Test fun versionTwoRequiresRecognizedFormatAndValidGrammar() {
        listOf<String?>(null, "HTML", "unknown", "").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                altered("curriculum.formatted") { path, doc ->
                    if (path.startsWith("subjects/")) {
                        val question = doc.getJSONArray("questions").getJSONObject(0)
                        if (value == null) question.remove("answerFormat") else question.put("answerFormat", value)
                    }
                }
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            altered("curriculum.formatted") { path, doc ->
                if (path.startsWith("subjects/")) doc.getJSONArray("questions").getJSONObject(0).put("answer", "<p onclick=\"bad\">x</p>")
            }
        }
    }

    @Test fun manifestCatalogAndSubjectVersionsMustMatch() {
        listOf("curriculum.json", "subjects/").forEach { changed ->
            assertThrows(IllegalArgumentException::class.java) {
                altered("curriculum.formatted") { path, doc ->
                    if (path == changed || path.startsWith(changed)) doc.put("schemaVersion", 1)
                }
            }
        }
    }
}
