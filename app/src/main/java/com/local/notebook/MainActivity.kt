package com.local.notebook

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.ui.MainScaffold
import com.local.notebook.ui.screens.guide.AppGateViewModel
import com.local.notebook.ui.screens.guide.NotificationGuideScreen
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.theme.NotiLedgerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val gateViewModel: AppGateViewModel = hiltViewModel()
            val gate by gateViewModel.state.collectAsStateWithLifecycle()
            val themeMode by gateViewModel.themeMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> systemDark
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
            val view = LocalView.current
            SideEffect {
                val activity = view.context as? Activity
                if (activity != null) {
                    WindowCompat.getInsetsController(activity.window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }

            LaunchedEffect(gate.requestNotificationPermission) {
                if (gate.requestNotificationPermission &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ) {
                    gateViewModel.markPermissionAsked()
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            NotiLedgerTheme(darkTheme = darkTheme) {
                val palette = LedgerTheme.palette
                when {
                    gate.loading -> Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = palette.bg
                    ) {
                        Box(Modifier.fillMaxSize())
                    }
                    gate.showGuide -> NotificationGuideScreen(
                        onEnter = gateViewModel::dismissGuide,
                        onSkip = gateViewModel::dismissGuide
                    )
                    else -> MainScaffold()
                }
            }
        }
    }
}
