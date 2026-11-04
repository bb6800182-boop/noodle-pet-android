package com.c520.lamian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 主要動作：primary 底、白字。 */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = T.touch)
            .clip(RoundedCornerShape(T.rMd))
            .background(if (enabled) T.primary else T.surface3)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = T.sMd, vertical = T.sXs),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.button, color = if (enabled) T.onPrimary else T.inkSubtle) }
}

/** 次要動作：surface 底、hairline-strong 外框。 */
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, color: Color = T.ink, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = T.touch)
            .clip(RoundedCornerShape(T.rMd))
            .background(T.surface1)
            .border(1.dp, T.hairlineStrong, RoundedCornerShape(T.rMd))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = T.sMd, vertical = T.sXs),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.button, color = color) }
}

/** 文字按鈕（怎麼做、暫停 1 小時）。 */
@Composable
fun TextLink(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = T.touch).clip(RoundedCornerShape(T.rMd)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = T.sXs),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.button, color = T.inkSubtle) }
}

/** 開關：關閉時 ink-subtle 外框與圓點（元件邊界 3:1），開啟時 primary 底、白圓點。 */
@Composable
fun LSwitch(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 52.dp, height = T.touch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(width = 40.dp, height = 24.dp).clip(CircleShape)
                .background(if (checked) T.primary else Color.Transparent)
                .border(2.dp, if (checked) T.primary else T.inkSubtle, CircleShape),
        ) {
            Box(
                Modifier.offset(x = if (checked) 19.dp else 3.dp, y = 3.dp).size(14.dp).clip(CircleShape)
                    .background(if (checked) T.onPrimary else T.inkSubtle),
            )
        }
    }
}

/** 單選分段：選中只換 surface（DESIGN.md）。 */
@Composable
fun <V> Segmented(options: List<Pair<V, String>>, selected: V, label: String, onSelect: (V) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(T.surface2).border(1.dp, T.hairline, RoundedCornerShape(50)).padding(2.dp)
            .selectableGroup().semantics { contentDescription = label },
    ) {
        options.forEach { (v, text) ->
            val on = v == selected
            Box(
                Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).background(if (on) T.surface3 else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(v) }.padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(text, style = T.button, color = if (on) T.ink else T.inkSubtle) }
        }
    }
}

/** 小膠囊：星期幾（多選）、選動作（單選，radio = true）。選中 primary 底。 */
@Composable
fun Chip(text: String, selected: Boolean, radio: Boolean = false, onClick: () -> Unit) {
    val pick = if (radio) Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    else Modifier.toggleable(value = selected, role = Role.Checkbox) { onClick() }
    Box(
        Modifier.defaultMinSize(minWidth = T.touch, minHeight = T.touch).clip(RoundedCornerShape(50))
            .background(if (selected) T.primary else Color.Transparent)
            .border(1.dp, if (selected) T.primary else T.hairlineStrong, RoundedCornerShape(50))
            .then(pick).padding(horizontal = T.sSm, vertical = T.sXxs),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.button, color = if (selected) T.onPrimary else T.inkSubtle) }
}

/**
 * 數字輸入：− 數字 ＋。輸入不合法時顯示錯誤、不套用；合法才呼叫 onCommit。
 * extraCheck 回傳錯誤訊息（null 代表通過），用在「探頭要比走掉早」這類跨欄位檢查。
 */
@Composable
fun Stepper(
    value: Int, range: IntRange, unit: String, label: String,
    extraCheck: (Int) -> String? = { null },
    onCommit: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var error by remember(value) { mutableStateOf<String?>(null) }
    fun tryCommit(t: String) {
        val v = t.trim().toIntOrNull()
        error = when {
            v == null || v !in range -> "請輸入 ${range.first}–${range.last} 的整數"
            else -> extraCheck(v)
        }
        if (error == null && v != null && v != value) onCommit(v)
    }
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(T.sXxs)) {
            StepButton("−", "$label：減少") { val v = ((text.toIntOrNull() ?: value) - 1).coerceIn(range); text = v.toString(); tryCommit(text) }
            BasicTextField(
                value = text,
                onValueChange = { t -> text = t.filter { it.isDigit() }.take(4); tryCommit(text) },
                singleLine = true,
                textStyle = T.bodySm.copy(color = T.ink, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
                cursorBrush = SolidColor(T.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(56.dp).height(40.dp).clip(RoundedCornerShape(T.rMd)).background(T.canvas)
                    .border(1.dp, if (error != null) T.danger else T.hairlineStrong, RoundedCornerShape(T.rMd))
                    .padding(top = 10.dp).semantics { contentDescription = "$label（${range.first}–${range.last} $unit）" },
            )
            StepButton("＋", "$label：增加") { val v = ((text.toIntOrNull() ?: value) + 1).coerceIn(range); text = v.toString(); tryCommit(text) }
            Text(unit, style = T.caption, color = T.inkSubtle, modifier = Modifier.width(30.dp))
        }
        error?.let { Text(it, style = T.caption, color = T.danger, modifier = Modifier.padding(top = T.sXxs)) }
    }
}

@Composable
private fun StepButton(symbol: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(T.rMd)).background(T.surface2).border(1.dp, T.hairlineStrong, RoundedCornerShape(T.rMd))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = T.body, color = T.ink) }
}

/** 設定群組：surface-1 底、圓角 lg。 */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(T.rLg)).background(T.surface1).border(1.dp, T.hairline, RoundedCornerShape(T.rLg))
            .padding(horizontal = T.sMd, vertical = 2.dp),
        content = content,
    )
}

@Composable
fun GroupTitle(text: String) {
    Text(text, style = T.eyebrow, color = T.inkSubtle, modifier = Modifier.padding(top = T.sMd, bottom = T.sXs))
}

/** 群組裡的一列：左邊標題（＋說明），右邊控制項。第一列以外上面有 hairline。 */
@Composable
fun SettingRow(label: String, hint: String? = null, first: Boolean = false, control: @Composable RowScope.() -> Unit) {
    Column {
        if (!first) Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = T.sSm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = T.sSm)) {
                Text(label, style = T.bodySm, color = T.ink)
                hint?.let { Text(it, style = T.caption, color = T.inkSubtle, modifier = Modifier.padding(top = 2.dp)) }
            }
            control()
        }
    }
}

@Composable
fun Pill(text: String, ok: Boolean = false) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(if (ok) T.successTint else T.surface3).padding(horizontal = T.sXs, vertical = 1.dp)) {
        Text(text, style = T.caption, color = if (ok) T.ink else T.inkMuted)
    }
}

@Composable
fun VSpace(h: androidx.compose.ui.unit.Dp) = Spacer(Modifier.height(h))

/** 群組裡列與列之間的分隔線。 */
@Composable
fun Hairline() = Box(Modifier.fillMaxWidth().height(1.dp).background(T.hairline))

val NumStyle: TextStyle get() = T.cardTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontFeatureSettings = "tnum")

