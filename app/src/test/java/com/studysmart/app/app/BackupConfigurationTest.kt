package com.studysmart.app.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element

/** Static configuration guard, not proof of platform backup/restore behavior. */
class BackupConfigurationTest {
    @Test fun manifestUsesTheReviewedBackupConfigurations() {
        val app = xml("AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("true", app.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(android, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(android, "dataExtractionRules"))
    }

    @Test fun legacyBackupAllowsOnlyDataStorePreferencesNotRoomOrOtherFiles() {
        val root = xml("res/xml/backup_rules.xml")
        assertEquals("full-backup-content", root.tagName)
        assertPreferencesAllowlist(root)
    }

    @Test fun cloudAndDeviceTransferBothRestrictBackupToPreferences() {
        val root = xml("res/xml/data_extraction_rules.xml")
        assertEquals("data-extraction-rules", root.tagName)
        assertEquals(setOf("cloud-backup", "device-transfer"), children(root).map { it.tagName }.toSet())
        assertEquals(2, children(root).size)
        children(root).forEach(::assertPreferencesAllowlist)
    }

    private fun assertPreferencesAllowlist(section: Element) {
        // An explicit include switches Android backup to an allowlist. Room lives
        // in the database domain; widening this list requires privacy review.
        val rules = children(section)
        assertEquals(1, rules.size)
        val include = rules.single()
        assertEquals("include", include.tagName)
        assertEquals("file", include.getAttribute("domain"))
        assertEquals("datastore/", include.getAttribute("path"))
    }

    private fun children(element: Element): List<Element> = buildList {
        for (index in 0 until element.childNodes.length) {
            val child = element.childNodes.item(index)
            if (child is Element) add(child)
        }
    }

    private fun xml(relative: String): Element {
        val source = File("src/main", relative)
        check(source.isFile) { "Missing configuration: ${source.absolutePath}" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        return factory.newDocumentBuilder().parse(source).documentElement.also { assertNotNull(it) }
    }
}
