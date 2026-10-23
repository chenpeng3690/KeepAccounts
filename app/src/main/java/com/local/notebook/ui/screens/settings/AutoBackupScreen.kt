package com.local.notebook.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.R
import com.local.notebook.ui.components.LedgerCard
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.components.SettingsRow
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.util.formatClock
import com.local.notebook.ui.util.formatShortDate

@Composable
fun AutoBackupScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val enabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val period by viewModel.autoBackupPeriodDays.collectAsStateWithLifecycle()
    val lastAutoBackupAt by viewModel.lastAutoBackupAt.collectAsStateWithLifecycle()
    val nextAutoBackupAt by viewModel.nextAutoBackupAt.collectAsStateWithLifecycle()
    var showPeriodDialog by remember { mutableStateOf(false) }

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
                "自动备份",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(30.dp))
        }

        LedgerCard(
            shape = RoundedCornerShape(15.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            SettingsRow(
                icon = R.drawable.ic_clock,
                iconTint = p.brandSoftText,
                iconBg = p.brandSoft,
                label = "自动备份",
                checked = enabled,
                onCheckedChange = viewModel::setAutoBackupEnabled
            )
            SettingsRow(
                icon = null,
                iconTint = p.ink3,
                iconBg = p.card,
                label = "备份周期",
                value = "$period 天",
                indent = true,
                labelMuted = true,
                showDivider = true,
                onClick = { showPeriodDialog = true }
            )
            if (enabled) {
                SettingsRow(
                    icon = null,
                    iconTint = p.ink3,
                    iconBg = p.card,
                    label = "下次自动备份",
                    value = if (nextAutoBackupAt > 0) {
                        "${formatShortDate(nextAutoBackupAt)} ${formatClock(nextAutoBackupAt)}"
                    } else {
                        "等待系统调度"
                    },
                    indent = true,
                    labelMuted = true,
                    showChevron = false,
                    showDivider = true
                )
                SettingsRow(
                    icon = null,
                    iconTint = p.ink3,
                    iconBg = p.card,
                    label = "上次自动备份",
                    value = if (lastAutoBackupAt > 0) {
                        "${formatShortDate(lastAutoBackupAt)} ${formatClock(lastAutoBackupAt)}"
                    } else {
                        "尚未备份"
                    },
                    indent = true,
                    labelMuted = true,
                    showChevron = false,
                    showDivider = true
                )
            }
        }

        Text(
            "自动备份只在设备电量充足时执行，最多保留最近 5 份，超出后自动删除最旧的备份。",
            fontSize = 10.5.sp,
            color = p.ink3,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 2.dp, top = 8.dp, end = 2.dp)
        )
        Spacer(Modifier.padding(bottom = 20.dp))
    }

    if (showPeriodDialog) {
        ChoiceDialog(
            title = "备份周期",
            options = listOf(
                "1 天" to "1",
                "3 天" to "3",
                "7 天" to "7",
                "30 天" to "30"
            ),
            current = period.toString(),
            onSelect = { value -> value.toIntOrNull()?.let(viewModel::setAutoBackupPeriod) },
            onDismiss = { showPeriodDialog = false }
        )
    }
}
