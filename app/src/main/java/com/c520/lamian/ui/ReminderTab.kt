package com.c520.lamian.ui

import com.c520.lamian.L
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
        Hint(L.t("到時間麵條會出來說一句話，也可以帶一組動作或喝水。暫停、安靜時段都不影響自訂提醒。", "At the set time the noodle comes out and says your line; it can also lead an exercise or a water break. Pause and quiet hours don't affect custom reminders."))
        VSpace(T.sSm)
        if (st.reminders.isEmpty()) Text(L.t("還沒有自訂提醒。", "No custom reminders yet."), style = T.bodySm, color = T.inkSubtle, modifier = Modifier.padding(vertical = T.sXs))
        st.reminders.forEach { r ->
            val past = r.repeat == Repeat.ONCE && Schedule.nextReminderAt(r.copy(enabled = true), now) == null
            val exName = if (r.action == RemAction.EXERCISE) Store.exercise(r.exerciseId)?.name else null
            Row(
                Modifier.padding(bottom = T.sXs).fillMaxWidth().clip(RoundedCornerShape(T.rLg)).background(T.surface1)
                    .border(1.dp, T.hairline, RoundedCornerShape(T.rLg)).padding(horizontal = T.sMd, vertical = T.sXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f).heightIn(min = T.touch).clickable(role = Role.Button, onClickLabel = L.t("編輯", "Edit")) { editing = r }
                        .semantics(mergeDescendants = true) { contentDescription = L.t("編輯 ${hhmm(r.minuteOfDay)} ${r.text}", "Edit ${hhmm(r.minuteOfDay)} ${r.text}") }
                        .padding(vertical = T.sXxs),
                ) {
                    Text(hhmm(r.minuteOfDay), style = T.cardTitle.copy(fontFeatureSettings = "tnum"), color = if (r.enabled) T.ink else T.inkSubtle)
                    Text(r.text, style = T.bodySm, color = if (r.enabled) T.inkMuted else T.inkSubtle)
                    Text(repeatText(r) + L.t("・", " · ") + actionText(r, exName) + if (past) L.t("・時間已過", " · Time passed") else "", style = T.caption, color = T.inkSubtle)
                }
                LSwitch(r.enabled, L.t("${hhmm(r.minuteOfDay)} 的提醒", "Reminder at ${hhmm(r.minuteOfDay)}")) { on ->
                    if (on && past) toast(context, L.t("這個時間已經過了，點進去改日期", "That time has passed. Tap to change the date"))
                    else {
                        Store.setReminderEnabled(r.id, on)
                        if (!on) Alarms.cancelSnooze(context, r.id)
                    }
                }
            }
        }
        VSpace(T.sXs)
        PrimaryButton(L.t("＋ 新增提醒", "+ New reminder"), Modifier.fillMaxWidth()) {
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
        textErr = if (d.text.isBlank()) L.t("要說的話不能空白", "The line can't be empty") else null
        repErr = if (!full) null else when {
            d.repeat == Repeat.CUSTOM && d.days.isEmpty() -> L.t("至少選一天", "Choose at least one day")
            d.repeat == Repeat.ONCE && !onceInFuture(d) -> L.t("這個時間已經過了", "That time has passed")
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
            Text(if (isNew) L.t("新增提醒", "New reminder") else L.t("編輯提醒", "Edit reminder"), style = T.cardTitle, color = T.ink)

            Field(L.t("時間", "Time")) {
                TimeButton(d.minuteOfDay, L.t("時間", "Time")) { pickTime(context, d.minuteOfDay) { d = d.copy(minuteOfDay = it); repErr = null } }
            }
            Field(L.t("重複", "Repeat")) {
                Segmented(
                    listOf(Repeat.DAILY to L.t("每天", "Every day"), Repeat.WEEKDAYS to L.t("平日", "Weekdays"), Repeat.CUSTOM to L.t("自選", "Custom"), Repeat.ONCE to L.t("單次", "Once")),
                    d.repeat, L.t("重複", "Repeat"),
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
                        SecondaryButton(L.t("${date.year}/${date.monthValue}/${date.dayOfMonth}（${WEEK[date.dayOfWeek.value]}）", "${date.year}/${date.monthValue}/${date.dayOfMonth} (${WEEK[date.dayOfWeek.value]})")) {
                            pickDate(context, date) { d = d.copy(date = it.toString()); repErr = null }
                        }
                    }
                }
                ErrorText(repErr)
            }
            Field(L.t("要說的話", "What to say")) {
                BasicTextField(
                    value = d.text,
                    onValueChange = { v -> d = d.copy(text = v.replace("\n", " ").take(Limits.REMINDER_TEXT_MAX)); if (d.text.isNotBlank()) textErr = null },
                    singleLine = true,
                    textStyle = T.body.copy(color = T.ink),
                    cursorBrush = SolidColor(T.primary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = T.touch).clip(RoundedCornerShape(T.rMd)).background(T.canvas)
                        .border(1.dp, if (textErr != null) T.danger else T.hairlineStrong, RoundedCornerShape(T.rMd))
                        .padding(horizontal = T.sSm, vertical = T.sSm)
                        .semantics { contentDescription = L.t("要說的話，最多 ${Limits.REMINDER_TEXT_MAX} 字", "What to say, up to ${Limits.REMINDER_TEXT_MAX} characters") },
                    decorationBox = { inner ->
                        Box {
                            if (d.text.isEmpty()) Text(L.t("例如：三點了，記得回覆廠商的信！", "For example: It's 3, reply to the vendor!"), style = T.body, color = T.inkSubtle)
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
            Field(L.t("麵條要做什麼", "What the noodle does")) {
                Segmented(
                    listOf(RemAction.SAY to L.t("說一句話", "Say a line"), RemAction.EXERCISE to L.t("帶一組動作", "Lead an exercise"), RemAction.WATER to L.t("喝水", "Water")),
                    d.action, L.t("麵條要做什麼", "What the noodle does"),
                ) { d = d.copy(action = it) }
                if (d.action == RemAction.EXERCISE) {
                    val list = st.exercises.filter { !it.proposed }
                    FlowRow(Modifier.padding(top = T.sXs), horizontalArrangement = Arrangement.spacedBy(T.sXxs), verticalArrangement = Arrangement.spacedBy(T.sXxs)) {
                        list.forEach { e -> Chip(e.name, e.id == d.exerciseId, radio = true) { d = d.copy(exerciseId = e.id) } }
                    }
                }
            }
            Field(L.t("螢幕關著的時候", "When the screen is off")) {
                Segmented(listOf(ScreenOff.NOTIFY to L.t("通知並震動", "Notify and vibrate"), ScreenOff.WAIT to L.t("解鎖後再說", "Wait for unlock")), d.screenOff, L.t("螢幕關著的時候", "When the screen is off")) { d = d.copy(screenOff = it) }
            }

            Row(Modifier.padding(top = T.sLg).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(T.sXs), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(L.t("儲存", "Save")) {
                    if (validate(full = true)) {
                        Store.saveReminder(cleaned())
                        toast(context, L.t("已儲存", "Saved"))
                        close()
                    }
                }
                SecondaryButton(L.t("取消", "Cancel")) { close() }
                Spacer(Modifier.weight(1f))
                if (!isNew) SecondaryButton(L.t("刪除", "Delete"), color = T.danger) { confirmDelete = true }
            }
            TextLink(L.t("現在試一次", "Try it now"), Modifier.padding(top = T.sXs)) {
                if (validate(full = false)) service(context)?.preview(cleaned())
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(L.t("要刪除這個提醒嗎？", "Delete this reminder?"), style = T.body, color = T.ink) },
            confirmButton = {
                SecondaryButton(L.t("刪除", "Delete"), color = T.danger) {
                    confirmDelete = false
                    Store.deleteReminder(initial.id)
                    Alarms.cancelSnooze(context, initial.id)
                    toast(context, L.t("已刪除", "Deleted"))
                    close()
                }
            },
            dismissButton = { SecondaryButton(L.t("取消", "Cancel")) { confirmDelete = false } },
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

