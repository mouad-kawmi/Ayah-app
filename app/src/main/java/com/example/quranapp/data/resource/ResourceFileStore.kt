package com.example.quranapp.data.resource

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * Generic on-device storage for downloaded resources (architecture §3.2.1).
 *
 * Layout:
 *   files/resources/<type>/<id>.db            installed resource file
 *   files/resources/<type>/.downloads/<id>.db.part      partial download (resumed via Range)
 *   files/resources/<type>/.downloads/<id>.db.part.meta resume metadata (JSON ResumeMeta)
 *
 * The layer is content-agnostic: it stores and removes opaque files per resource
 * key "type:id". It never parses resource content.
 */
class ResourceFileStore(private val appContext: Context) {

    companion object {
        const val RESOURCES_DIR_NAME = "resources"
        const val DOWNLOADS_DIR_NAME = ".downloads"
        const val PART_EXTENSION = ".part"
        const val PART_META_EXTENSION = ".part.meta"
        const val SPACE_MARGIN_BYTES = 10L * 1024L * 1024L

        @Volatile
        private var INSTANCE: ResourceFileStore? = null

        fun getInstance(context: Context): ResourceFileStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ResourceFileStore(context.applicationContext).also { INSTANCE = it }
            }
    }

    fun resourcesRoot(): File = File(appContext.filesDir, RESOURCES_DIR_NAME)

    fun typeDir(type: ResourceType): File = File(resourcesRoot(), type.name.lowercase())

    fun downloadsDir(type: ResourceType): File = File(typeDir(type), DOWNLOADS_DIR_NAME)

    fun resourceFile(type: ResourceType, resourceId: String): File =
        File(typeDir(type), "$resourceId.db")

    fun partFile(type: ResourceType, resourceId: String): File =
        File(downloadsDir(type), "$resourceId.db$PART_EXTENSION")

    fun partMetaFile(type: ResourceType, resourceId: String): File =
        File(downloadsDir(type), "$resourceId.db$PART_META_EXTENSION")

    /** Deletes every file owned by this resource (installed, partial, resume metadata). */
    fun deleteResourceFiles(type: ResourceType, resourceId: String): Boolean {
        val deleted = resourceFile(type, resourceId).delete()
        val partDeleted = partFile(type, resourceId).delete()
        val metaDeleted = partMetaFile(type, resourceId).delete()
        return deleted || partDeleted || metaDeleted
    }

    fun resourceExists(type: ResourceType, resourceId: String): Boolean =
        resourceFile(type, resourceId).isFile

    /**
     * Free space available for new downloads (bytes), with the safety margin
     * subtracted. Used by the download manager before starting a transfer.
     */
    fun availableSpaceWithMargin(): Long {
        val free = freeBytes()
        return (free - SPACE_MARGIN_BYTES).coerceAtLeast(0L)
    }

    fun freeBytes(): Long {
        return try {
            val path = appContext.filesDir?.absolutePath ?: return 0L
            val stat = StatFs(File(path, RESOURCES_DIR_NAME).parentFile?.absolutePath ?: path)
            stat.availableBytes
        } catch (e: Exception) {
            // Fall back to the environment-level stat if the target is unavailable.
            runCatching { StatFs(Environment.getDataDirectory().absolutePath).availableBytes }
                .getOrDefault(0L)
        }
    }
}
