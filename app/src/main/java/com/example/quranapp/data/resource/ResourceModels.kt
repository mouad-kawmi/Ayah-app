package com.example.quranapp.data.resource

import kotlinx.serialization.Serializable

@Serializable
enum class ResourceType { TAFSIR, TRANSLATION, AUDIO, DICTIONARY, LEARNING_PACK }

@Serializable
data class ResourceMeta(
    val id: String,
    val type: ResourceType,
    val name: String,
    val nameLatin: String,
    val author: String,
    val license: String,
    val source: String = "",
    val edition: String = "",
    val publisher: String = "",
    val website: String = "",
    val copyrightNotice: String = "",
    val version: String,
    val language: String,
    val lastUpdated: String,
    val minAppVersion: String,
    val schemaVersion: Int = 1,
    val downloadSizeBytes: Long,
    val installedSizeBytes: Long = 0L,
    val downloadUrl: String,
    val sha256: String,
    val bundled: Boolean = false,
    val description: String? = null
)

@Serializable
data class ResourceCatalog(
    val schemaVersion: Int,
    val generatedAt: String,
    val resources: List<ResourceMeta>
)

enum class ResourceInstallState { NOT_INSTALLED, DOWNLOADING, INSTALLED, ERROR }

data class DownloadProgress(
    val resourceKey: String,
    val state: ResourceInstallState,
    val progress: Float,
    val bytesDone: Long,
    val bytesTotal: Long,
    val errorMessage: String? = null
)

data class ResourceListItem(
    val meta: ResourceMeta,
    val state: ResourceInstallState,
    val progress: Float = 0f,
    val installedVersion: String? = null,
    val installedSizeBytes: Long = 0L,
    val isSelected: Boolean = false,
    val updateAvailable: Boolean = false,
    val appUpdateRequired: Boolean = false,
    val schemaSupported: Boolean = true,
    val errorMessage: String? = null
)

@Serializable
data class ResourceIndex(
    val schemaVersion: Int = 1,
    val resources: Map<String, ResourceIndexEntry> = emptyMap()
)

@Serializable
data class ResourceIndexEntry(
    val type: ResourceType,
    val version: String,
    val installedAt: Long,
    val sizeBytes: Long,
    val sha256: String,
    val schemaVersion: Int = 1,
    val bundled: Boolean = false
)

@Serializable
data class ResumeMeta(
    val type: ResourceType,
    val resourceId: String,
    val version: String,
    val url: String,
    val bytesDone: Long,
    val bytesTotal: Long
)
