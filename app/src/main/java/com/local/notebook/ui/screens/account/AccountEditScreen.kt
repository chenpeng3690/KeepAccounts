package com.local.notebook.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.notebook.ui.components.FormTextFieldRow
import com.local.notebook.ui.components.LedgerSwitch
import com.local.notebook.ui.components.RoundIconButton
import com.local.notebook.ui.theme.LedgerTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditScreen(
    onClose: () -> Unit,
    viewModel: AccountEditViewModel = hiltViewModel()
) {
    val p = LedgerTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(
                icon = com.local.notebook.R.drawable.ic_chevron_left,
                contentDescription = "返回",
                onClick = onClose
            )
            Text(
                if (state.isEdit) "编辑账户" else "添加账户",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(30.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            FormTextFieldRow(
                label = "名称",
                value = state.name,
                onValueChange = viewModel::setName,
                placeholder = "例如 招商银行 储蓄卡"
            )
            Spacer(Modifier.height(10.dp))

            Text(
                "账户类型",
                fontSize = 10.5.sp,
                color = p.ink3,
                modifier = Modifier.padding(start = 2.dp, bottom = 7.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf("CASH", "DEBIT", "CREDIT", "BNPL", "LOAN", "INVESTMENT", "OTHER").forEach { type ->
                    val selected = state.type == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) p.brandSoft else p.card)
                            .clickable { viewModel.setType(type) }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text(
                            accountTypeLabel(type),
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) p.brandSoftText else p.ink2
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            FormTextFieldRow(
                label = if (state.isCredit) "当前欠款" else "初始余额",
                value = state.balanceInput,
                onValueChange = viewModel::setBalance,
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal
            )
            if (state.isCredit) {
                Text(
                    "欠款请输入正数，账户余额会以负数展示与计算。",
                    fontSize = 10.sp,
                    color = p.ink3,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp)
                )
                Spacer(Modifier.height(8.dp))
                FormTextFieldRow(
                    label = "总额度",
                    value = state.creditLimitInput,
                    onValueChange = viewModel::setCreditLimit,
                    placeholder = "可选",
                    keyboardType = KeyboardType.Decimal
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        FormTextFieldRow(
                            label = "账单日",
                            value = state.billDayInput,
                            onValueChange = viewModel::setBillDay,
                            placeholder = "5",
                            keyboardType = KeyboardType.Number
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        FormTextFieldRow(
                            label = "还款日",
                            value = state.repayDayInput,
                            onValueChange = viewModel::setRepayDay,
                            placeholder = "23",
                            keyboardType = KeyboardType.Number
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                color = p.card
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("计入净资产", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = p.ink)
                        Text(
                            "关闭后，该账户余额不计入净资产",
                            fontSize = 10.sp,
                            color = p.ink3,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    LedgerSwitch(
                        checked = state.includeInNetWorth,
                        onCheckedChange = viewModel::setIncludeInNetWorth
                    )
                }
            }

            state.error?.let {
                Text(
                    it,
                    fontSize = 10.5.sp,
                    color = p.expense,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (state.saveEnabled) p.brand else p.brand.copy(alpha = 0.45f))
                .clickable(enabled = state.saveEnabled) { viewModel.save() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (state.saving) "保存中…" else if (state.isEdit) "保存修改" else "添加账户",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}
