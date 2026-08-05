package com.example.quranapp.data.resource

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entry-level catalog validation (§3.2.4, §13.1).
 *
 * Same path as production: JSON string -> Json.decodeFromString<ResourceCatalog>
 * (ignoreUnknownKeys) -> ResourceCatalogValidator.
 */
class ResourceCatalogValidatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun entry(id: String = "ibn_kathir", overrides: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val base: MutableMap<String, Any?> = mutableMapOf(
            "id" to id,
            "type" to "TAFSIR",
            "name" to "تفسير ابن كثير",
            "nameLatin" to "Tafsir Ibn Kathir",
            "author" to "ابن كثير",
            "license" to "Public Domain",
            "source" to "QUL",
            "edition" to "QUL tafsir/22",
            "publisher" to "QUL",
            "website" to "https://qul.tarteel.ai",
            "copyrightNotice" to "Text in the public domain",
            "version" to "1.0.0",
            "language" to "ar",
            "lastUpdated" to "2026-08-03T00:00:00Z",
            "minAppVersion" to "1.0",
            "schemaVersion" to 1,
            "downloadSizeBytes" to 1234L,
            "installedSizeBytes" to 1234L,
            "downloadUrl" to "https://cdn.example.org/resources/tafsir/ibn_kathir/1.0.0.db",
            "sha256" to "a".repeat(64)
        )
        base.putAll(overrides)
        return base
    }

    private fun catalog(vararg entries: Map<String, Any?>): ResourceCatalog {
        val body = entries.joinToString(",") { map ->
            "{" + map.entries.joinToString(",") { (k, v) ->
                val value = when (v) {
                    is String -> "\"$v\""
                    is Boolean -> v.toString()
                    else -> v.toString()
                }
                "\"$k\":$value"
            } + "}"
        }
        return json.decodeFromString("""{"schemaVersion":1,"generatedAt":"2026-08-03T00:00:00Z","resources":[$body]}""")
    }

    // ------------------------------------------------------------------

    @Test
    fun validEntryPasses() {
        val result = ResourceCatalogValidator.validate(catalog(entry()))
        assertEquals(0, result.droppedEntries)
        assertEquals(1, result.catalog.resources.size)
    }

    @Test
    fun missingShaIsDropped() {
        val result = ResourceCatalogValidator.validate(catalog(entry(overrides = mapOf("sha256" to ""))))
        assertEquals(1, result.droppedEntries)
        assertTrue(result.catalog.resources.isEmpty())
    }

    @Test
    fun missingProvenanceFieldIsDropped() {
        assertEquals(1, ResourceCatalogValidator.validate(catalog(entry(overrides = mapOf("source" to "")))).droppedEntries)
        assertEquals(1, ResourceCatalogValidator.validate(catalog(entry(overrides = mapOf("website" to "")))).droppedEntries)
        assertEquals(1, ResourceCatalogValidator.validate(catalog(entry(overrides = mapOf("copyrightNotice" to "")))).droppedEntries)
        assertEquals(1, ResourceCatalogValidator.validate(catalog(entry(overrides = mapOf("publisher" to "")))).droppedEntries)
    }

    @Test
    fun missingAuthorOrNameDropped() {
        assertEquals(1, ResourceCatalogValidator.validate(catalog(entry(overrides = mapOf("author" to "")))).droppedEntries)
        assertEquals(1, ResourceCatalogValidator.validate(catalog(entry(overrides = mapOf("name" to "")))).droppedEntries)
    }

    @Test
    fun invalidSchemaVersionDroppedButFutureSchemaKept() {
        assertEquals(1, ResourceCatalogValidator.validate(catalog(entry(overrides = mapOf("schemaVersion" to 0)))).droppedEntries)
        assertEquals(1, ResourceCatalogValidator.validate(catalog(entry(overrides = mapOf("schemaVersion" to -3)))).droppedEntries)
        // Unknown FUTURE schema: entry stays visible (Manager shows it blocked).
        val future = catalog(entry(overrides = mapOf("schemaVersion" to 2)))
        val result = ResourceCatalogValidator.validate(future)
        assertEquals(0, result.droppedEntries)
        assertEquals(2, result.catalog.resources.single().schemaVersion)
    }

    @Test
    fun invalidUrlDropped() {
        assertEquals(1, ResourceCatalogValidator.validate(catalog(entry(overrides = mapOf("downloadUrl" to "resources/tafsir/x.db")))).droppedEntries)
        assertEquals(1, ResourceCatalogValidator.validate(catalog(entry(overrides = mapOf("downloadUrl" to "")))).droppedEntries)
        assertEquals(1, ResourceCatalogValidator.validate(catalog(entry(overrides = mapOf("downloadUrl" to "ftp://cdn/tafsir.db")))).droppedEntries)
    }

    @Test
    fun duplicateIdKeepsLastVersion() {
        val result = ResourceCatalogValidator.validate(
            catalog(entry(overrides = mapOf("version" to "1.0.0")), entry(overrides = mapOf("version" to "1.1.0")))
        )
        assertEquals(1, result.droppedEntries)
        assertEquals(1, result.catalog.resources.size)
        assertEquals("1.1.0", result.catalog.resources.single().version)
    }

    @Test
    fun duplicateIdAndVersionKeepsFirst() {
        val result = ResourceCatalogValidator.validate(
            catalog(entry(overrides = mapOf("version" to "1.0.0")), entry(overrides = mapOf("version" to "1.0.0")))
        )
        assertEquals(1, result.droppedEntries)
        assertEquals(1, result.catalog.resources.size)
    }

    @Test
    fun mixedValidAndInvalidKeepsOnlyValid() {
        val result = ResourceCatalogValidator.validate(
            catalog(
                entry(id = "muyassar"),
                entry(id = "bad_sha", overrides = mapOf("sha256" to "")),
                entry(id = "bad_src", overrides = mapOf("source" to "")),
                entry(id = "duplicate", overrides = mapOf("version" to "1.0.0")),
                entry(id = "duplicate", overrides = mapOf("version" to "1.2.0"))
            )
        )
        assertEquals(3, result.droppedEntries)
        assertEquals(2, result.catalog.resources.size)
        assertEquals(setOf("muyassar", "duplicate"), result.catalog.resources.map { it.id }.toSet())
        assertEquals("1.2.0", result.catalog.resources.first { it.id == "duplicate" }.version)
    }

    @Test
    fun sameIdAcrossTypesIsNotADuplicate() {
        val result = ResourceCatalogValidator.validate(
            catalog(
                entry(id = "shared"),
                entry(id = "shared", overrides = mapOf("type" to "TRANSLATION"))
            )
        )
        assertEquals(0, result.droppedEntries)
        assertEquals(2, result.catalog.resources.size)
    }

    @Test
    fun productionCatalogFieldsParseAndExposeProvenance() {
        val raw =
            """{"schemaVersion":1,"generatedAt":"2026-08-03T00:00:00Z","resources":[
               {"id":"muyassar","type":"TAFSIR","name":"التفسير الميسر","nameLatin":"Tafsir Al-Muyassar",
                "author":"مجموعة من العلماء","license":"Free (Islamic text — King Fahd Complex)",
                "source":"api.alquran.cloud — edition ar.muyassar","edition":"ar.muyassar",
                "publisher":"King Fahd Complex for the Printing of the Holy Quran",
                "website":"https://qurancomplex.gov.sa",
                "copyrightNotice":"التفسير الميسر: إعداد لجنة من العلماء بإشراف مجمع الملك فهد",
                "version":"1.0.0","language":"ar","lastUpdated":"2026-08-03T00:00:00Z",
                "minAppVersion":"1.0","schemaVersion":1,
                "downloadSizeBytes":2912256,"installedSizeBytes":2912256,
                "downloadUrl":"https://cdn.example/resources/tafsir/muyassar/1.0.0.db",
                "sha256":"${"b".repeat(64)}","bundled":true}
            ]}"""
        val parsed = json.decodeFromString<ResourceCatalog>(raw)
        val validated = ResourceCatalogValidator.validate(parsed)
        assertEquals(0, validated.droppedEntries)
        val meta = validated.catalog.resources.single()
        assertEquals("التفسير الميسر", meta.name)
        assertEquals("King Fahd Complex for the Printing of the Holy Quran", meta.publisher)
        assertEquals("ar.muyassar", meta.edition)
        assertEquals("https://qurancomplex.gov.sa", meta.website)
        assertTrue(meta.copyrightNotice.startsWith("التفسير الميسر"))
    }

    @Test
    fun malformedTypeValueRejectsWholeCatalog() {
        // A bad enum value fails decode at the catalog level; the repository
        // then keeps the previous state (graceful, no crash).
        val raw =
            """{"schemaVersion":1,"generatedAt":"2026-08-03T00:00:00Z","resources":[
               {"id":"x","type":"NOT_A_TYPE","name":"X","author":"Y","license":"Z",
                "version":"1.0.0","language":"ar","lastUpdated":"z","minAppVersion":"1.0",
                "downloadSizeBytes":1,"downloadUrl":"https://x/y.db","sha256":"${"c".repeat(64)}"}
            ]}"""
        val result = runCatching { json.decodeFromString<ResourceCatalog>(raw) }
        assertTrue(result.isFailure)
    }
}