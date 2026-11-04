package com.c520.lamian.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.c520.lamian.data.AppState
import com.c520.lamian.data.Store
import com.c520.lamian.data.greeting
import com.c520.lamian.data.hhmm
import com.c520.lamian.data.statusLine
import com.c520.lamian.data.validMissed
import com.c520.lamian.engine.Schedule
import com.c520.lamian.overlay.SpriteCache
import com.c520.lamian.service.PetService
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeTab(st: AppState, perms: Perms, sprites: SpriteCache, onPerms: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = System.currentTimeMillis() } }
    val s = st.settings
    val t = st.today
    val paused = (st.pausedUntil ?: 0L) > now
    var waving by remember { mutableStateOf(false) }
    LaunchedEffect(waving) { if (waving) { delay(1800); waving = false } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = T.sMd, vertical = T.sXs)) {
        if (!s.enabled) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(T.rLg)).background(T.surface2).border(1.dp, T.hairlineStrong, RoundedCornerShape(T.rLg))
                    .padding(horizontal = T.sMd, vertical = T.sSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("拉麵關著", style = T.bodySm, color = T.ink)
                    Hint("不會提醒你；常駐通知和麵條都收起來了。")
                }
                Spacer(Modifier.width(T.sSm))
                PrimaryButton("打開") { PetService.setEnabled(context, true) }
            }
            VSpace(T.sSm)
        } else if (!perms.notify || !perms.overlay) SetupCard(perms, onPerms)

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(T.rLg)).background(T.surface1).border(1.dp, T.hairline, RoundedCornerShape(T.rLg))
                .padding(start = T.sXs, end = T.sMd, top = T.sSm, bottom = T.sSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedPet(
                sprites, s.character, if (waving) "wave" else "idle",
                Modifier.size(96.dp).clip(RoundedCornerShape(T.rLg)).clickable(role = Role.Button) { waving = true }
                    .semantics { contentDescription = "跟麵條打招呼" },
            )
            Spacer(Modifier.width(T.sSm))
            Column {
                Text(greeting(), style = T.body.copy(fontWeight = FontWeight.Medium), color = T.ink)
                Text(statusLine(st, now), style = T.bodySm, color = T.inkMuted, modifier = Modifier.padding(top = T.sXxs))
            }
        }
        VSpace(T.sSm)

        Row(horizontalArrangement = Arrangement.spacedBy(T.sXs)) {
            Stat("${t.water}", "杯水")
            Stat("${t.stretch}", "組伸展")
            Stat("${t.walk}", "次起來走走")
        }
        VSpace(T.sSm)

        if (s.enabled) validMissed(st, now)?.let { m ->
            Store.exercise(m.exerciseId)?.let { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(T.rLg)).background(T.surface2).border(1.dp, T.hairlineStrong, RoundedCornerShape(T.rLg))
                        .padding(horizontal = T.sMd, vertical = T.sSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("剛剛的「${e.name}」還沒做", style = T.bodySm, color = T.ink)
                        Hint("${s.recallWithinMin} 分鐘內可以補做")
                    }
                    Spacer(Modifier.width(T.sSm))
                    PrimaryButton("現在做") { needOverlay(context, perms) { service(context)?.recall() } }
                }
                VSpace(T.sSm)
            }
        }

        if (s.enabled) FlowRow(horizontalArrangement = Arrangement.spacedBy(T.sXs), verticalArrangement = Arrangement.spacedBy(T.sXs)) {
            PrimaryButton("現在做一組") { needOverlay(context, perms) { service(context)?.manualStretch() } }
            SecondaryButton("喝了一杯") {
                service(context)?.let {
                    it.recordWaterNow("補記一杯")
                    toast(context, "記上了，今天第 ${Store.current.today.water} 杯")
                }
            }
            SecondaryButton(if (paused) "恢復提醒" else "暫停 1 小時") { service(context)?.togglePause(quiet = false) }
        }

        if (s.enabled) {
            GroupTitle("接下來")
            Group {
                NextRow("動一動", nextStretch(st, now), first = true)
                NextRow("喝水", nextWater(st))
                NextRow("自訂提醒", nextReminder(st))
            }
        }

        GroupTitle("今天的紀錄")
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val logs = st.log.filter { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() == today }.take(10)
        if (logs.isEmpty()) Hint("還沒有紀錄", Modifier.padding(vertical = T.sXs))
        else Group {
            logs.forEachIndexed { i, l ->
                Column {
                    if (i > 0) Hairline()
                    Row(Modifier.fillMaxWidth().padding(vertical = T.sXs), verticalAlignment = Alignment.CenterVertically) {
                        Text(hhmm(l.at), style = T.caption.copy(fontFeatureSettings = "tnum"), color = T.inkSubtle, modifier = Modifier.width(44.dp))
                        Text(l.label, style = T.bodySm, color = T.ink, modifier = Modifier.weight(1f).padding(end = T.sXs))
                        if (l.result.isNotEmpty()) Pill(l.result, l.ok)
                    }
                }
            }
        }
        VSpace(T.sLg)
    }
}

