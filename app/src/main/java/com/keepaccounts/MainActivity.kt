package com.keepaccounts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.keepaccounts.ui.AppRoot
import com.keepaccounts.ui.theme.KeepAccountsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KeepAccountsTheme {
                KeepAccountsAppRoot()
            }
        }
    }
}

@Composable
private fun KeepAccountsAppRoot() {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as KeepAccountsApp).container
    }
    AppRoot(container = container)
}
