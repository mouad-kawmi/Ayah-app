package com.example.quranapp.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quranapp.data.resource.ResourceCatalogRepository
import com.example.quranapp.data.resource.ResourceIndexEntry
import com.example.quranapp.data.resource.ResourceIndexStore
import com.example.quranapp.data.resource.ResourceMeta
import com.example.quranapp.data.resource.ResourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SourceLicenseItem(
    val meta: ResourceMeta,
    val installedVersion: String?,
    val installed: Boolean
)

data class SourcesLicensesUiState(
    val items: List<SourceLicenseItem> = emptyList(),
    val loading: Boolean = true
)

/**
 * Sources & licenses screen (§13.4): one entry per tafsir (bundled and
 * downloadable) with name, author, source, publisher, license, website and
 * version, fed by the embedded catalog + disk cache + installation index.
 * Never performs network work — the screen is fully usable offline.
 */
class SourcesLicensesViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val catalogRepo = ResourceCatalogRepository.getInstance(appContext)
    private val indexStore = ResourceIndexStore.getInstance(appContext)

    private val _uiState = MutableStateFlow(SourcesLicensesUiState())
    val uiState: StateFlow<SourcesLicensesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val catalog = catalogRepo.getCatalog()
            val index = indexStore.allEntries()
            _uiState.value = SourcesLicensesUiState(
                items = buildItems(catalog.resources, index),
                loading = false
            )
        }
    }

    private fun buildItems(
        resources: List<ResourceMeta>,
        index: Map<String, ResourceIndexEntry>
    ): List<SourceLicenseItem> {
        return resources
            .filter { it.type == ResourceType.TAFSIR }
            .map { meta ->
                val key = ResourceIndexStore.getInstance(appContext).resourceKey(meta.type, meta.id)
                val indexEntry = index[key]
                SourceLicenseItem(
                    meta = meta,
                    installedVersion = indexEntry?.version ?: if (meta.bundled) meta.version else null,
                    installed = indexEntry != null || meta.bundled
                )
            }
            .sortedBy { it.meta.nameLatin }
    }
}
