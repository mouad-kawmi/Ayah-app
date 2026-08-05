package com.example.quranapp.data.resource

/**
 * Per-entry catalog validation (architecture §3.2.4, §13.1).
 *
 * The whole-catalog JSON decode only guarantees structural validity. This
 * validator applies the entry-level rules on a parsed [ResourceCatalog] and
 * DROPS the invalid entries while keeping the valid remainder — the app never
 * crashes on a bad entry and never lets a malformed entry reach the UI.
 *
 * Removal rules (an entry is dropped when ANY applies):
 *  - blank id, name, author or version
 *  - incomplete provenance (§13.1: source, edition, publisher, author,
 *    license, website, copyrightNotice all required)
 *  - blank or non-http(s) downloadUrl
 *  - blank sha256
 *  - schemaVersion < 1 (an UNKNOWN higher schema stays visible but is blocked
 *    by the Manager via `schemaSupported` — it is not dropped)
 *  - duplicate          (type:id) — last occurrence wins, remote is authoritative
 *    (same semantics as the repository merge: remote replaces embedded)
 *
 * Embedded/bundled entries are NOT part of a remote catalog and are never run
 * through this validator.
 */
object ResourceCatalogValidator {

    data class ValidationResult(
        val catalog: ResourceCatalog,
        val droppedEntries: Int
    )

    fun validate(catalog: ResourceCatalog): ValidationResult {
        val valid = catalog.resources.filter(::isEntryValid)
        // Last occurrence wins per (type:id) — consistent with the repository
        // merge (remote is authoritative) and with duplicate (id, version) rows.
        val lastIndexById = LinkedHashMap<String, Int>()
        for ((index, entry) in valid.withIndex()) {
            lastIndexById["${entry.type.name}:${entry.id}"] = index
        }
        val kept = valid.filterIndexed { index, entry ->
            lastIndexById["${entry.type.name}:${entry.id}"] == index
        }
        return ValidationResult(catalog.copy(resources = kept), catalog.resources.size - kept.size)
    }

    private fun isEntryValid(entry: ResourceMeta): Boolean {
        if (entry.id.isBlank()) return false
        if (entry.name.isBlank()) return false
        if (entry.author.isBlank()) return false
        if (entry.version.isBlank()) return false
        if (entry.sha256.isBlank()) return false
        if (entry.schemaVersion < 1) return false
        // Provenance §13.1 — all 7 fields required.
        if (entry.source.isBlank() || entry.edition.isBlank() || entry.publisher.isBlank() ||
            entry.license.isBlank() || entry.website.isBlank() || entry.copyrightNotice.isBlank()
        ) return false
        // downloadUrl must be absolute http(s) for the download manager (§6.1).
        if (entry.downloadUrl.isBlank()) return false
        if (!entry.downloadUrl.startsWith("http://") && !entry.downloadUrl.startsWith("https://")) return false
        return true
    }
}