package com.c520.lamian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.c520.lamian.data.AppState
import com.c520.lamian.data.Limits
import com.c520.lamian.data.RemAction
import com.c520.lamian.data.Reminder
import com.c520.lamian.data.Repeat
import com.c520.lamian.data.ScreenOff
import com.c520.lamian.data.Store
import com.c520.lamian.data.WEEK
import com.c520.lamian.data.actionText
import com.c520.lamian.data.hhmm
import com.c520.lamian.data.repeatText
import com.c520.lamian.engine.Alarms
import com.c520.lamian.engine.Schedule
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

@Composable
fun ReminderTab(st: AppState) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Reminder?>(null) }
    val now = ZonedDateTime.now()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = T.sMd, vertical = T.sXs)) {
        Hint("到時間麵條會出來說一句話，也可以帶一組動作或喝水。暫停、安靜時段都不影響自訂提醒。")
        VSpace(T.sSm)
        if (st.reminders.isEmpty()) Text("還沒有自訂提醒。", style = T.bodySm, color = T.inkSubtle, modifier = Modifier.padding(vertical = T.sXs))
        st.reminders.forEach { r ->
            val past = r.repeat == Repeat.ONCE && Schedule.nextReminderAt(r.copy(enabled = true), now) == null
            val exName = if (r.action == RemAction.EXERCISE) Store.exercise(r.exerciseId)?.name else null
            Row(
                Modifier.padding(bottom = T.sXs).fillMaxWidth().clip(RoundedCornerShape(T.rLg)).background(T.surface1)
                    .border(1.dp, T.hairline, RoundedCornerShape(T.rLg)).padding(horizontal = T.sMd, vertical = T.sXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f).heightIn(min = T.touch).clickable(role = Role.Button, onClickLabel = "編輯") { editing = r }
                        .semantics(mergeDescendants = true) { contentDescription = "編輯 ${hhmm(r.minuteOfDay)} ${r.text}" }
                        .padding(vertical = T.sXxs),
                ) {
                    Text(hhmm(r.minuteOfDay), style = T.cardTitle.copy(fontFeatureSettings = "tnum"), color = if (r.enabled) T.ink else T.inkSubtle)
                    Text(r.text, style = T.bodySm, color = if (r.enabled) T.inkMuted else T.inkSubtle)
                    Text("${repeatText(r)}・${actionText(r, exName)}${if (past) "・時間已過" else ""}", style = T.caption, color = T.inkSubtle)
                }
                LSwitch(r.enabled, "${hhmm(r.minuteOfDay)} 的提醒") { on ->
                    if (on && past) toast(context, "這個時間已經過了，點進去改日期")
                    else {
                        Store.setReminderEnabled(r.id, on)
                        if (!on) Alarms.cancelSnooze(context, r.id)
                    }
                }
            }
        }
        VSpace(T.sXs)
        PrimaryButton("＋ 新增提醒", Modifier.fillMaxWidth()) {
            editing = Reminder(id = 0, minuteOfDay = 16 * 60, repeat = Repeat.DAILY, date = LocalDate.now().toString(), text = "")
        }
        VSpace(T.sLg)
    }
    editing?.let { ReminderEditor(it, st) { editing = null } }
}

