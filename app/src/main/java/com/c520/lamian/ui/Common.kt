package com.c520.lamian.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.c520.lamian.R
import com.c520.lamian.data.Store
import com.c520.lamian.data.hhmm
import com.c520.lamian.overlay.SpriteCache
import com.c520.lamian.overlay.SpriteView
import com.c520.lamian.service.PetService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 會動的麵條（App 首頁）。 */
@Composable
fun AnimatedPet(cache: SpriteCache, character: String, pose: String, modifier: Modifier = Modifier) {
    AndroidView(factory = { SpriteView(it, cache) }, modifier = modifier, update = { it.play(character, pose) })
}

/** 靜態縮圖：背景執行緒解碼第一格。 */
@Composable
fun StillPet(cache: SpriteCache, character: String, pose: String, modifier: Modifier = Modifier) {
    val bmp by produceState<ImageBitmap?>(null, character, pose) {
        value = withContext(Dispatchers.IO) { cache.firstFrame(character, pose)?.asImageBitmap() }
    }
    Box(modifier) { bmp?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) } }
}

fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

/** 服務正常都在跑；總開關關著就提醒先打開；萬一被系統關掉，先叫起來並告訴使用者稍等。 */
fun service(context: Context): PetService? = PetService.instance ?: run {
    if (!Store.current.settings.enabled) {
        toast(context, "拉麵關著，先到「設定」打開")
    } else {
        PetService.start(context)
        toast(context, "麵條還在準備，等一下再試一次")
    }
    null
}

/** 系統原生的時間選擇器（24 小時制）。 */
fun pickTime(context: Context, minuteOfDay: Int, onPick: (Int) -> Unit) {
    TimePickerDialog(context, R.style.Theme_Lamian_Dialog, { _, h, m -> onPick(h * 60 + m) }, minuteOfDay / 60, minuteOfDay % 60, true).show()
}

/** 系統原生的日期選擇器，最早只能選今天。 */
fun pickDate(context: Context, current: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(context, R.style.Theme_Lamian_Dialog, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, current.year, current.monthValue - 1, current.dayOfMonth).apply {
        datePicker.minDate = System.currentTimeMillis() - 1000
    }.show()
}

/** 顯示時間、點了開選擇器的按鈕。 */
@Composable
fun TimeButton(minuteOfDay: Int, label: String, error: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = T.touch).clip(RoundedCornerShape(T.rMd)).background(T.canvas)
            .border(1.dp, if (error) T.danger else T.hairlineStrong, RoundedCornerShape(T.rMd))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label ${hhmm(minuteOfDay)}，點一下修改" }
            .padding(horizontal = T.sSm),
        contentAlignment = Alignment.Center,
    ) { Text(hhmm(minuteOfDay), style = T.body.copy(fontFeatureSettings = "tnum"), color = T.ink) }
}

/** 標題在上、控制項在下（分段選擇等比較寬的控制項用，放大字體也不會擠）。 */
@Composable
fun StackedRow(label: String, hint: String? = null, first: Boolean = false, content: @Composable () -> Unit) {
    Column {
        if (!first) Hairline()
        Column(Modifier.fillMaxWidth().padding(vertical = T.sSm)) {
            Text(label, style = T.bodySm, color = T.ink)
            hint?.let { Text(it, style = T.caption, color = T.inkSubtle, modifier = Modifier.padding(top = 2.dp)) }
            Box(Modifier.padding(top = T.sXs)) { content() }
        }
    }
}

/** 欄位錯誤：紅字，並讓讀螢幕軟體念出來。 */
@Composable
fun ErrorText(msg: String?, modifier: Modifier = Modifier) {
    msg?.let { Text(it, style = T.caption, color = T.danger, modifier = modifier.padding(top = T.sXxs).semantics { liveRegion = LiveRegionMode.Polite }) }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) = Text(text, style = T.caption, color = T.inkSubtle, modifier = modifier)
