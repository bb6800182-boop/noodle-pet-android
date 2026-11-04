package com.c520.lamian.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.c520.lamian.ui.PrimaryButton
import com.c520.lamian.ui.SecondaryButton
import com.c520.lamian.ui.T
import com.c520.lamian.ui.TextLink
import kotlinx.coroutines.delay

/** 泡泡上要顯示的內容與狀態；PetOverlay 改這些欄位，畫面自動更新。 */
class BubbleState {
    var eyebrow by mutableStateOf("")
    var meta by mutableStateOf("")
    var title by mutableStateOf("")
    var line by mutableStateOf("")
    var spec by mutableStateOf("")
    var steps by mutableStateOf(emptyList<String>())
    var tip by mutableStateOf("")
    var primary by mutableStateOf("")
    var secondary by mutableStateOf("")
    /** 第三個文字按鈕；有動作步驟時固定是「怎麼做」 */
    var link by mutableStateOf<String?>(null)
    var hasHowto by mutableStateOf(false)
    var stepsOpen by mutableStateOf(false)
    var timing by mutableStateOf(false)
    var done by mutableStateOf(false)
    var remainingMs by mutableLongStateOf(0L)
    var totalMs by mutableLongStateOf(1L)
    var peeking by mutableStateOf(false)
    var shown by mutableStateOf(false)
    var rightSide by mutableStateOf(true)
    var tailFromStart by mutableStateOf(48.dp)
    var peekBack by mutableStateOf<ImageBitmap?>(null)
    var peekFront by mutableStateOf<ImageBitmap?>(null)
    var reduceMotion by mutableStateOf(false)
    /** 泡泡本體最高多高（橫向螢幕時放不下就捲動） */
    var maxBox by mutableStateOf(480.dp)

    var onPrimary: () -> Unit = {}
    var onSecondary: () -> Unit = {}
    var onLink: () -> Unit = {}
    var onTouch: () -> Unit = {}
}

private val PEEK = 104.dp
/** 泡泡上方留給探頭的空間：探頭時頭頂最高到圖高的 12.5%（直麵），手搭在泡泡上緣（43.5%） */
val PEEK_ROOM = 34.dp

@Composable
fun BubbleContent(s: BubbleState) {
    val alpha by animateFloatAsState(if (s.shown) 1f else 0f, tween(if (s.reduceMotion) 120 else 220), label = "a")
    val scale by animateFloatAsState(if (s.shown || s.reduceMotion) 1f else 0.94f, tween(220), label = "s")
    val peek = remember { Animatable(0f) }
    LaunchedEffect(s.peeking) {
        if (s.peeking) {
            peek.snapTo(0f); peek.animateTo(1f, tween(600)); delay(1200); peek.animateTo(0f, tween(600))
        } else peek.snapTo(0f)
    }
    val density = LocalDensity.current
    val tailPx = with(density) { s.tailFromStart.toPx() }
    Column(
        Modifier
            .graphicsLayer {
                this.alpha = alpha; scaleX = scale; scaleY = scale
                transformOrigin = TransformOrigin(if (size.width > 0f) (tailPx / size.width).coerceIn(0f, 1f) else 0.5f, 1f)
            }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); s.onTouch() } } },
    ) {
        Box(Modifier.fillMaxWidth().padding(top = PEEK_ROOM)) {
            val hidden = PEEK * 0.5f
            val peekX = Modifier.align(if (s.rightSide) Alignment.TopEnd else Alignment.TopStart)
                .padding(start = if (s.rightSide) 0.dp else 14.dp, end = if (s.rightSide) 14.dp else 0.dp)
            // 探頭：身體在泡泡後面（先畫），手在泡泡前面（後畫）
            s.peekBack?.let {
                // 動畫值只在 layout／draw 階段讀，不讓整個泡泡每一格都重組
                Image(
                    it, null,
                    peekX.offset { IntOffset(0, (-(PEEK * 0.435f) + hidden * (1f - peek.value)).roundToPx()) }.size(PEEK)
                        .graphicsLayer { this.alpha = if (peek.value > 0f) 1f else 0f },
                )
            }
            BubbleBox(s)
            s.peekFront?.let {
                Image(it, null, peekX.offset(y = -(PEEK * 0.435f)).size(PEEK).graphicsLayer { this.alpha = if (peek.value > 0.9f) 1f else 0f })
            }
        }
        Tail(s.tailFromStart)
    }
}