/** 沒有懸浮權限時先說明，不然按了沒反應。 */
private fun needOverlay(context: android.content.Context, perms: Perms, go: () -> Unit) {
    if (!perms.overlay) {
        toast(context, "要先允許「顯示在其他應用程式上層」，麵條才能出來")
        openOverlaySettings(context)
    } else go()
}

@Composable
private fun RowScope.Stat(num: String, label: String) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(T.rLg)).background(T.surface1).border(1.dp, T.hairline, RoundedCornerShape(T.rLg)).padding(T.sSm)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(num, style = NumStyle, color = T.ink)
        Text(label, style = T.caption, color = T.inkSubtle)
    }
}

@Composable
private fun NextRow(label: String, value: String, first: Boolean = false) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        if (!first) Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = T.sSm), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = T.bodySm, color = T.ink, modifier = Modifier.width(80.dp))
            Text(value, style = T.bodySm, color = T.inkMuted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SetupCard(perms: Perms, onPerms: () -> Unit) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked = true; onPerms() }
    Text("還差幾個設定，麵條才能出來", style = T.eyebrow, color = T.inkSubtle, modifier = Modifier.padding(top = T.sXs, bottom = T.sXs))
    Group {
        var first = true
        if (!perms.overlay) {
            SettingRow("顯示在其他應用程式上層", "麵條要畫在其他 App 上面才能出來提醒", first = true) {
                PrimaryButton("去允許") { openOverlaySettings(context) }
            }
            first = false
        }
        if (!perms.notify) {
            SettingRow("通知", "螢幕關著時用通知提醒；常駐通知有快速按鈕", first = first) {
                PrimaryButton(if (asked) "到設定打開" else "允許") {
                    if (asked) openNotificationSettings(context) else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    VSpace(T.sSm)
}

private fun nextStretch(st: AppState, now: Long): String {
    val s = st.settings
    val since = st.live.usedSince
    return when {
        !s.stretchOn -> "關著"
        (st.pausedUntil ?: 0L) > now -> "暫停中"
        since == null -> "螢幕亮著時開始計時"
        else -> {
            val left = s.intervalMin - ((now - since) / 60_000L).toInt()
            val next = Store.peekNextExercise(st)?.name ?: "隨機"
            "${if (left > 0) "再滑 $left 分鐘" else "馬上"}（下一個：$next）"
        }
    }
}

private fun nextWater(st: AppState): String {
    if (!st.settings.waterOn) return "關著"
    val at = st.nextWaterAt ?: return "準備中"
    val d = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (d) {
        today -> hhmm(at)
        today.plusDays(1) -> "明天 ${hhmm(at)}"
        else -> "${d.monthValue}/${d.dayOfMonth} ${hhmm(at)}"
    }
}

private fun nextReminder(st: AppState): String {
    val now = ZonedDateTime.now()
    val up = st.reminders.mapNotNull { r -> Schedule.nextReminderAt(r, now)?.takeIf { it.toLocalDate() == now.toLocalDate() }?.let { it to r } }
        .minByOrNull { it.first.toInstant() } ?: return "今天沒有了"
    return "${hhmm(up.second.minuteOfDay)} ${up.second.text}"
}
