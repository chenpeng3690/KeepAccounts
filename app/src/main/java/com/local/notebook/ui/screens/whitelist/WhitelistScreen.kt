package com.local.notebook.ui.screens.whitelist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.components.LedgerSwitch
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.components.SectionHeader
import com.local.notebook.ui.theme.LedgerTheme

@Composable
fun WhitelistScreen(
    onBack: () -> Unit,
    viewModel: WhitelistViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var newPackage by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(
                    icon = R.drawable.ic_chevron_left,
                    contentDescription = "返回",
                    onClick = onBack
                )
                Text(
                    "白名单",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.ink,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                RoundIconButton(
                    icon = R.drawable.ic_plus,
                    contentDescription = "手动添加包名",
                    onClick = {
                        newPackage = ""
                        showAddDialog = true
                    }
                )
            }
        }

        item {
            LedgerCard(
                shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp)
            ) {
                Row {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = null,
                        tint = p.warn,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(15.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "只有白名单里的应用通知会被读取，其他应用的通知一律忽略，连原文都不会保存。",
                        fontSize = 11.sp,
                        color = p.ink2,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(p.card)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = p.ink3,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.5.sp, color = p.ink),
                    cursorBrush = SolidColor(p.brand),
                    decorationBox = { inner ->
                        Box {
                            if (state.query.isEmpty()) {
                                Text("搜索应用名称或包名", fontSize = 12.5.sp, color = p.ink3)
                            }
                            inner()
                        }
                    }
                )
                if (state.query.isNotEmpty()) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "清空",
                        tint = p.ink3,
                        modifier = Modifier
                            .size(13.dp)
                            .clickable { viewModel.setQuery("") }
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "已安装应用",
                trailing = if (state.loading) "读取中…" else "${state.apps.size} 个"
            )
        }

        if (state.loading) {
            item {
                Text(
                    "正在读取已安装应用…",
                    fontSize = 11.sp,
                    color = p.ink3,
                    modifier = Modifier.padding(vertical = 24.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(state.apps, key = { it.packageName }) { app ->
                AppRow(
                    icon = app.icon,
                    label = app.label,
                    packageName = app.packageName,
                    enabled = app.packageName in state.whitelist,
                    onToggle = { viewModel.togglePackage(app.packageName, it) }
                )
            }
        }

        if (state.manualPackages.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "手动添加的包名",
                    trailing = "${state.manualPackages.size} 个",
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            items(state.manualPackages, key = { "manual-$it" }) { pkg ->
                AppRow(
                    icon = null,
                    label = pkg,
                    packageName = "未在已安装应用列表中找到",
                    enabled = true,
                    onToggle = { viewModel.togglePackage(pkg, it) }
                )
            }
        }

        item {
            Text(
                "白名单变更后立即生效，不需要重启服务",
                fontSize = 10.5.sp,
                color = p.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
            )
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = p.card,
            title = {
                Text("手动添加包名", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink)
            },
            text = {
                Column {
                    Text(
                        "用于找不到的应用。添加后，该应用的通知会进入解析流程。",
                        fontSize = 11.5.sp,
                        color = p.ink2,
                        lineHeight = 17.sp
                    )
                    OutlinedTextField(
                        value = newPackage,
                        onValueChange = { newPackage = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        placeholder = { Text("例如 com.example.app", fontSize = 12.sp) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newPackage.isNotBlank(),
                    onClick = {
                        viewModel.addPackage(newPackage)
                        showAddDialog = false
                    }
                ) { Text("添加", color = p.brand) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消", color = p.ink2) }
            }
        )
    }
}

@Composable
private fun AppRow(
    icon: android.graphics.Bitmap?,
    label: String,
    packageName: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val p = LedgerTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!enabled) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(p.cardAlt),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    label.take(1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.ink2
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = p.ink,
                maxLines = 1
            )
            Text(
                packageName,
                fontSize = 10.sp,
                color = p.ink3,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        LedgerSwitch(checked = enabled, onCheckedChange = onToggle)
    }
}