private fun onceInFuture(r: Reminder): Boolean = runCatching {
    LocalDate.parse(r.date).atTime(r.minuteOfDay / 60, r.minuteOfDay % 60).atZone(ZonedDateTime.now().zone).isAfter(ZonedDateTime.now())
}.getOrDefault(false)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReminderEditor(initial: Reminder, st: AppState, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var d by remember { mutableStateOf(initial.copy(date = initial.date.ifEmpty { LocalDate.now().toString() })) }
    var textErr by remember { mutableStateOf<String?>(null) }
    var repErr by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = initial.id == 0

    fun close() { scope.launch { sheet.hide() }.invokeOnCompletion { onClose() } }

    fun validate(full: Boolean): Boolean {
        textErr = if (d.text.isBlank()) "要說的話不能空白" else null
        repErr = if (!full) null else when {
            d.repeat == Repeat.CUSTOM && d.days.isEmpty() -> "至少選一天"
            d.repeat == Repeat.ONCE && !onceInFuture(d) -> "這個時間已經過了"
            else -> null
        }
        return textErr == null && repErr == null
    }

    fun cleaned(): Reminder = d.copy(
        text = d.text.trim(),
        days = if (d.repeat == Repeat.CUSTOM) d.days else emptySet(),
        date = if (d.repeat == Repeat.ONCE) d.date else "",
        exerciseId = d.exerciseId.takeIf { id -> st.exercises.any { it.id == id && !it.proposed } } ?: "walk",
        // 單次提醒響過會自動關掉；改到未來的時間再存，就重新打開
        enabled = d.enabled || d.repeat == Repeat.ONCE,
    )

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = T.surface1,
        contentColor = T.ink,
        dragHandle = { BottomSheetDefaults.DragHandle(color = T.hairlineStrong) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = T.sMd).padding(bottom = T.sLg)) {
            Text(if (isNew) "新增提醒" else "編輯提醒", style = T.cardTitle, color = T.ink)

            Field("時間") {
                TimeButton(d.minuteOfDay, "時間") { pickTime(context, d.minuteOfDay) { d = d.copy(minuteOfDay = it); repErr = null } }
            }
            Field("重複") {
                Segmented(
                    listOf(Repeat.DAILY to "每天", Repeat.WEEKDAYS to "平日", Repeat.CUSTOM to "自選", Repeat.ONCE to "單次"),
                    d.repeat, "重複",
                ) { d = d.copy(repeat = it); repErr = null }
                if (d.repeat == Repeat.CUSTOM) {
                    FlowRow(Modifier.padding(top = T.sXs), horizontalArrangement = Arrangement.spacedBy(T.sXxs), verticalArrangement = Arrangement.spacedBy(T.sXxs)) {
                        (1..7).forEach { i ->
                            Chip(WEEK[i], i in d.days) { d = d.copy(days = if (i in d.days) d.days - i else d.days + i); repErr = null }
                        }
                    }
                }
                if (d.repeat == Repeat.ONCE) {
                    val date = runCatching { LocalDate.parse(d.date) }.getOrDefault(LocalDate.now())
                    Row(Modifier.padding(top = T.sXs)) {
                        SecondaryButton("${date.year}/${date.monthValue}/${date.dayOfMonth}（${WEEK[date.dayOfWeek.value]}）") {
                            pickDate(context, date) { d = d.copy(date = it.toString()); repErr = null }
                        }
                    }
                }
                ErrorText(repErr)
            }
            Field("要說的話") {
                BasicTextField(
                    value = d.text,
                    onValueChange = { v -> d = d.copy(text = v.replace("\n", " ").take(Limits.REMINDER_TEXT_MAX)); if (d.text.isNotBlank()) textErr = null },
                    singleLine = true,
                    textStyle = T.body.copy(color = T.ink),
                    cursorBrush = SolidColor(T.primary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = T.touch).clip(RoundedCornerShape(T.rMd)).background(T.canvas)
                        .border(1.dp, if (textErr != null) T.danger else T.hairlineStrong, RoundedCornerShape(T.rMd))
                        .padding(horizontal = T.sSm, vertical = T.sSm)
                        .semantics { contentDescription = "要說的話，最多 ${Limits.REMINDER_TEXT_MAX} 字" },
                    decorationBox = { inner ->
                        Box {
                            if (d.text.isEmpty()) Text("例如：三點了，記得回覆廠商的信！", style = T.body, color = T.inkSubtle)
                            inner()
                        }
                    },
                )
                Row(verticalAlignment = Alignment.Top) {
                    ErrorText(textErr, Modifier.weight(1f))
                    if (textErr == null) Spacer(Modifier.weight(1f))
                    Text("${d.text.length}/${Limits.REMINDER_TEXT_MAX}", style = T.caption, color = T.inkSubtle, modifier = Modifier.padding(top = T.sXxs))
                }
            }
            Field("麵條要做什麼") {
                Segmented(
                    listOf(RemAction.SAY to "說一句話", RemAction.EXERCISE to "帶一組動作", RemAction.WATER to "喝水"),
                    d.action, "麵條要做什麼",
                ) { d = d.copy(action = it) }
                if (d.action == RemAction.EXERCISE) {
                    val list = st.exercises.filter { !it.proposed }
                    FlowRow(Modifier.padding(top = T.sXs), horizontalArrangement = Arrangement.spacedBy(T.sXxs), verticalArrangement = Arrangement.spacedBy(T.sXxs)) {
                        list.forEach { e -> Chip(e.name, e.id == d.exerciseId, radio = true) { d = d.copy(exerciseId = e.id) } }
                    }
                }
            }
            Field("螢幕關著的時候") {
                Segmented(listOf(ScreenOff.NOTIFY to "通知並震動", ScreenOff.WAIT to "解鎖後再說"), d.screenOff, "螢幕關著的時候") { d = d.copy(screenOff = it) }
            }

            Row(Modifier.padding(top = T.sLg).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(T.sXs), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("儲存") {
                    if (validate(full = true)) {
                        Store.saveReminder(cleaned())
                        toast(context, "已儲存")
                        close()
                    }
                }
                SecondaryButton("取消") { close() }
                Spacer(Modifier.weight(1f))
                if (!isNew) SecondaryButton("刪除", color = T.danger) { confirmDelete = true }
            }
            TextLink("現在試一次", Modifier.padding(top = T.sXs)) {
                if (validate(full = false)) service(context)?.preview(cleaned())
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text("要刪除這個提醒嗎？", style = T.body, color = T.ink) },
            confirmButton = {
                SecondaryButton("刪除", color = T.danger) {
                    confirmDelete = false
                    Store.deleteReminder(initial.id)
                    Alarms.cancelSnooze(context, initial.id)
                    toast(context, "已刪除")
                    close()
                }
            },
            dismissButton = { SecondaryButton("取消") { confirmDelete = false } },
            containerColor = T.surface1,
        )
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = T.sMd)) {
        Text(label, style = T.eyebrow, color = T.inkSubtle, modifier = Modifier.padding(bottom = T.sXs))
        content()
    }
}

