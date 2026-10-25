package com.local.notebook.ui.screens.whitelist

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap?
)

data class WhitelistUiState(
    val apps: List<InstalledApp> = emptyList(),
    val manualPackages: List<String> = emptyList(),
    val whitelist: Set<String> = emptySet(),
    val query: String = "",
    val loading: Boolean = true
)

@HiltViewModel
class WhitelistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) : ViewModel() {

    private val packageManager = context.packageManager
    private val collator = Collator.getInstance(Locale.CHINA)
    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val query = MutableStateFlow("")
    private val loading = MutableStateFlow(true)

    val state: StateFlow<WhitelistUiState> = combine(
        allApps,
        query,
        settings.whitelistPackages,
        loading
    ) { apps, search, whitelist, isLoading ->
        val keyword = search.trim()
        val filtered = apps
            .filter {
                keyword.isEmpty() ||
                    it.label.contains(keyword, ignoreCase = true) ||
                    it.packageName.contains(keyword, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<InstalledApp> { it.packageName in whitelist }
                    .thenBy(collator) { it.label }
            )
        WhitelistUiState(
            apps = filtered,
            manualPackages = whitelist
                .filter { pkg -> apps.none { it.packageName == pkg } }
                .sorted(),
            whitelist = whitelist,
            query = search,
            loading = isLoading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WhitelistUiState()
    )

    init {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { loadLauncherApps() }
            allApps.value = apps
            loading.value = false
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun togglePackage(packageName: String, enabled: Boolean) {
        viewModelScope.launch { settings.setPackageEnabled(packageName, enabled) }
    }

    fun addPackage(packageName: String) {
        viewModelScope.launch { settings.addPackage(packageName) }
    }

    private fun loadLauncherApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = runCatching { packageManager.queryIntentActivities(intent, 0) }
            .getOrDefault(emptyList())
        return activities
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                val label = runCatching {
                    packageManager.getApplicationLabel(info).toString()
                }.getOrDefault(info.packageName)
                val bitmap = runCatching {
                    packageManager.getApplicationIcon(info).toBitmap(96, 96)
                }.getOrNull()
                InstalledApp(info.packageName, label, bitmap)
            }
    }
}
