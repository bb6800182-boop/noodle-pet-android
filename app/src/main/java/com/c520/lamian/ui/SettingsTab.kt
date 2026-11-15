package com.c520.lamian.ui

import com.c520.lamian.L
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import com.c520.lamian.About
import com.c520.lamian.BuildConfig
import com.c520.lamian.data.ActorSize
import com.c520.lamian.data.AppState
import com.c520.lamian.data.CHARACTERS
import com.c520.lamian.data.Entrance
import com.c520.lamian.data.IdleMode
import com.c520.lamian.data.Limits
import com.c520.lamian.data.PeekPos
import com.c520.lamian.data.Settings
import com.c520.lamian.data.Side
import com.c520.lamian.data.Store
import com.c520.lamian.overlay.SpriteCache
import com.c520.lamian.service.PetService

private fun set(f: (Settings) -> Settings) = Store.updateSettings(f)

@Composable
fun SettingsTab(st: AppState, perms: Perms, sprites: SpriteCache) {
    val s = st.settings
    val context = LocalContext.current
    var license by remember { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked = true }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = T.sMd, vertical = T.sXs)) {
        GroupTitle(L.t("拉麵", "Lamian"))
        Group {
            SettingRow(L.t("開啟拉麵", "Lamian on"), L.t("關掉就完全停止：不提醒，常駐通知和麵條都收起來；之後回這裡打開就恢復", "Off stops everything: no reminders, and the status notification and the noodle are put away. Turn it back on here"), first = true) {
                LSwitch(s.enabled, L.t("開啟拉麵", "Lamian on")) { on ->
                    PetService.setEnabled(context, on)
                    toast(context, if (on) L.t("拉麵開始提醒了", "Lamian is on") else L.t("拉麵關掉了", "Lamian is off"))
                }
            }
        }

        GroupTitle(L.t("麵條", "Noodle"))
        Group {
            StackedRow(L.t("平時", "When idle"), L.t("隱藏最省電；探頭會一直貼在螢幕邊", "Hidden saves the most battery; peeking keeps it at the screen edge"), first = true) {
                Segmented(listOf(IdleMode.HIDDEN to L.t("隱藏", "Hidden"), IdleMode.PEEK to L.t("側邊探頭", "Peek from side")), s.idleMode, L.t("平時", "When idle")) { v -> set { it.copy(idleMode = v) } }
            }
            StackedRow(L.t("怎麼出來", "Entrance")) {
                Segmented(listOf(Entrance.WALK to L.t("走出來", "Walk in"), Entrance.DROP to L.t("垂降", "Drop in")), s.entrance, L.t("怎麼出來", "Entrance")) { v -> set { it.copy(entrance = v) } }
            }
            StackedRow(L.t("站在", "Stands at")) {
                Segmented(listOf(Side.RIGHT to L.t("右下", "Bottom right"), Side.LEFT to L.t("左下", "Bottom left")), s.side, L.t("站在", "Stands at")) { v -> set { it.copy(side = v) } }
            }
            StackedRow(L.t("大小", "Size")) {
                Segmented(listOf(ActorSize.S to L.t("小", "S"), ActorSize.M to L.t("中", "M"), ActorSize.L to L.t("大", "L")), s.actorSize, L.t("大小", "Size")) { v -> set { it.copy(actorSize = v) } }
            }
            StackedRow(L.t("探頭高度", "Peek height"), L.t("也可以直接拖麵條", "You can also drag the noodle")) {
                Segmented(listOf(PeekPos.TOP to L.t("上", "Top"), PeekPos.MIDDLE to L.t("中", "Middle"), PeekPos.BOTTOM to L.t("下", "Bottom")), s.peekPos, L.t("探頭高度", "Peek height")) { v -> set { it.copy(peekPos = v) } }
            }
            StackedRow(L.t("角色", "Character")) {
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(T.sXxs)) {
                    CHARACTERS.forEach { (id, name, kind) -> CharChip(id, name, kind, s.character == id, sprites) { set { it.copy(character = id) } } }
                }
            }
        }

        GroupTitle(L.t("滑手機提醒", "Screen-time reminders"))
        Group {
            SettingRow(L.t("連續滑手機太久就提醒", "Remind me after long phone use"), first = true) { LSwitch(s.stretchOn, L.t("連續滑手機太久就提醒", "Remind me after long phone use")) { v -> set { it.copy(stretchOn = v) } } }
            SettingRow(L.t("連續使用多久提醒", "Remind after")) { Stepper(s.intervalMin, Limits.interval, L.t("分鐘", "min"), L.t("連續使用多久提醒", "Remind after")) { v -> set { it.copy(intervalMin = v) } } }
            SettingRow(L.t("關螢幕多久算休息過", "Screen off counts as a break after"), L.t("休息過就重新計時", "The timer restarts after a break")) { Stepper(s.breakResetMin, Limits.breakReset, L.t("分鐘", "min"), L.t("關螢幕多久算休息過", "Screen off counts as a break after")) { v -> set { it.copy(breakResetMin = v) } } }
        }

        GroupTitle(L.t("喝水", "Water"))
        Group {
            SettingRow(L.t("提醒喝水", "Water reminders"), first = true) { LSwitch(s.waterOn, L.t("提醒喝水", "Water reminders")) { v -> set { it.copy(waterOn = v) } } }
            SettingRow(L.t("每隔", "Every"), L.t("照時鐘算，不是照滑手機的時間", "By the clock, not by screen time")) { Stepper(s.waterEveryMin, Limits.waterEvery, L.t("分鐘", "min"), L.t("喝水每隔", "Water every")) { v -> set { it.copy(waterEveryMin = v) } } }
            TimeRange(L.t("時段", "Hours"), s.waterFrom, s.waterTo, { from, to -> if (from >= to) L.t("開始要早於結束", "Start must be before end") else null }) { from, to -> set { it.copy(waterFrom = from, waterTo = to) } }
        }

        GroupTitle(L.t("泡泡", "Bubble"))
        Group {
            SettingRow(L.t("按「晚點」延後", "\"Later\" snoozes for"), first = true) { Stepper(s.snoozeMin, Limits.snooze, L.t("分鐘", "min"), L.t("按晚點延後", "Later snoozes for")) { v -> set { it.copy(snoozeMin = v) } } }
            SettingRow(L.t("沒理多久探頭", "Peek if ignored for")) {
                Stepper(s.peekAfterSec, Limits.peekAfter, L.t("秒", "sec"), L.t("沒理多久探頭", "Peek if ignored for"), extraCheck = { v -> if (v >= s.dismissAfterSec) L.t("探頭要比自己走掉（${s.dismissAfterSec} 秒）早", "Must be earlier than leaving (${s.dismissAfterSec} sec)") else null }) { v ->
                    set { it.copy(peekAfterSec = v) }
                }
            }
            SettingRow(L.t("沒理多久自己走掉", "Leave if ignored for"), L.t("自訂提醒不會自己走，要按「知道了」", "Custom reminders stay until you tap \"Got it\"")) {
                Stepper(s.dismissAfterSec, Limits.dismissAfter, L.t("秒", "sec"), L.t("沒理多久自己走掉", "Leave if ignored for"), extraCheck = { v -> if (v <= s.peekAfterSec) L.t("要比探頭（${s.peekAfterSec} 秒）晚", "Must be later than peeking (${s.peekAfterSec} sec)") else null }) { v ->
                    set { it.copy(dismissAfterSec = v) }
                }
            }
            SettingRow(L.t("錯過後多久內可以補做", "Catch up on a missed one within"), L.t("0 代表不提供補做", "0 turns catch-up off")) { Stepper(s.recallWithinMin, Limits.recallWithin, L.t("分鐘", "min"), L.t("錯過後多久內可以補做", "Catch up on a missed one within")) { v -> set { it.copy(recallWithinMin = v) } } }
            SettingRow(L.t("一出現就展開「怎麼做」", "Show \"How to\" right away")) { LSwitch(s.expandSteps, L.t("一出現就展開怎麼做", "Show how-to right away")) { v -> set { it.copy(expandSteps = v) } } }
        }

        GroupTitle(L.t("不打擾", "Do not disturb"))
        Group {
            SettingRow(L.t("通話中先不出來", "Stay away during calls"), L.t("伸展、喝水延到掛斷後；自訂提醒改發通知", "Stretch and water wait until you hang up; custom reminders become notifications"), first = true) { LSwitch(s.holdInCall, L.t("通話中先不出來", "Stay away during calls")) { v -> set { it.copy(holdInCall = v) } } }
            SettingRow(L.t("安靜時段", "Quiet hours"), L.t("只擋伸展和喝水，自訂提醒照常", "Blocks stretch and water only; custom reminders still come")) { LSwitch(s.quietOn, L.t("安靜時段", "Quiet hours")) { v -> set { it.copy(quietOn = v) } } }
            TimeRange(L.t("時段", "Hours"), s.quietFrom, s.quietTo, { from, to -> if (from == to) L.t("開始和結束不能一樣", "Start and end can't be the same") else null }) { from, to -> set { it.copy(quietFrom = from, quietTo = to) } }
        }

        GroupTitle(L.t("系統", "System"))
        Group {
            SettingRow(L.t("開機自動啟動", "Start on boot"), first = true) { LSwitch(s.autostart, L.t("開機自動啟動", "Start on boot")) { v -> set { it.copy(autostart = v) } } }
            SettingRow(L.t("顯示在其他應用程式上層", "Display over other apps"), if (perms.overlay) null else L.t("麵條要畫在其他 App 上面才能出來", "The noodle needs this to appear over other apps")) {
                if (perms.overlay) Pill(L.t("已允許", "Allowed"), ok = true) else SecondaryButton(L.t("去允許", "Allow")) { openOverlaySettings(context) }
            }
            SettingRow(L.t("通知", "Notifications"), if (perms.notify) null else L.t("螢幕關著時靠通知提醒", "Used to remind you while the screen is off")) {
                if (perms.notify) Pill(L.t("已允許", "Allowed"), ok = true)
                else SecondaryButton(if (asked) L.t("到設定打開", "Open settings") else L.t("允許", "Allow")) {
                    if (asked) openNotificationSettings(context) else notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            SettingRow(L.t("電池用量", "Battery usage"), L.t("部分品牌要設成「不限制」才不會被關掉", "Some brands need \"Unrestricted\" or the app gets closed")) {
                if (perms.battery) Pill(L.t("不限制", "Unrestricted"), ok = true) else SecondaryButton(L.t("前往設定", "Open settings")) { openBatterySettings(context) }
            }
            SettingRow(L.t("動畫", "Animation"), L.t("跟著系統的「移除動畫」設定，開了就不走路、直接出現", "Follows the system \"Remove animations\" setting: when on, it appears without walking")) {}
        }

        GroupTitle(L.t("關於", "About"))
        Group {
            SettingRow(L.t("版本", "Version"), first = true) { Text("v${BuildConfig.VERSION_NAME}", style = T.bodySm, color = T.inkMuted) }
            SettingRow(L.t("授權資訊", "Licenses")) { SecondaryButton(L.t("查看", "View")) { license = true } }
            SettingRow(L.t("聯繫作者", "Contact"), About.CONTACT_TEXT) { SecondaryButton(L.t("寄信", "Email")) { About.mail(context, L.t("拉麵手機版", "Lamian for Android"), BuildConfig.VERSION_NAME) } }
            SettingRow(L.t("更多軟體", "More apps"), About.MORE_TEXT) { SecondaryButton(L.t("前往", "Open")) { About.more(context) } }
        }
        VSpace(T.sLg)
    }

    if (license) {
        AlertDialog(
            onDismissRequest = { license = false },
            title = { Text(L.t("授權資訊", "Licenses"), style = T.cardTitle, color = T.ink) },
            text = {
                Text(
                    L.t("拉麵手機版以 MIT 授權釋出。角色、動作示範與說明來自 Noodle（© 2026 Pete Cybriwsky，MIT 授權）；這是非官方改寫版，與原作者無關。", "Lamian for Android is released under the MIT License. The characters, exercise demos and instructions come from Noodle (© 2026 Pete Cybriwsky, MIT License). This is an unofficial port, not affiliated with the original author."),
                    style = T.bodySm, color = T.inkMuted,
                )
            },
            confirmButton = { PrimaryButton(L.t("關閉", "Close")) { license = false } },
            containerColor = T.surface1,
        )
    }
}

