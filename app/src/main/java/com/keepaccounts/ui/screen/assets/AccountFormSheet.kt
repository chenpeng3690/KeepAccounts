package com.keepaccounts.ui.screen.assets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.ui.AccountLabels
import com.keepaccounts.ui.component.KaChips
import com.keepaccounts.ui.component.KaField
import com.keepaccounts.ui.component.KaSheet
import com.keepaccounts.ui.component.KaSheetActions
import com.keepaccounts.ui.component.KaSwitchRow
import com.keepaccounts.util.Money

data class AccountFormData(
    val name: String,
    val type: String,
    val balanceSigned: Long,
    val creditLimit: Long?,
    val billDay: Int?,
    val repayDay: Int?,
    val includeInNetWorth: Boolean
)

/**
 * 新建 / 编辑账户。
 *
 * 编辑时**不提供余额修改**：余额是流水的派生值，改了它就跟流水对不上。
 * 要校正余额，走详情页的「校准」，那样会留下一条可追溯的调整记录。
 */
@Composable
fun AccountFormSheet(
    account: AccountEntity?,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (AccountFormData) -> Unit
) {
    val isEdit = account != null
    val typeOptions = AccountLabels.typeOptions

    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var typeIndex by remember {
        mutableStateOf(
            account?.let { a -> typeOptions.indexOf(a.type).coerceAtLeast(0) } ?: 1
        )
    }
    var balanceText by remember {
        mutableStateOf(
            account?.let { Money.format(if (it.currentBalance < 0) -it.currentBalance else it.currentBalance) }
                ?: ""
        )
    }
    var limitText by remember { mutableStateOf(account?.creditLimit?.let { Money.format(it) } ?: "") }
    var billDayText by remember { mutableStateOf(account?.billDay?.toString() ?: "") }
    var repayDayText by remember { mutableStateOf(account?.repayDay?.toString() ?: "") }
    var includeInNetWorth by remember { mutableStateOf(account?.includeInNetWorth ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    val type = typeOptions[typeIndex]
    val liability = AccountLabels.isLiability(type)
    val creditFields = AccountLabels.hasCreditFields(type)

    KaSheet(
        title = if (isEdit) "编辑账户" else "添加账户",
        onDismiss = onDismiss,
        subtitle = if (isEdit) "余额不在这里改，用详情页的「校准」" else "负债类账户填欠款金额即可，系统会存成负余额"
    ) {
        KaField(
            value = name,
            onValueChange = { name = it; error = null },
            label = "账户名称",
            placeholder = "例如：招商银行储蓄卡"
        )
        Spacer(Modifier.height(12.dp))

        Text(
            text = "类型",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        KaChips(
            options = typeOptions.map { AccountLabels.typeName(it) },
            selectedIndex = typeIndex,
            onSelect = { typeIndex = it; error = null }
        )
        Spacer(Modifier.height(12.dp))

        if (!isEdit) {
            KaField(
                value = balanceText,
                onValueChange = { balanceText = it; error = null },
                label = AccountLabels.initialBalanceLabel(type),
                prefix = "¥",
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal
            )
            Spacer(Modifier.height(12.dp))
        } else {
            KaField(
                value = balanceText,
                onValueChange = {},
                label = AccountLabels.balanceLabel(type),
                prefix = "¥",
                keyboardType = KeyboardType.Decimal,
                supportingText = "如需校正，请到账户详情页点「校准」"
            )
            Spacer(Modifier.height(12.dp))
        }

        if (creditFields) {
            KaField(
                value = limitText,
                onValueChange = { limitText = it; error = null },
                label = "信用额度（选填）",
                prefix = "¥",
                placeholder = "20000.00",
                keyboardType = KeyboardType.Decimal
            )
            Spacer(Modifier.height(10.dp))
            KaField(
                value = billDayText,
                onValueChange = { input -> billDayText = input.filter { it.isDigit() }.take(2) },
                label = "账单日（选填）",
                suffix = "日",
                placeholder = "5",
                keyboardType = KeyboardType.Number
            )
            Spacer(Modifier.height(10.dp))
            KaField(
                value = repayDayText,
                onValueChange = { input -> repayDayText = input.filter { it.isDigit() }.take(2) },
                label = "还款日（选填）",
                suffix = "日",
                placeholder = "23",
                keyboardType = KeyboardType.Number
            )
            Spacer(Modifier.height(12.dp))
        }

        KaSwitchRow(
            label = "计入净资产",
            description = "关掉后这个账户不参与净资产计算，适合代持或已停用的卡",
            checked = includeInNetWorth,
            onCheckedChange = { includeInNetWorth = it }
        )

        KaSheetActions(
            confirmText = if (isEdit) "保存" else "添加",
            errorText = error,
            onDismiss = onDismiss,
            onConfirm = {
                val trimmed = name.trim()
                val others = if (isEdit) existingNames.filter { it != account?.name } else existingNames
                when {
                    trimmed.isEmpty() -> error = "账户名不能为空"
                    others.contains(trimmed) -> error = "已有同名账户"
                    !isEdit && balanceText.isNotBlank() && Money.parse(balanceText) == null -> error = "余额格式不对"
                    creditFields && limitText.isNotBlank() && Money.parse(limitText) == null ->
                        error = "额度格式不对"
                    billDayText.isNotBlank() && (billDayText.toInt() !in 1..31) ->
                        error = "账单日要在 1–31 之间"
                    repayDayText.isNotBlank() && (repayDayText.toInt() !in 1..31) ->
                        error = "还款日要在 1–31 之间"
                    else -> {
                        val raw = Money.parse(balanceText) ?: 0L
                        onSave(
                            AccountFormData(
                                name = trimmed,
                                type = type,
                                // 负债类：用户填的是欠款正数，存成负余额
                                balanceSigned = if (liability) -raw else raw,
                                creditLimit = if (creditFields) limitText.takeIf { it.isNotBlank() }
                                    ?.let { Money.parse(it) } else null,
                                billDay = billDayText.toIntOrNull(),
                                repayDay = repayDayText.toIntOrNull(),
                                includeInNetWorth = includeInNetWorth
                            )
                        )
                    }
                }
            }
        )
    }
}
