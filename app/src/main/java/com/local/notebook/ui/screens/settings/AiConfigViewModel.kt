package com.local.notebook.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.ai.AiClient
import com.local.notebook.data.ai.AiKeyStore
import com.local.notebook.data.repository.SettingsRepository
import com.local.notebook.domain.ai.AiCallResult
import com.local.notebook.domain.ai.AiConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiConfigUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-chat",
    val apiKey: String = "",
    val confidence: String = "70",
    val busy: Boolean = false,
    val message: SettingsMessage? = null
)

@HiltViewModel
class AiConfigViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val keyStore: AiKeyStore,
    private val client: AiClient
) : ViewModel() {

    private val _state = MutableStateFlow(AiConfigUiState())
    val state: StateFlow<AiConfigUiState> = _state

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = false,
                    enabled = settings.aiEnabled.first(),
                    baseUrl = settings.aiBaseUrl.first(),
                    model = settings.aiModel.first(),
                    apiKey = keyStore.load().orEmpty(),
                    confidence = settings.aiConfidencePercent.first().toString()
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.update {
            it.copy(
                enabled = enabled,
                message = if (enabled && it.apiKey.isBlank()) {
                    SettingsMessage("已开启 AI，但还没有填写 API Key", error = true)
                } else {
                    it.message
                }
            )
        }
        viewModelScope.launch { settings.setAiEnabled(enabled) }
    }

    fun setBaseUrl(value: String) {
        _state.update { it.copy(baseUrl = value) }
    }

    fun setModel(value: String) {
        _state.update { it.copy(model = value) }
    }

    fun setApiKey(value: String) {
        _state.update { it.copy(apiKey = value) }
    }

    fun setConfidence(value: String) {
        _state.update { it.copy(confidence = value.filter { ch -> ch.isDigit() }.take(3)) }
    }

    fun save() {
        val config = persist()
        _state.update {
            it.copy(
                message = if (config.apiKey.isBlank()) {
                    SettingsMessage("请先填写 API Key", error = true)
                } else {
                    SettingsMessage("配置已保存")
                }
            )
        }
    }

    fun testConnection() {
        val config = persist()
        if (config.apiKey.isBlank()) {
            _state.update { it.copy(message = SettingsMessage("请先填写 API Key", error = true)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = client.test(config)
            _state.update {
                it.copy(
                    busy = false,
                    message = when (result) {
                        is AiCallResult.Success -> SettingsMessage("连接成功，AI 返回了有效结果")
                        is AiCallResult.Failure -> SettingsMessage("连接失败：${result.message}", error = true)
                    }
                )
            }
        }
    }

    fun clearApiKey() {
        keyStore.clear()
        _state.update { it.copy(apiKey = "", message = SettingsMessage("已清除 API Key")) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun persist(): AiConfig {
        val current = _state.value
        val baseUrl = current.baseUrl.trim().ifBlank { "https://api.deepseek.com" }
        val model = current.model.trim().ifBlank { "deepseek-chat" }
        val confidence = current.confidence.toIntOrNull()?.coerceIn(0, 100) ?: 70
        keyStore.save(current.apiKey)
        viewModelScope.launch {
            settings.setAiConfig(baseUrl, model, confidence)
        }
        _state.update {
            it.copy(
                baseUrl = baseUrl,
                model = model,
                confidence = confidence.toString()
            )
        }
        return AiConfig(
            baseUrl = baseUrl,
            model = model,
            apiKey = current.apiKey.trim(),
            confidencePercent = confidence
        )
    }
}
