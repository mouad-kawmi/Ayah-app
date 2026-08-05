package com.example.quranapp.presentation.tafsir

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quranapp.core.debug.DebugLogger
import com.example.quranapp.core.debug.LogCategory
import com.example.quranapp.data.tafsir.TafsirDatabaseVerifier
import com.example.quranapp.data.tafsir.TafsirResourceMeta
import com.example.quranapp.data.tafsir.TafsirSelectionStore
import com.example.quranapp.data.resource.ResourceCatalog
import com.example.quranapp.data.resource.ResourceCatalogRepository
import com.example.quranapp.data.resource.ResourceDownloadManager
import com.example.quranapp.data.resource.ResourceIndexStore
import com.example.quranapp.data.resource.ResourceIndexEntry
import com.example.quranapp.data.resource.ResourceInstallState
import com.example.quranapp.data.resource.ResourceListItem
import com.example.quranapp.data.resource.ResourceMeta
import com.example.quranapp.data.resource.ResourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TafsirManagerUiState(
    val items: List<ResourceListItem> = emptyList(),
    val offline: Boolean = false,
    val refreshing: Boolean = false,
    val loading: Boolean = true
)

class TafsirManagerViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val catalogRepo = ResourceCatalogRepository.getInstance(appContext)
    private val indexStore = ResourceIndexStore.getInstance(appContext)
    private val downloadManager = ResourceDownloadManager.getInstance(appContext)
    private val selectionStore = TafsirSelectionStore(appContext)

    private val catalog = MutableStateFlow(ResourceCatalog(schemaVersion = 1, generatedAt = "", resources = emptyList()))
    private val index = MutableStateFlow<Map<String, ResourceIndexEntry>>(emptyMap())
    private val selectedId = MutableStateFlow(selectionStore.selectedTafsirId())
    private val offline = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val loading = MutableStateFlow(true)

    private val _uiState = MutableStateFlow(TafsirManagerUiState())
    val uiState: StateFlow<TafsirManagerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadManager.reconcile()
            catalog.value = catalogRepo.getCatalog()
            index.value = indexStore.allEntries()
            loading.value = false
            refresh()
        }
        viewModelScope.launch {
            combine(
                combine(catalog, index, downloadManager.progress, selectedId) { cat, idx, progress, sel ->
                    buildItems(cat, idx, progress.resourceKey, progress.state, progress.progress, progress.errorMessage, sel)
                },
                offline,
                refreshing,
                loading
            ) { items, offlineNow, refreshingNow, loadingNow ->
                TafsirManagerUiState(
                    items = items,
                    offline = offlineNow,
                    refreshing = refreshingNow,
                    loading = loadingNow
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
        // Install completion / failure changes the on-disk truth: reload the
        // index so the list reflects the new state without a manual refresh.
        viewModelScope.launch {
            downloadManager.progress.collect { p ->
                if (p.state == ResourceInstallState.INSTALLED || p.state == ResourceInstallState.ERROR) {
                    index.value = indexStore.allEntries()
                    selectedId.value = selectionStore.selectedTafsirId()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            val result = catalogRepo.refresh()
            catalog.value = result.catalog
            index.value = indexStore.allEntries()
            selectedId.value = selectionStore.selectedTafsirId()
            offline.value = !result.success && catalogRepo.remoteEnabled()
            refreshing.value = false
        }
    }

    fun download(meta: ResourceMeta) {
        if (meta.type == ResourceType.TAFSIR) {
            downloadManager.download(meta) { file ->
                TafsirDatabaseVerifier.verify(file, TafsirResourceMeta(meta.id, meta.sha256))
            }
        } else {
            downloadManager.download(meta)
        }
        selectedId.value = selectionStore.selectedTafsirId()
    }

    fun cancel(meta: ResourceMeta) = downloadManager.cancel(meta)

    fun delete(meta: ResourceMeta) {
        viewModelScope.launch {
            downloadManager.delete(meta)
            index.value = indexStore.allEntries()
            selectedId.value = selectionStore.selectedTafsirId()
        }
    }

    fun select(meta: ResourceMeta) {
        if (meta.type != ResourceType.TAFSIR) return
        selectionStore.selectTafsir(meta.id)
        selectedId.value = selectionStore.selectedTafsirId()
        DebugLogger.info(LogCategory.TAFSIR, "Tafsir selected: ${meta.id}")
    }

    private fun buildItems(
        cat: ResourceCatalog,
        idx: Map<String, ResourceIndexEntry>,
        progressKey: String,
        progressState: ResourceInstallState,
        progressValue: Float,
        progressError: String?,
        sel: String
    ): List<ResourceListItem> {
        return cat.resources
            .filter { it.type == ResourceType.TAFSIR }
            .map { meta ->
                val key = ResourceIndexStore.getInstance(appContext).resourceKey(meta.type, meta.id)
                val indexEntry = idx[key]
                val isActiveProgress = progressKey == key

                when {
                    meta.bundled -> ResourceListItem(
                        meta = meta,
                        state = ResourceInstallState.INSTALLED,
                        installedVersion = meta.version,
                        installedSizeBytes = meta.installedSizeBytes,
                        isSelected = sel == meta.id,
                        appUpdateRequired = !supportsMinVersion(meta.minAppVersion),
                        schemaSupported = meta.schemaVersion <= ResourceDownloadManager.SUPPORTED_SCHEMA_VERSION
                    )
                    isActiveProgress && progressState == ResourceInstallState.DOWNLOADING -> ResourceListItem(
                        meta = meta,
                        state = ResourceInstallState.DOWNLOADING,
                        progress = progressValue,
                        appUpdateRequired = !supportsMinVersion(meta.minAppVersion),
                        schemaSupported = meta.schemaVersion <= ResourceDownloadManager.SUPPORTED_SCHEMA_VERSION
                    )
                    isActiveProgress && progressState == ResourceInstallState.ERROR -> ResourceListItem(
                        meta = meta,
                        state = ResourceInstallState.ERROR,
                        progress = progressValue,
                        errorMessage = progressError,
                        appUpdateRequired = !supportsMinVersion(meta.minAppVersion),
                        schemaSupported = meta.schemaVersion <= ResourceDownloadManager.SUPPORTED_SCHEMA_VERSION
                    )
                    indexEntry != null -> ResourceListItem(
                        meta = meta,
                        state = ResourceInstallState.INSTALLED,
                        installedVersion = indexEntry.version,
                        installedSizeBytes = indexEntry.sizeBytes,
                        isSelected = sel == meta.id,
                        updateAvailable = compareVersions(meta.version, indexEntry.version) > 0,
                        appUpdateRequired = !supportsMinVersion(meta.minAppVersion),
                        schemaSupported = meta.schemaVersion <= ResourceDownloadManager.SUPPORTED_SCHEMA_VERSION
                    )
                    else -> ResourceListItem(
                        meta = meta,
                        state = ResourceInstallState.NOT_INSTALLED,
                        appUpdateRequired = !supportsMinVersion(meta.minAppVersion),
                        schemaSupported = meta.schemaVersion <= ResourceDownloadManager.SUPPORTED_SCHEMA_VERSION
                    )
                }
            }
            .sortedBy { it.meta.nameLatin }
    }

    private fun supportsMinVersion(minAppVersion: String): Boolean {
        if (minAppVersion.isBlank()) return true
        return compareVersions(com.example.quranapp.BuildConfig.VERSION_NAME, minAppVersion) >= 0
    }

    private fun compareVersions(a: String, b: String): Int {
        val ap = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bp = b.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(ap.size, bp.size)
        for (i in 0 until len) {
            val av = ap.getOrElse(i) { 0 }
            val bv = bp.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }
}
