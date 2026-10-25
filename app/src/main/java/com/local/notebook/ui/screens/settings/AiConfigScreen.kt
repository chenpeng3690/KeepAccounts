package com.local.notebook.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.components.SectionHeader
import com.local.notebook.ui.components.SettingsRow
import com.local.notebook.ui.theme.LedgerTheme

@Composable
fun AiConfigScreen(
    onBack: () -> Unit,
    viewModel: AiConfigViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
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
                "AI 配置",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(30.dp))
        }

        SectionHeader(title = "AI 解析", modifier = Modifier.padding(top = 0.dp))
        LedgerCard(shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(0.dp)) {
            SettingsRow(
                icon = R.drawable.ic_ai,
                iconTint = p.brandSoftText,
                iconBg = p.brandSoft,
                label = "启用 AI 解析",
                checked = state.enabled,
                onCheckedChange = viewModel::setEnabled
            )
        }
        Text(
            "入账方式在设置页选择；直接入账时，AI 置信度低于阈值、金额异常或账户无法匹配仍会进入待确认。",
            fontSize = 10.sp,
            color = p.ink3,
            lineHeight = 15.sp,
            modifier = Modifier.padding(start = 2.dp, top = 6.dp, end = 2.dp)
        )

        SectionHeader(title = "连接配置")
        LedgerCard(shape = RoundedCornerShape(15.dp)) {
            ConfigField(
                label = "Base URL",
                value = state.baseUrl,
                hint = "https://api.deepseek.com",
                onValueChange = viewModel::setBaseUrl
            )
            ConfigField(
                label = "模型",
                value = state.model,
                hint = "deepseek-chat",
                onValueChange = viewModel::setModel
            )
            ConfigField(
                label = "API Key",
                value = state.apiKey,
                hint = "sk-...",
                password = true,
                onValueChange = viewModel::setApiKey
            )
            ConfigField(
                label = "置信度阈值（%）",
                value = state.confidence,
                hint = "70",
                onValueChange = viewModel::setConfidence
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = viewModel::clearApiKey) {
                    Text("清除 Key", color = p.dangerText)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = viewModel::save, enabled = !state.busy) {
                        Text("保存", color = p.ink2)
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = viewModel::testConnection,
                        enabled = !state.busy,
                        shape = RoundedCornerShape(11.dp),
                        color = p.brand
                    ) {
                        Text(
                            if (state.busy) "测试中…" else "测试连接",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        SectionHeader(title = "隐私说明")
        LedgerCard(shape = RoundedCornerShape(15.dp)) {
            Row {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shield),
                    contentDescription = null,
                    tint = p.warn,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(15.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "开启 AI 解析后，命中关键词的通知标题与正文会发送到所配置的 AI 服务商；API Key 使用系统 Keystore 加密保存在本机，不会写入备份。账单数据仍只保存在本地。",
                    fontSize = 11.sp,
                    color = p.ink2,
                    lineHeight = 17.sp
                )
            }
        }
        Text(
            "默认 DeepSeek 接口：https://api.deepseek.com，模型 deepseek-chat。也可填写任意 OpenAI 兼容地址。",
            fontSize = 10.sp,
            color = p.ink3,
            lineHeight = 15.sp,
            modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 20.dp, end = 2.dp)
        )
    }

    state.message?.let {
        SettingsMessageDialog(message = it, onDismiss = viewModel::consumeMessage)
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    hint: String,
    password: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val p = LedgerTheme.palette
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = p.ink3
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            placeholder = { Text(hint, fontSize = 12.sp) },
            visualTransformation = if (password) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            }
        )
    }
}
