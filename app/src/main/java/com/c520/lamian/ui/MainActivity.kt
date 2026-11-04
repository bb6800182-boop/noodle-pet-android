package com.c520.lamian.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.c520.lamian.data.Store
import com.c520.lamian.data.WEEK
import com.c520.lamian.overlay.SpriteCache
import com.c520.lamian.service.PetService
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(T.canvas.toArgb()), SystemBarStyle.dark(T.canvas.toArgb()))
        super.onCreate(savedInstanceState)
        PetService.start(this)
        val sprites = SpriteCache(this)
        setContent { LamianTheme { App(sprites) } }
    }
}

/** 三個要使用者手動打開的權限。 */
data class Perms(val notify: Boolean, val overlay: Boolean, val battery: Boolean) {
    companion object {
        fun read(c: Context) = Perms(
            notify = c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            overlay = Settings.canDrawOverlays(c),
            battery = c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName),
        )
    }
}

fun openOverlaySettings(c: Context) = launch(c, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}")))

fun openNotificationSettings(c: Context) = launch(c, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName))

/** 直接跳「不限制電池」的系統詢問；部分品牌沒有這個畫面就改開清單。 */
fun openBatterySettings(c: Context) {
    if (!launch(c, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}")))) {
        launch(c, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

private fun launch(c: Context, i: Intent): Boolean = try {
    c.startActivity(i); true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

private data class Tab(val label: String, val icon: ImageVector)

private fun strokeIcon(vararg paths: String): ImageVector = ImageVector.Builder(defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
    paths.forEach {
        addPath(addPathNodes(it), fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }
}.build()

private val TABS = listOf(
    Tab("今天", strokeIcon("M4 11l8-7 8 7", "M6 10v10h12V10")),
    Tab("動作", strokeIcon("M10 5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M5 9l7 2 7-2M12 11v5l-3 5M12 16l3 5")),
    Tab("提醒", strokeIcon("M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z", "M10 20a2 2 0 0 0 4 0")),
    Tab("設定", strokeIcon("M4 7h10M18 7h2M4 17h4M12 17h8", "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")),
)

@Composable
fun App(sprites: SpriteCache) {
    val st by Store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var perms by remember { mutableStateOf(Perms.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        perms = Perms.read(context)
        PetService.instance?.evaluate() ?: PetService.start(context)
    }
    Column(Modifier.fillMaxSize().background(T.canvas).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = T.sMd, vertical = T.sSm), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("拉麵", style = T.cardTitle, color = T.ink)
            val d = LocalDate.now()
            Text("${d.monthValue}月${d.dayOfMonth}日（${WEEK[d.dayOfWeek.value]}）", style = T.caption, color = T.inkSubtle)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> HomeTab(st, perms, sprites, onPerms = { perms = Perms.read(context) })
                1 -> ExerciseTab(st, sprites)
                2 -> ReminderTab(st)
                else -> SettingsTab(st, perms, sprites)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(T.hairline))
        Row(
            Modifier.fillMaxWidth().background(T.surface1)
                .navigationBarsPadding().padding(horizontal = T.sXs, vertical = T.sXxs).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(T.sXxs),
        ) {
            TABS.forEachIndexed { i, t ->
                val on = tab == i
                Column(
                    Modifier.weight(1f).heightIn(min = T.touch).clip(RoundedCornerShape(T.rMd))
                        .selectable(selected = on, role = Role.Tab) { tab = i }.padding(top = 6.dp, bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(52.dp, 28.dp).clip(RoundedCornerShape(50)).background(if (on) T.surface3 else Color.Transparent), contentAlignment = Alignment.Center) {
                        Icon(t.icon, contentDescription = null, tint = if (on) T.ink else T.inkSubtle, modifier = Modifier.size(20.dp))
                    }
                    Text(t.label, style = T.caption, color = if (on) T.ink else T.inkSubtle, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
