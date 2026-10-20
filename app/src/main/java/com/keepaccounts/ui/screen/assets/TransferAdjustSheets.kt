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
import androidx.compose.ui.unit.sp
import com.keepaccounts.data.local.db.AccountEntity
import com.keepaccounts.ui.AccountLabels
import com.keepaccounts.ui.component.KaChips
import com.keepaccounts.ui.component.KaField
import com.keepaccounts.ui.component.KaSheet
import com.keepaccounts.ui.component.KaSheetActions
import com.keepaccounts.ui.theme.TabularNumbers
import com.keepaccounts.util.Money

/** 转账：两端账户对冲，不计入收支统计 */
@Composable
fun TransferSheet(
    from: AccountEntity,
    targets: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (toAccountId: Long, amountCents: Long, note: String?) -> Unit
) {
    var targetIndex by remember { mutableStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    KaSheet(
        title = "转账",
        subtitle = "从「${from.name}」转出，两端账户同时变动，不计入收支",
        onDismiss = onDismiss
    ) {
        if (targets.isEmpty()) {
            Text(
                text = "还没有其他账户可以转入。先再建一个账户。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(8.dp))
            return@KaSheet
        }

        Text(
            text = "转入账户",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        KaChips(
            options = targets.map { it.name },
            selectedIndex = targetIndex,
            onSelect = { targetIndex = it }
        )
        Spacer(Modifier.height(14.dp))

        KaField(
            value = amountText,
            onValueChange = { amountText = it; error = null },
            label = "金额",
            prefix = "¥",
            placeholder = "0.00",
            keyboardType = KeyboardType.Decimal
        )
        Spacer(Modifier.height(10.dp))
        KaField(
            value = note,
            onValueChange = { note = it; error = null },
            label = "备注（选填）",
            placeholder = "例如：信用卡还款"
        )

        KaSheetActions(
            confirmText = "转账",
            errorText = error,
            onDismiss = onDismiss,
            onConfirm = {
                val cents = Money.parse(amountText)
                when {
                    cents == null -> error = "金额格式不对"
                    cents <= 0 -> error = "金额必须大于 0"
                    else -> {
                        val target = targets.getOrNull(targetIndex)
                        if (target == null) error = "转入账户无效"
                        else onSave(target.id, cents, note.takeIf { it.isNotBlank() })
                    }
                }
            }
        )
    }
}

/**
 * 余额校准：把余额直接改成实际值，差额落一条「余额调整」流水。
 * 这条流水在账单里可见（知道钱去哪了），但不进收支统计。
 */
@Composable
fun AdjustSheet(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onSave: (actualBalanceSigned: Long) -> Unit
) {
    val liability = AccountLabels.isLiability(account.type)
    val currentShown = if (liability) -account.currentBalance else account.currentBalance

    var text by remember { mutableStateOf(Money.format(currentShown)) }
    var error by remember { mutableStateOf<String?>(null) }

    KaSheet(
        title = "校准余额",
        subtitle = "填入实际金额，差额会记一笔「余额调整」，不计入收支统计",
        onDismiss = onDismiss
    ) {
        Text(
            text = "${AccountLabels.balanceLabel(account.type)}：¥ ${Money.format(currentShown)}",
            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        KaField(
            value = text,
            onValueChange = { text = it; error = null },
            label = "实际${if (liability) "欠款" else "余额"}",
            prefix = "¥",
            keyboardType = KeyboardType.Decimal
        )

        KaSheetActions(
            confirmText = "确认校准",
            errorText = error,
            onDismiss = onDismiss,
            onConfirm = {
                val raw = Money.parse(text)
                if (raw == null) {
                    error = "金额格式不对"
                } else {
                    onSave(if (liability) -raw else raw)
                }
            }
        )
    }
}
