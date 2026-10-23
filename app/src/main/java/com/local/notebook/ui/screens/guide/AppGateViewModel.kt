package com.local.notebook.ui.screens.guide

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.notebook.data.repository.SettingsRepository
import com.local.notebook.service.NotificationAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppGateState(
    val loading: Boolean = true,
    val showGuide: Boolean = false,
    val requestNotificationPermission: Boolean = false
)

@HiltViewModel
class AppGateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AppGateState())
    val state: StateFlow<AppGateState> = _state

    val themeMode: StateFlow<String> = settings.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "SYSTEM"
    )

    init {
        viewModelScope.launch {
            val enabled = NotificationAccess.isEnabled(context)
            val shown = settings.guideShown.first()
            val showGuide = !enabled && !shown
            _state.value = AppGateState(loading = false, showGuide = showGuide)
            if (!showGuide) evaluatePermissionRequest()
        }
    }

    fun dismissGuide() {
        viewModelScope.launch {
            settings.setGuideShown(true)
            _state.update { it.copy(showGuide = false) }
            evaluatePermissionRequest()
        }
    }

    fun markPermissionAsked() {
        viewModelScope.launch {
            settings.setFeedbackPermissionAsked(true)
            _state.update { it.copy(requestNotificationPermission = false) }
        }
    }

    private suspend fun evaluatePermissionRequest() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        if (settings.feedbackMode.first() == "NONE") return
        if (settings.feedbackPermissionAsked.first()) return
        _state.update { it.copy(requestNotificationPermission = true) }
    }
}
