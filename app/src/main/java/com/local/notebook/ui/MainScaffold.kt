package com.local.notebook.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.local.notebook.R
import com.local.notebook.service.feedback.FeedbackKind
import com.local.notebook.ui.screens.AssetsScreen
import com.local.notebook.ui.screens.BillsScreen
import com.local.notebook.ui.screens.PendingScreen
import com.local.notebook.ui.screens.SettingsScreen
import com.local.notebook.ui.screens.StatsScreen
import com.local.notebook.ui.screens.account.AccountDetailScreen
import com.local.notebook.ui.screens.account.AccountEditScreen
import com.local.notebook.ui.screens.add.AddTransactionScreen
import com.local.notebook.ui.screens.detail.TxnEditScreen
import com.local.notebook.ui.screens.feedback.FeedbackViewModel
import com.local.notebook.ui.screens.logs.ParseLogScreen
import com.local.notebook.ui.screens.pending.PendingBadgeViewModel
import com.local.notebook.ui.screens.rulewizard.RuleWizardScreen
import com.local.notebook.ui.screens.rules.RuleListScreen
import com.local.notebook.ui.screens.settings.AutoBackupScreen
import com.local.notebook.ui.screens.trash.TrashScreen
import com.local.notebook.ui.screens.whitelist.WhitelistScreen
import com.local.notebook.ui.theme.LedgerTheme

private data class MainTab(
    val route: String,
    val label: String,
    @DrawableRes val icon: Int
)

private val mainTabs = listOf(
    MainTab("bills", "账单", R.drawable.ic_tab_bills),
    MainTab("assets", "资产", R.drawable.ic_tab_assets),
    MainTab("pending", "待确认", R.drawable.ic_tab_pending),
    MainTab("stats", "统计", R.drawable.ic_tab_stats),
    MainTab("settings", "设置", R.drawable.ic_tab_settings)
)

