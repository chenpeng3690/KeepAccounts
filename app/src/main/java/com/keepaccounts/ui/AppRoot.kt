package com.keepaccounts.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.keepaccounts.core.AppContainer
import com.keepaccounts.ui.component.TabIcon
import com.keepaccounts.ui.screen.PendingScreen
import com.keepaccounts.ui.screen.StatsScreen
import com.keepaccounts.ui.screen.assets.AccountDetailScreen
import com.keepaccounts.ui.screen.assets.AssetsScreen
import com.keepaccounts.ui.screen.category.CategoryManageScreen
import com.keepaccounts.ui.screen.entry.EntryScreen
import com.keepaccounts.ui.screen.ledger.LedgerScreen
import com.keepaccounts.ui.screen.search.SearchScreen
import com.keepaccounts.ui.screen.settings.SettingsScreen
import com.keepaccounts.ui.screen.trash.TrashScreen

/**
 * 底部导航固定 5 个，顺序不能改：账单 / 资产 / 待确认 / 统计 / 设置。
 *
 * 「待确认」放在中间偏左是刻意的——它是校对入口，比统计更高频，要落在拇指自然位置上。
 * 角标只在有待确认时出现，0 条不显示。
 */
enum class KaTab(
    val route: String,
    val label: String
) {
    Ledger("ledger", "账单"),
    Assets("assets", "资产"),
    Pending("pending", "待确认"),
    Stats("stats", "统计"),
    Settings("settings", "设置")
}

/** 二级页面的路由，不进底部导航 */
object KaRoute {
    const val ACCOUNT_DETAIL = "account/{accountId}"
    const val ENTRY = "entry?txnId={txnId}"
    const val SEARCH = "search"
    const val CATEGORIES = "categories"
    const val TRASH = "trash"

    fun accountDetail(id: Long) = "account/$id"

    /** txnId 传 null 表示新建 */
    fun entry(txnId: Long? = null) = "entry?txnId=${txnId ?: -1L}"
}

@Composable
fun AppRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: KaTab.Ledger.route

    val pendingCount by container.txnRepo.observePendingCount()
        .collectAsStateWithLifecycle(initialValue = 0)

    // 二级页面不显示底部导航，否则详情页会像"还停在一级"
    val showBottomBar = KaTab.entries.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
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
                                                    text = if (pendingCount > 99) "99+"
                                                    else pendingCount.toString(),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    TabIcon(tab = tab, tint = LocalContentColor.current)
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = KaTab.Ledger.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(KaTab.Ledger.route) {
                LedgerScreen(
                    onOpenEntry = { navController.navigate(KaRoute.entry()) },
                    onOpenTxn = { id -> navController.navigate(KaRoute.entry(id)) },
                    onOpenSearch = { navController.navigate(KaRoute.SEARCH) }
                )
            }
            composable(KaTab.Assets.route) {
                AssetsScreen(
                    onOpenAccount = { id -> navController.navigate(KaRoute.accountDetail(id)) }
                )
            }
            composable(KaTab.Pending.route) { PendingScreen(container) }
            composable(KaTab.Stats.route) { StatsScreen(container) }
            composable(KaTab.Settings.route) {
                SettingsScreen(onOpenTrash = { navController.navigate(KaRoute.TRASH) })
            }

            composable(
                route = KaRoute.ENTRY,
                arguments = listOf(
                    navArgument("txnId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { entry ->
                val txnId = entry.arguments?.getLong("txnId") ?: -1L
                EntryScreen(
                    txnId = txnId.takeIf { it > 0 },
                    onClose = { navController.popBackStack() },
                    onManageCategories = { navController.navigate(KaRoute.CATEGORIES) }
                )
            }

            composable(KaRoute.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTxn = { id -> navController.navigate(KaRoute.entry(id)) }
                )
            }

            composable(KaRoute.CATEGORIES) {
                CategoryManageScreen(onBack = { navController.popBackStack() })
            }

            composable(KaRoute.TRASH) {
                TrashScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = KaRoute.ACCOUNT_DETAIL,
                arguments = listOf(navArgument("accountId") { type = NavType.LongType })
            ) { entry ->
                val accountId = entry.arguments?.getLong("accountId") ?: 0L
                AccountDetailScreen(
                    accountId = accountId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
