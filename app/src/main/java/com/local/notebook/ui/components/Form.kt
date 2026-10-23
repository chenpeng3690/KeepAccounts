package com.local.notebook.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.notebook.R
import com.local.notebook.data.local.AccountEntity
import com.local.notebook.data.local.CategoryEntity
import com.local.notebook.ui.theme.LedgerTone
import com.local.notebook.ui.theme.LedgerTheme
import com.local.notebook.ui.theme.containerColor
import com.local.notebook.ui.theme.contentColor
import java.util.Calendar

@Composable
fun TypeToggle(
    type: String,
    onTypeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LedgerTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(p.track)
            .padding(3.dp)
    ) {
        listOf("EXPENSE" to "支出", "INCOME" to "收入").forEach { (value, label) ->
            val selected = type == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) p.card else Color.Transparent)
                    .clickable { onTypeChange(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) p.ink else p.ink3
                )
            }
        }
    }
}

@Composable
fun FormRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    chevron: Boolean = true,
    onClick: () -> Unit
) {
    val p = LedgerTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(p.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.5.sp, color = p.ink3)
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = p.ink,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1
        )
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            ChevronIcon()
        }
    }
}

@Composable
fun FormTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val p = LedgerTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(p.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.5.sp, color = p.ink3)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 12.5.sp,
                color = p.ink,
                textAlign = TextAlign.End
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(p.brand),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            placeholder,
                            fontSize = 12.5.sp,
                            color = p.ink3,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    }
                    inner()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val p = LedgerTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.card
    ) {
        Text(
            "选择分类",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = p.ink,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                val selected = category.id == selectedId
                val tone = runCatching { LedgerTone.valueOf(category.tone) }.getOrDefault(LedgerTone.GRAY)
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onSelect(category.id)
                            onDismiss()
                        }
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (selected) p.brand else tone.containerColor())
                            .then(
                                if (selected) Modifier.border(
                                    1.5.dp,
                                    p.brand,
                                    RoundedCornerShape(15.dp)
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            category.icon,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else tone.contentColor()
                        )
                    }
                    Text(
                        category.name,
                        fontSize = 10.5.sp,
                        color = if (selected) p.brand else p.ink2,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 5.dp),
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerSheet(
    accounts: List<AccountEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val p = LedgerTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.card
    ) {
        Text(
            "选择账户",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = p.ink,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)
        )
        Column(Modifier.navigationBarsPadding()) {
            accounts.forEach { account ->
                val selected = account.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(account.id)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(p.brandSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(account.icon, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.brandSoftText)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        account.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = p.ink,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = "已选择",
                            tint = p.brand,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun RetentionDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val p = LedgerTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text("回收站保留期", color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                listOf(3, 7, 30).forEach { days ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(days)
                                onDismiss()
                            }
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$days 天",
                            fontSize = 13.sp,
                            color = if (days == current) p.brand else p.ink,
                            fontWeight = if (days == current) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (days == current) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = null,
                                tint = p.brand,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Text(
                    "修改后按「删除时间 + 新保留期」重新计算",
                    fontSize = 10.5.sp,
                    color = p.ink3,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = p.brand) }
        }
    )
}

/** 先用日期选择器，再用时间选择器，返回毫秒时间戳。 */
fun showDateTimePicker(
    context: Context,
    initialMillis: Long,
    onPicked: (Long) -> Unit
) {
    val initial = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val chosen = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, initial.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, initial.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    chosen.set(Calendar.HOUR_OF_DAY, hour)
                    chosen.set(Calendar.MINUTE, minute)
                    onPicked(chosen.timeInMillis)
                },
                initial.get(Calendar.HOUR_OF_DAY),
                initial.get(Calendar.MINUTE),
                true
            ).show()
        },
        initial.get(Calendar.YEAR),
        initial.get(Calendar.MONTH),
        initial.get(Calendar.DAY_OF_MONTH)
    ).show()
}