@Composable
private fun BubbleBox(s: BubbleState) {
    val shape = RoundedCornerShape(T.rLg)
    Column(
        Modifier.fillMaxWidth().heightIn(max = s.maxBox).clip(shape).background(T.surface1).border(1.dp, T.hairlineStrong, shape)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.padding(start = T.sMd, end = T.sMd, top = T.sMd, bottom = T.sXxs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.eyebrow, style = T.eyebrow, color = T.inkSubtle, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(s.meta, style = T.eyebrow, color = T.inkSubtle)
            }
            Row(Modifier.padding(vertical = T.sXxs), verticalAlignment = Alignment.CenterVertically) {
                if (s.done) { CheckBadge(); Spacer(Modifier.width(T.sXs)) }
                Text(s.title, style = T.cardTitle, color = T.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (s.line.isNotEmpty()) Text(s.line, style = T.bodySm, color = T.inkMuted)
            if (s.spec.isNotEmpty() && !s.done) {
                Row(Modifier.fillMaxWidth().padding(top = T.sXs), verticalAlignment = Alignment.Bottom) {
                    Text(s.spec, style = T.bodySm, color = T.inkSubtle, modifier = Modifier.weight(1f))
                    if (s.timing) Text(mmss(s.remainingMs), style = T.headline.copy(fontFeatureSettings = "tnum"), color = T.ink)
                }
            }
        }
        if (s.timing || s.done) {
            val f = if (s.done) 1f else (1f - s.remainingMs.toFloat() / s.totalMs).coerceIn(0f, 1f)
            Box(Modifier.padding(start = T.sMd, end = T.sMd, top = T.sXs, bottom = if (s.done) T.sMd else 0.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(T.hairline)) {
                Box(Modifier.fillMaxWidth(f).height(4.dp).background(if (s.done) T.success else T.primary))
            }
        }
        if (!s.done) {
            Row(Modifier.fillMaxWidth().padding(start = T.sMd, end = T.sMd, top = T.sSm, bottom = T.sMd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(T.sXs)) {
                PrimaryButton(s.primary, onClick = s.onPrimary)
                SecondaryButton(s.secondary, onClick = s.onSecondary)
                Spacer(Modifier.weight(1f))
                if (s.hasHowto) TextLink(if (s.stepsOpen) "收起步驟" else "怎麼做") { s.stepsOpen = !s.stepsOpen }
                else s.link?.let { TextLink(it, onClick = s.onLink) }
            }
        }
        if (s.hasHowto && s.stepsOpen && !s.done) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(T.hairline))
            Column(Modifier.padding(start = T.sMd, end = T.sMd, top = T.sSm, bottom = T.sMd), verticalArrangement = Arrangement.spacedBy(T.sXs)) {
                s.steps.forEachIndexed { i, step ->
                    Row {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(T.surface3), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = T.caption, color = T.ink)
                        }
                        Spacer(Modifier.width(T.sXs))
                        Text(step, style = T.bodySm, color = T.inkMuted)
                    }
                }
                if (s.tip.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(T.rMd)).background(T.surface3).padding(horizontal = T.sSm, vertical = T.sXs)) {
                        Text("注意　${s.tip}", style = T.caption, color = T.inkMuted)
                    }
                }
                Text("動作只是一般建議；覺得痛或不舒服就停下來。", style = T.caption, color = T.inkSubtle)
            }
        }
    }
}

@Composable
private fun CheckBadge() {
    Canvas(Modifier.size(22.dp)) {
        drawCircle(T.success)
        val p = Path().apply {
            moveTo(size.width * 0.30f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.66f)
            lineTo(size.width * 0.72f, size.height * 0.37f)
        }
        drawPath(p, T.onPrimary, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** 泡泡下面指向麵條頭頂的小尖角；往上蓋住 1dp 外框。 */
@Composable
private fun Tail(fromStart: Dp) {
    Canvas(Modifier.offset(x = fromStart - 8.dp, y = (-1).dp).size(16.dp, 8.dp)) {
        val path = Path().apply { moveTo(0f, 0f); lineTo(size.width / 2, size.height); lineTo(size.width, 0f); close() }
        drawPath(path, T.surface1)
        val stroke = 1.dp.toPx()
        drawLine(T.hairlineStrong, Offset(0f, 0f), Offset(size.width / 2, size.height), stroke)
        drawLine(T.hairlineStrong, Offset(size.width / 2, size.height), Offset(size.width, 0f), stroke)
    }
}

fun mmss(ms: Long): String {
    val s = ((ms + 999) / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** 讓 ComposeView 能放在 WindowManager 的懸浮視窗裡（服務沒有 Activity 的 lifecycle）。 */
class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    init {
        controller.performAttach()
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() { registry.currentState = Lifecycle.State.RESUMED }
    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
}
