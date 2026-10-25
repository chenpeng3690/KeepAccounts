package com.local.notebook.ui.screens.keywords

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.data.local.TransactionKeywordEntity
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.components.LedgerSwitch
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.theme.LedgerTheme

@Composable
fun KeywordScreen(
    onBack: () -> Unit,
    viewModel: KeywordViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val keywords by viewModel.keywords.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var newWord by remember { mutableStateOf("") }

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
                    "交易关键词",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.ink,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                RoundIconButton(
                    icon = R.drawable.ic_plus,
                    contentDescription = "添加关键词",
                    onClick = {
                        newWord = ""
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
                        "白名单应用的通知只要包含任一启用关键词，就会交给 AI 判断是否为真实交易；未命中的通知不会保存。",
                        fontSize = 11.sp,
                        color = p.ink2,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            LedgerCard(
                modifier = Modifier.padding(top = 10.dp),
                shape = RoundedCornerShape(15.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                if (keywords.isEmpty()) {
                    Text(
                        "还没有关键词，点右上角 + 添加",
                        fontSize = 11.5.sp,
                        color = p.ink3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp)
                    )
                } else {
                    keywords.forEachIndexed { index, keyword ->
                        KeywordRow(
                            keyword = keyword,
                            showDivider = index > 0,
                            onToggle = { viewModel.toggle(keyword.id, it) },
                            onDelete = { viewModel.delete(keyword.id) }
                        )
                    }
                }
            }
        }

        item {
            Text(
                "恢复默认关键词",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = p.brand,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { viewModel.restoreDefaults() }
                    .padding(vertical = 8.dp)
            )
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = p.card,
            title = {
                Text("添加关键词", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink)
            },
            text = {
                Column {
                    Text(
                        "例如「支付」「退款」「到账」。关键词过多会增加 AI 调用次数。",
                        fontSize = 11.5.sp,
                        color = p.ink2,
                        lineHeight = 17.sp
                    )
                    OutlinedTextField(
                        value = newWord,
                        onValueChange = { newWord = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        placeholder = { Text("输入关键词", fontSize = 12.sp) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val word = newWord.trim()
                        if (word.isNotEmpty()) {
                            viewModel.add(word) { showAddDialog = false }
                        }
                    }
                ) {
                    Text("添加", color = p.brand)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("取消", color = p.ink2)
                }
            }
        )
    }
}

@Composable
private fun KeywordRow(
    keyword: TransactionKeywordEntity,
    showDivider: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val p = LedgerTheme.palette
    Column {
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = p.divider)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                keyword.word,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (keyword.enabled) p.ink else p.ink3,
                modifier = Modifier.weight(1f)
            )
            if (keyword.isBuiltin) {
                Text(
                    "内置",
                    fontSize = 9.5.sp,
                    color = p.ink3,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.iconButtonBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_trash),
                contentDescription = "删除关键词",
                tint = p.dangerText,
                modifier = Modifier
                    .size(15.dp)
                    .clickable(onClick = onDelete)
            )
            Spacer(Modifier.width(10.dp))
            LedgerSwitch(
                checked = keyword.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}
