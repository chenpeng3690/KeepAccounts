package com.keepaccounts.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.keepaccounts.core.AppContainer
import com.keepaccounts.ui.screen.AssetsScreen
import com.keepaccounts.ui.screen.LedgerScreen
import com.keepaccounts.ui.screen.PendingScreen
import com.keepaccounts.ui.screen.SettingsScreen
import com.keepaccounts.ui.screen.StatsScreen

/**
 * 底部导航固定 5 个，顺序不能改：账单 / 资产 / 待确认 / 统计 / 设置。
 *
 * 「待确认」放在中间偏左是刻意的——它是校对入口，比统计更高频，要落在拇指自然位置上。
 * 角标只在有待确认时出现，0 条不显示。
 */
enum class KaTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Ledger("ledger", "账单", Icons.Outlined.ReceiptLong),
    Assets("assets", "资产", Icons.Outlined.AccountBalanceWallet),
    Pending("pending", "待确认", Icons.Outlined.NotificationsActive),
    Stats("stats", "统计", Icons.Outlined.InsertChart),
    Settings("settings", "设置", Icons.Outlined.Settings)
}

@Composable
fun AppRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: KaTab.Ledger.route

    val pendingCount by container.txnRepo.observePendingCount()
        .collectAsStateWithLifecycle(initialValue = 0)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                KaTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            val showBadge = tab == KaTab.Pending && pendingCount > 0
                            BadgedBox(
                                badge = {
                                    if (showBadge) {
                                        Badge {
                                            Text(
                                                text = if (pendingCount > 99) "99+" else pendingCount.toString(),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(imageVector = tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = KaTab.Ledger.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(KaTab.Ledger.route) { LedgerScreen(container) }
            composable(KaTab.Assets.route) { AssetsScreen(container) }
            composable(KaTab.Pending.route) { PendingScreen(container) }
            composable(KaTab.Stats.route) { StatsScreen(container) }
            composable(KaTab.Settings.route) { SettingsScreen(container) }
        }
    }
}
