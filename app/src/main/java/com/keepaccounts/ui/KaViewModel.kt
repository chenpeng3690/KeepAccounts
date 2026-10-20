package com.keepaccounts.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaccounts.KeepAccountsApp
import com.keepaccounts.core.AppContainer

/**
 * 从 Application 取依赖容器。
 * 没有 Hilt，ViewModel 的构造参数只能靠工厂手工传，这里统一收口。
 */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) {
        (context.applicationContext as KeepAccountsApp).container
    }
}

@Composable
fun <VM : ViewModel> kaViewModel(build: (AppContainer) -> VM): VM {
    val container = rememberAppContainer()
    val provider = remember(container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = build(container) as T
        }
    }
    return viewModel(factory = provider)
}
