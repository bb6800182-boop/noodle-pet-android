package com.c520.lamian.ui

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
        GroupTitle("拉麵")
        Group {
            SettingRow("開啟拉麵", "關掉就完全停止：不提醒，常駐通知和麵條都收起來；之後回這裡打開就恢復", first = true) {
                LSwitch(s.enabled, "開啟拉麵") { on ->
                    PetService.setEnabled(context, on)
                    toast(context, if (on) "拉麵開始提醒了" else "拉麵關掉了")
                }
            }
        }

        GroupTitle("麵條")
        Group {
            StackedRow("平時", "隱藏最省電；探頭會一直貼在螢幕邊", first = true) {
                Segmented(listOf(IdleMode.HIDDEN to "隱藏", IdleMode.PEEK to "側邊探頭"), s.idleMode, "平時") { v -> set { it.copy(idleMode = v) } }
            }
            StackedRow("怎麼出來") {
                Segmented(listOf(Entrance.WALK to "走出來", Entrance.DROP to "垂降"), s.entrance, "怎麼出來") { v -> set { it.copy(entrance = v) } }
            }
            StackedRow("站在") {
                Segmented(listOf(Side.RIGHT to "右下", Side.LEFT to "左下"), s.side, "站在") { v -> set { it.copy(side = v) } }
            }
            StackedRow("大小") {
                Segmented(listOf(ActorSize.S to "小", ActorSize.M to "中", ActorSize.L to "大"), s.actorSize, "大小") { v -> set { it.copy(actorSize = v) } }
            }
            StackedRow("探頭高度", "也可以直接拖麵條") {
                Segmented(listOf(PeekPos.TOP to "上", PeekPos.MIDDLE to "中", PeekPos.BOTTOM to "下"), s.peekPos, "探頭高度") { v -> set { it.copy(peekPos = v) } }
            }
            StackedRow("角色") {
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(T.sXxs)) {
                    CHARACTERS.forEach { (id, name, kind) -> CharChip(id, name, kind, s.character == id, sprites) { set { it.copy(character = id) } } }
                }
            }
        }

        GroupTitle("滑手機提醒")
        Group {
            SettingRow("連續滑手機太久就提醒", first = true) { LSwitch(s.stretchOn, "連續滑手機太久就提醒") { v -> set { it.copy(stretchOn = v) } } }
            SettingRow("連續使用多久提醒") { Stepper(s.intervalMin, Limits.interval, "分鐘", "連續使用多久提醒") { v -> set { it.copy(intervalMin = v) } } }
            SettingRow("關螢幕多久算休息過", "休息過就重新計時") { Stepper(s.breakResetMin, Limits.breakReset, "分鐘", "關螢幕多久算休息過") { v -> set { it.copy(breakResetMin = v) } } }
        }

        GroupTitle("喝水")
        Group {
            SettingRow("提醒喝水", first = true) { LSwitch(s.waterOn, "提醒喝水") { v -> set { it.copy(waterOn = v) } } }
            SettingRow("每隔", "照時鐘算，不是照滑手機的時間") { Stepper(s.waterEveryMin, Limits.waterEvery, "分鐘", "喝水每隔") { v -> set { it.copy(waterEveryMin = v) } } }
            TimeRange("時段", s.waterFrom, s.waterTo, { from, to -> if (from >= to) "開始要早於結束" else null }) { from, to -> set { it.copy(waterFrom = from, waterTo = to) } }
        }

        GroupTitle("泡泡")
        Group {
            SettingRow("按「晚點」延後", first = true) { Stepper(s.snoozeMin, Limits.snooze, "分鐘", "按晚點延後") { v -> set { it.copy(snoozeMin = v) } } }
            SettingRow("沒理多久探頭") {
                Stepper(s.peekAfterSec, Limits.peekAfter, "秒", "沒理多久探頭", extraCheck = { v -> if (v >= s.dismissAfterSec) "探頭要比自己走掉（${s.dismissAfterSec} 秒）早" else null }) { v ->
                    set { it.copy(peekAfterSec = v) }
                }
            }
            SettingRow("沒理多久自己走掉", "自訂提醒不會自己走，要按「知道了」") {
                Stepper(s.dismissAfterSec, Limits.dismissAfter, "秒", "沒理多久自己走掉", extraCheck = { v -> if (v <= s.peekAfterSec) "要比探頭（${s.peekAfterSec} 秒）晚" else null }) { v ->
                    set { it.copy(dismissAfterSec = v) }
                }
            }
            SettingRow("錯過後多久內可以補做", "0 代表不提供補做") { Stepper(s.recallWithinMin, Limits.recallWithin, "分鐘", "錯過後多久內可以補做") { v -> set { it.copy(recallWithinMin = v) } } }
            SettingRow("一出現就展開「怎麼做」") { LSwitch(s.expandSteps, "一出現就展開怎麼做") { v -> set { it.copy(expandSteps = v) } } }
        }

        GroupTitle("不打擾")
        Group {
            SettingRow("通話中先不出來", "伸展、喝水延到掛斷後；自訂提醒改發通知", first = true) { LSwitch(s.holdInCall, "通話中先不出來") { v -> set { it.copy(holdInCall = v) } } }
            SettingRow("安靜時段", "只擋伸展和喝水，自訂提醒照常") { LSwitch(s.quietOn, "安靜時段") { v -> set { it.copy(quietOn = v) } } }
            TimeRange("時段", s.quietFrom, s.quietTo, { from, to -> if (from == to) "開始和結束不能一樣" else null }) { from, to -> set { it.copy(quietFrom = from, quietTo = to) } }
        }

        GroupTitle("系統")
        Group {
            SettingRow("開機自動啟動", first = true) { LSwitch(s.autostart, "開機自動啟動") { v -> set { it.copy(autostart = v) } } }
            SettingRow("顯示在其他應用程式上層", if (perms.overlay) null else "麵條要畫在其他 App 上面才能出來") {
                if (perms.overlay) Pill("已允許", ok = true) else SecondaryButton("去允許") { openOverlaySettings(context) }
            }
            SettingRow("通知", if (perms.notify) null else "螢幕關著時靠通知提醒") {
                if (perms.notify) Pill("已允許", ok = true)
                else SecondaryButton(if (asked) "到設定打開" else "允許") {
                    if (asked) openNotificationSettings(context) else notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            SettingRow("電池用量", "部分品牌要設成「不限制」才不會被關掉") {
                if (perms.battery) Pill("不限制", ok = true) else SecondaryButton("前往設定") { openBatterySettings(context) }
            }
            SettingRow("動畫", "跟著系統的「移除動畫」設定，開了就不走路、直接出現") {}
        }

        GroupTitle("關於")
        Group {
            SettingRow("版本", first = true) { Text("v${BuildConfig.VERSION_NAME}", style = T.bodySm, color = T.inkMuted) }
            SettingRow("授權資訊") { SecondaryButton("查看") { license = true } }
        }
        VSpace(T.sLg)
    }

    if (license) {
        AlertDialog(
            onDismissRequest = { license = false },
            title = { Text("授權資訊", style = T.cardTitle, color = T.ink) },
            text = {
                Text(
                    "拉麵手機版以 MIT 授權釋出。角色、動作示範與說明來自 Noodle（© 2026 Pete Cybriwsky，MIT 授權）；這是非官方改寫版，與原作者無關。",
                    style = T.bodySm, color = T.inkMuted,
                )
            },
            confirmButton = { PrimaryButton("關閉") { license = false } },
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
            TimeButton(from, "${label}開始", error = err != null) { pickTime(context, from) { tryCommit(it, to) } }
            Text("–", style = T.bodySm, color = T.inkSubtle, modifier = Modifier.padding(horizontal = T.sXs))
            TimeButton(to, "${label}結束", error = err != null) { pickTime(context, to) { tryCommit(from, it) } }
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

