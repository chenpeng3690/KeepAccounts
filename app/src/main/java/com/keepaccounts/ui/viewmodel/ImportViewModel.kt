package com.keepaccounts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keepaccounts.core.AppContainer
import com.keepaccounts.data.import.CsvImporter
import com.keepaccounts.data.repo.ImportReport
import com.keepaccounts.data.repo.ImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportUiState(
    val source: String? = null,
    val rowCount: Int = 0,
    val skippedStatus: Int = 0,
    val skippedNeutral: Int = 0,
    val report: ImportReport? = null,
    val busy: Boolean = false,
    val error: String? = null
)

class ImportViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = ImportRepository(container.db, container.txnRepo)
    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var pendingBytes: ByteArray? = null

    /** 解析预览：识别来源与行数，不写库 */
    fun load(bytes: ByteArray) {
        pendingBytes = bytes
        val parsed = CsvImporter.parse(bytes)
        if (parsed.source == "unknown") {
            _state.update { it.copy(source = null, error = "没识别出这是微信还是支付宝的账单，请确认表头包含「交易时间」和「金额」") }
        } else {
            _state.update {
                it.copy(
                    source = parsed.source,
                    rowCount = parsed.rows.size,
                    skippedStatus = parsed.skippedStatus,
                    skippedNeutral = parsed.skippedNeutral,
                    report = null,
                    error = null
                )
            }
        }
    }

    fun doImport() {
        val bytes = pendingBytes ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val report = withContext(Dispatchers.IO) { repo.import(bytes) }
            _state.update { it.copy(busy = false, report = report) }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