/** 時段：兩個時間按鈕，跨欄位檢查不通過就不套用並顯示原因。 */
@Composable
private fun TimeRange(label: String, from: Int, to: Int, check: (Int, Int) -> String?, onCommit: (Int, Int) -> Unit) {
    val context = LocalContext.current
    var err by remember(from, to) { mutableStateOf<String?>(null) }
    fun tryCommit(f: Int, t: Int) {
        err = check(f, t)
        if (err == null) onCommit(f, t)
    }
    Column {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = T.sSm), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = T.bodySm, color = T.ink, modifier = Modifier.weight(1f))
            TimeButton(from, L.t("${label}開始", "$label start"), error = err != null) { pickTime(context, from) { tryCommit(it, to) } }
            Text("–", style = T.bodySm, color = T.inkSubtle, modifier = Modifier.padding(horizontal = T.sXs))
            TimeButton(to, L.t("${label}結束", "$label end"), error = err != null) { pickTime(context, to) { tryCommit(from, it) } }
        }
        ErrorText(err, Modifier.padding(bottom = T.sXs))
    }
}

@Composable
private fun RowScope.CharChip(id: String, name: String, kind: String, selected: Boolean, sprites: SpriteCache, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).heightIn(min = T.touch).clip(RoundedCornerShape(T.rMd))
            .background(if (selected) T.surface3 else T.surface1)
            .border(1.dp, if (selected) T.primary else T.hairlineStrong, RoundedCornerShape(T.rMd))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$kind $name" }
            .padding(vertical = T.sXxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StillPet(sprites, id, "idle", Modifier.size(40.dp))
        Text(kind, style = T.caption, color = if (selected) T.ink else T.inkSubtle)
    }
}

