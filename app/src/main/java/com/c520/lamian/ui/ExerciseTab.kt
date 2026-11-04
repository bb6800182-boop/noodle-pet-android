package com.c520.lamian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.c520.lamian.data.AppState
import com.c520.lamian.data.DEMO_POSES
import com.c520.lamian.data.Exercise
import com.c520.lamian.data.Limits
import com.c520.lamian.data.Rotation
import com.c520.lamian.data.Store
import com.c520.lamian.data.specLine
import com.c520.lamian.overlay.SpriteCache

@Composable
fun ExerciseTab(st: AppState, sprites: SpriteCache) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var refused by remember { mutableStateOf<String?>(null) }
    val base = st.exercises.filter { !it.proposed }
    val proposed = st.exercises.filter { it.proposed }
    val item: @Composable (Exercise, Boolean) -> Unit = { e, first ->
        ExerciseItem(
            e, first, expanded = open == e.id, refused = refused == e.id, sprites = sprites, character = st.settings.character,
            onOpen = { open = if (open == e.id) null else e.id },
            onToggle = { on -> refused = if (Store.setExerciseEnabled(e.id, on)) null else e.id },
        )
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = T.sMd, vertical = T.sXs)) {
        Hint("提醒時照順序輪流出現；關掉的不會出現。點名稱可以改秒數、說明和順序。")
        VSpace(T.sSm)
        Group {
            SettingRow("輪替方式", first = true) {
                Segmented(listOf(Rotation.ORDER to "依序", Rotation.RANDOM to "隨機"), st.settings.rotation, "輪替方式") { v ->
                    Store.updateSettings { it.copy(rotation = v) }
                }
            }
        }
        GroupTitle("現有動作（${base.size}）")
        Group { base.forEachIndexed { i, e -> item(e, i == 0) } }
        GroupTitle("提案：適合滑手機的新動作")
        Hint("還沒有專屬的示範動畫；打開的話，麵條會站著用文字帶你做。")
        VSpace(T.sXs)
        Group { proposed.forEachIndexed { i, e -> item(e, i == 0) } }
        VSpace(T.sLg)
    }
}

@Composable
private fun ExerciseItem(
    e: Exercise, first: Boolean, expanded: Boolean, refused: Boolean, sprites: SpriteCache, character: String,
    onOpen: () -> Unit, onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Column {
        if (!first) Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = T.sXs), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (e.proposed || e.id !in DEMO_POSES) Box(Modifier.size(28.dp).clip(CircleShape).border(1.dp, T.hairlineStrong, CircleShape))
                else StillPet(sprites, character, e.id, Modifier.requiredSize(56.dp))
            }
            Spacer(Modifier.width(T.sSm))
            Column(
                Modifier.weight(1f).heightIn(min = T.touch).clickable(role = Role.Button, onClickLabel = if (expanded) "收起" else "修改") { onOpen() }
                    .semantics { stateDescription = if (expanded) "已展開" else "已收合" },
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.name, style = T.bodySm.copy(fontWeight = FontWeight.Medium), color = T.ink)
                    if (e.proposed) { Spacer(Modifier.width(T.sXs)); Pill("沒有示範動畫") }
                }
                Text(specLine(e), style = T.caption, color = T.inkSubtle)
            }
            LSwitch(e.enabled, "${e.name}，加入輪替", onToggle)
        }
        if (refused) ErrorText("至少要留一個動作在輪替裡")
        if (expanded) {
            Column(Modifier.padding(start = 60.dp, bottom = T.sSm), verticalArrangement = Arrangement.spacedBy(T.sXs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("秒數", style = T.bodySm, color = T.ink, modifier = Modifier.weight(1f))
                    Stepper(e.seconds, Limits.exerciseSeconds, "秒", "${e.name}的秒數") { Store.setExerciseSeconds(e.id, it) }
                }
                SpecField(e)
                Row(horizontalArrangement = Arrangement.spacedBy(T.sXs)) {
                    SecondaryButton("往上移") { if (!Store.moveExercise(e.id, -1)) toast(context, "已經是第一個") }
                    SecondaryButton("往下移") { if (!Store.moveExercise(e.id, 1)) toast(context, "已經是最後一個") }
                }
            }
        }
    }
}

@Composable
private fun SpecField(e: Exercise) {
    var text by remember(e.id) { mutableStateOf(e.spec) }
    val err = if (text.isBlank()) "次數說明不能空白" else null
    Column {
        Text("次數說明", style = T.bodySm, color = T.ink)
        BasicTextField(
            value = text,
            onValueChange = { v ->
                text = v.take(Limits.SPEC_TEXT_MAX)
                if (text.isNotBlank()) Store.setExerciseSpec(e.id, text)
            },
            singleLine = true,
            textStyle = T.bodySm.copy(color = T.ink),
            cursorBrush = SolidColor(T.primary),
            modifier = Modifier.padding(top = T.sXxs).fillMaxWidth().heightIn(min = T.touch).clip(RoundedCornerShape(T.rMd)).background(T.canvas)
                .border(1.dp, if (err != null) T.danger else T.hairlineStrong, RoundedCornerShape(T.rMd))
                .padding(horizontal = T.sSm, vertical = T.sSm)
                .semantics { contentDescription = "${e.name}的次數說明，最多 ${Limits.SPEC_TEXT_MAX} 字" },
        )
        ErrorText(err)
    }
}