@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val p = LedgerTheme.palette
    val snackbarHostState = remember { SnackbarHostState() }
    val badgeViewModel: PendingBadgeViewModel = hiltViewModel()
    val pendingCount by badgeViewModel.count.collectAsStateWithLifecycle()
    val feedbackViewModel: FeedbackViewModel = hiltViewModel()

    LaunchedEffect(feedbackViewModel) {
        feedbackViewModel.events.collect { event ->
            val actionLabel = if (event.kind == FeedbackKind.AUTO) "撤销" else "去确认"
            val result = snackbarHostState.showSnackbar(
                message = event.title,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (event.kind == FeedbackKind.AUTO) {
                    event.txnId?.let(feedbackViewModel::undo)
                } else {
                    navController.navigate("pending")
                }
            }
        }
    }

    Scaffold(
        containerColor = p.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (mainTabs.any { it.route == currentRoute }) {
                LedgerBottomBar(
                    currentRoute = currentRoute,
                    pendingCount = pendingCount,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "bills",
            modifier = Modifier.padding(padding)
        ) {
            composable(
                "bills",
                deepLinks = listOf(navDeepLink { uriPattern = "notiledger://bills" })
            ) {
                BillsScreen(
                    onAdd = { navController.navigate("add") },
                    onOpenTxn = { id -> navController.navigate("txn/$id") },
                    snackbarHostState = snackbarHostState
                )
            }
            composable(
                "assets",
                deepLinks = listOf(navDeepLink { uriPattern = "notiledger://assets" })
            ) {
                AssetsScreen(
                    onAddAccount = { navController.navigate("account-add") },
                    onOpenAccount = { id -> navController.navigate("account/$id") }
                )
            }
            composable(
                "pending",
                deepLinks = listOf(navDeepLink { uriPattern = "notiledger://pending" })
            ) {
                PendingScreen(
                    onOpenLogs = { navController.navigate("logs") },
                    onOpenRules = { navController.navigate("rules") },
                    onEditPending = { id -> navController.navigate("txn/$id?confirm=true") },
                    snackbarHostState = snackbarHostState
                )
            }
            composable(
                "stats",
                deepLinks = listOf(navDeepLink { uriPattern = "notiledger://stats" })
            ) { StatsScreen() }
            composable(
                "settings",
                deepLinks = listOf(navDeepLink { uriPattern = "notiledger://settings" })
            ) {
                SettingsScreen(
                    onOpenTrash = { navController.navigate("trash") },
                    onOpenWhitelist = { navController.navigate("whitelist") },
                    onOpenRules = { navController.navigate("rules") },
                    onOpenAutoBackup = { navController.navigate("auto-backup") }
                )
            }
            composable("add") {
                AddTransactionScreen(onClose = { navController.popBackStack() })
            }
            composable(
                route = "txn/{id}?confirm={confirm}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("confirm") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                ),
                deepLinks = listOf(navDeepLink { uriPattern = "notiledger://txn/{id}" })
            ) {
                TxnEditScreen(onClose = { navController.popBackStack() })
            }
            composable("trash") {
                TrashScreen(
                    onBack = { navController.popBackStack() },
                    snackbarHostState = snackbarHostState
                )
            }
            composable("auto-backup") {
                AutoBackupScreen(onBack = { navController.popBackStack() })
            }
            composable(
                "logs",
                deepLinks = listOf(navDeepLink { uriPattern = "notiledger://logs" })
            ) {
                ParseLogScreen(onBack = { navController.popBackStack() })
            }
            composable(
                "account-add",
                deepLinks = listOf(navDeepLink { uriPattern = "notiledger://account-add" })
            ) {
                AccountEditScreen(onClose = { navController.popBackStack() })
            }
            composable(
                route = "account/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                deepLinks = listOf(navDeepLink { uriPattern = "notiledger://account/{id}" })
            ) {
                AccountDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEditAccount = { id -> navController.navigate("account-edit/$id") }
                )
            }
            composable(
                route = "account-edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) {
                AccountEditScreen(onClose = { navController.popBackStack() })
            }
            composable(
                "whitelist",
                deepLinks = listOf(navDeepLink { uriPattern = "notiledger://whitelist" })
            ) {
                WhitelistScreen(onBack = { navController.popBackStack() })
            }
            composable(
                "rules",
                deepLinks = listOf(navDeepLink { uriPattern = "notiledger://rules" })
            ) {
                RuleListScreen(
                    onBack = { navController.popBackStack() },
                    onNewRule = { navController.navigate("rule-wizard?ruleId=-1") },
                    onEditRule = { id -> navController.navigate("rule-wizard?ruleId=$id") }
                )
            }
            composable(
                route = "rule-wizard?ruleId={ruleId}",
                arguments = listOf(
                    navArgument("ruleId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "notiledger://rule-wizard?ruleId={ruleId}" }
                )
            ) {
                RuleWizardScreen(onClose = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun LedgerBottomBar(
    currentRoute: String?,
    pendingCount: Int,
    onSelect: (String) -> Unit
) {
    val p = LedgerTheme.palette
    Column(modifier = Modifier
        .fillMaxWidth()
        .background(p.card)) {
        HorizontalDivider(thickness = 1.dp, color = p.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(58.dp)
        ) {
            mainTabs.forEach { tab ->
                val selected = tab.route == currentRoute
                val tint = if (selected) p.brand else p.ink3
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.size(21.dp)) {
                        Icon(
                            painter = painterResource(id = tab.icon),
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(21.dp)
                        )
                        if (tab.route == "pending" && pendingCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 9.dp, y = (-4).dp)
                                    .heightIn(min = 15.dp)
                                    .clip(CircleShape)
                                    .background(p.expense)
                                    .border(1.5.dp, p.card, CircleShape)
                                    .padding(horizontal = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pendingCount.toString(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    lineHeight = 10.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = tint
                    )
                }
            }
        }
    }
}
