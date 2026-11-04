package com.c520.lamian.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.c520.lamian.data.AppState
import com.c520.lamian.data.DEMO_POSES
import com.c520.lamian.data.Entrance
import com.c520.lamian.data.PeekPos
import com.c520.lamian.data.Side
import com.c520.lamian.data.Store
import com.c520.lamian.data.hhmm
import com.c520.lamian.data.specLine
import com.c520.lamian.data.statusLine
import com.c520.lamian.service.PetService
import com.c520.lamian.ui.LamianTheme
import com.c520.lamian.ui.T
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 畫在其他 App 上面的麵條：走出來／垂降、示範動作、泡泡、沒理時探頭、做完跳一下再走掉；平時可以貼在側邊探頭。
 * 只在提醒時才建立視窗，收起來就全部移除（不常駐、不耗電）。所有方法都在主執行緒呼叫。
 */
class PetOverlay(private val svc: PetService) {
    enum class Result { DONE, DRANK, SNOOZED, SKIPPED, IGNORED, ACK }
    private enum class Phase { NUDGE, TIMING, DONE }

    /** 視窗用的 context：跟著螢幕旋轉更新尺寸 */
    private val ctx: Context = svc.createWindowContext(
        svc.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null,
    )
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val cache = SpriteCache(ctx)
    private val main = MainScope()
    private val density get() = ctx.resources.displayMetrics.density
    private fun px(dp: Float) = (dp * density).roundToInt()
    private val reduce get() = !ValueAnimator.areAnimatorsEnabled()

    private var actorRoot: FrameLayout? = null
    private var actor: SpriteView? = null
    private var actorLp: WindowManager.LayoutParams? = null
    private val bubble = BubbleState()
    private var bubbleView: ComposeView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var owner: OverlayOwner? = null
    private var strand: View? = null
    private var strandLp: WindowManager.LayoutParams? = null
    private var edge: EdgePetView? = null
    private var edgeLp: WindowManager.LayoutParams? = null
    private var edgeKey = ""
    private var dragView: SpriteView? = null
    private var dragLp: WindowManager.LayoutParams? = null

    private var spec: ReminderSpec? = null
    private var phase = Phase.NUDGE
    private var closing = false
    private var peeked = false
    private var character = "rigatoni"
    private var geo: Geo? = null
    private var scene: Job? = null
    private var nudge: Job? = null
    private var countdown: Job? = null
    private var attentionJob: Job? = null

    /** 麵條在外面（含正在走掉）就算忙：新的提醒要排隊 */
    val busy: Boolean get() = spec != null || actorRoot != null
    val current: ReminderSpec? get() = spec

    private val configWatcher = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = refresh()
        @Deprecated("Deprecated in Java") override fun onLowMemory() = cache.trim()
    }

    init {
        ctx.registerComponentCallbacks(configWatcher)
        bubble.onPrimary = ::onPrimary
        bubble.onSecondary = ::onSecondary
        bubble.onLink = ::onLink
        bubble.onTouch = { if (phase == Phase.NUDGE && !closing) restartNudge() }
    }

    fun destroy() {
        hideNow()
        ctx.unregisterComponentCallbacks(configWatcher)
        main.cancel()
    }

    // ---------------- 版面 ----------------

    private class Geo(
        val right: Boolean, val a: Int, val hop: Int,
        val homeX: Int, val offX: Int, val winY: Int,
        val bubbleW: Int, val bubbleX: Int, val bubbleBottom: Int,
        val tailDp: Float, val maxBoxDp: Float,
    )

    private fun measure(): Geo {
        val s = Store.current.settings
        val m = wm.currentWindowMetrics
        val b = m.bounds
        val ins = m.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
        val top = m.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
        val w = b.width()
        val h = b.height()
        val right = s.side == Side.RIGHT
        val a = px(s.actorSize.dp.toFloat())
        val hop = px(16f)
        val homeX = if (right) w - ins.right - a - px(6f) else ins.left + px(6f)
        val offX = if (right) w + px(8f) else -a - px(8f)
        val spriteTop = h - ins.bottom - px(2f) - a
        val bubbleW = min(px(336f), w - ins.left - ins.right - px(24f))
        val bubbleX = if (right) w - ins.right - bubbleW - px(12f) else ins.left + px(12f)
        // 泡泡底（尖角）落在麵條圖框頂端；用 BOTTOM 對齊，展開步驟時往上長
        val bubbleBottom = h - (spriteTop + px(2f))
        val tail = ((homeX + a / 2f - bubbleX) / density).coerceIn(20f, bubbleW / density - 20f)
        val maxBox = ((spriteTop - top) / density) - PEEK_ROOM.value - 16f
        return Geo(right, a, hop, homeX, offX, spriteTop - hop, bubbleW, bubbleX, bubbleBottom, tail, max(160f, maxBox))
    }

    private fun lp(w: Int, h: Int, touchable: Boolean = true) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        setFitInsetsTypes(0)
    }

    /** 權限被收回、視窗加不上去時回傳 false，不讓整個服務當掉 */
    private fun add(v: View, p: WindowManager.LayoutParams): Boolean = runCatching { wm.addView(v, p) }.isSuccess
    private fun remove(v: View?) { if (v != null) runCatching { wm.removeViewImmediate(v) } }
    private fun update(v: View?, p: WindowManager.LayoutParams?) { if (v != null && p != null) runCatching { wm.updateViewLayout(v, p) } }

    // ---------------- 顯示一張提醒 ----------------

    /** 回傳 false 代表沒辦法顯示（正在忙、沒有權限），呼叫端改排隊或發通知。 */
    fun show(next: ReminderSpec): Boolean {
        if (spec != null || !Settings.canDrawOverlays(svc)) return false
        val st = Store.current
        val g = measure()
        spec = next; phase = Phase.NUDGE; closing = false; peeked = false; geo = g
        if (actorRoot == null) character = st.settings.character
        fill(next, st, g)
        scene?.cancel()
        scene = main.launch {
            if (actorRoot == null && !enter(g, st.settings.entrance)) { failed(next); return@launch }
            pose(demoPose(next))
            if (!addBubble(g)) { failed(next); return@launch }
            delay(32)
            bubble.shown = true
            if (st.settings.expandSteps && bubble.hasHowto) bubble.stepsOpen = true
            restartNudge()
            if (next is ReminderSpec.Say) {
                delay(1800)
                if (phase == Phase.NUDGE && !closing) pose("idle")
            }
        }
        return true
    }

    private fun failed(sp: ReminderSpec) {
        hideNow()
        svc.onOverlayFailed(sp)
    }

    private fun fill(sp: ReminderSpec, st: AppState, g: Geo) {
        val now = System.currentTimeMillis()
        val tag = sp.reminder?.let { "提醒・${hhmm(it.minuteOfDay)}" }
        with(bubble) {
            shown = false; done = false; timing = false; stepsOpen = false; peeking = false
            meta = ""; line = ""; spec = ""; steps = emptyList(); tip = ""; link = null; hasHowto = false
            rightSide = g.right; tailFromStart = g.tailDp.dp; maxBox = g.maxBoxDp.dp
            reduceMotion = reduce
            peekBack = cache.sheet(character, "peek-back")?.asImageBitmap()
            peekFront = cache.sheet(character, "peek-front")?.asImageBitmap()
            when (sp) {
                is ReminderSpec.Water -> {
                    eyebrow = tag ?: "喝水時間"; meta = "今天第 ${st.today.water + 1} 杯"
                    title = "喝口水吧！"; line = "起來倒杯水，順便走一走、看看遠方。"
                    primary = "喝了"; secondary = "晚點"
                }
                is ReminderSpec.Say -> {
                    eyebrow = "提醒"; meta = hhmm(sp.reminder.minuteOfDay)
                    title = sp.reminder.text; primary = "知道了"; secondary = "晚點"
                }
                ReminderSpec.Visit -> {
                    eyebrow = "拉麵"; meta = hhmm(now); title = "嗨！"; line = statusLine(st, now)
                    primary = "現在做一組"; secondary = "喝了一杯"
                    link = if ((st.pausedUntil ?: 0L) > now) "恢復提醒" else "暫停 1 小時"
                }
                is ReminderSpec.Stretch -> {
                    val e = sp.exercise
                    val on = Store.enabledExercises(st)
                    val i = on.indexOfFirst { it.id == e.id }
                    val used = st.live.usedSince?.let { ((now - it) / 60_000L).toInt() } ?: 0
                    eyebrow = tag ?: if (sp.manual) "現在做一組" else "已經滑了 ${max(used, st.settings.intervalMin)} 分鐘"
                    meta = if (tag != null || i < 0) "" else "${i + 1}/${on.size}"
                    title = e.name; line = e.instruction; spec = specLine(e)
                    steps = e.steps; tip = e.tip; hasHowto = e.steps.isNotEmpty()
                    primary = "開始"; secondary = "晚點"
                    totalMs = e.seconds * 1000L; remainingMs = totalMs
                }
            }
        }
    }

    private fun demoPose(sp: ReminderSpec): String = when (sp) {
        is ReminderSpec.Water -> "drink"
        is ReminderSpec.Say, ReminderSpec.Visit -> "wave"
        is ReminderSpec.Stretch -> sp.exercise.id.takeIf { !sp.exercise.proposed && it in DEMO_POSES } ?: "idle"
    }

    private fun pose(name: String, mirror: Boolean = false) = actor?.play(character, name, mirror)

    // ---------------- 進場、退場 ----------------

    private suspend fun enter(g: Geo, entrance: Entrance): Boolean {
        hideEdgeAnimated()
        if (reduce) {
            if (!addActor(g, g.homeX, g.winY)) return false
            pose("wave")
            actorRoot?.alpha = 0f
            animate(250) { actorRoot?.alpha = it }
            return true
        }
        if (entrance == Entrance.DROP) {
            val startY = -(g.a + g.hop + px(8f))
            val handY = g.hop + (0.08f * g.a).roundToInt()
            addStrand(g)
            if (!addActor(g, g.homeX, startY)) { removeStrand(); return false }
            pose("hang")
            animate(1300, PathInterpolator(0.2f, 0.8f, 0.2f, 1f)) { t ->
                val y = lerp(startY, g.winY, t)
                moveActor(g.homeX, y)
                setStrand(y + handY)
            }
            pose("wave")
            hop()
            val full = g.winY + handY
            animate(320, AccelerateInterpolator()) { t -> setStrand((full * (1 - t)).roundToInt()) }
            removeStrand()
        } else {
            if (!addActor(g, g.offX, g.winY)) return false
            pose("walk", mirror = g.right) // 圖是面向右；從右邊進來要往左走
            animate(1200) { t -> moveActor(lerp(g.offX, g.homeX, t), g.winY) }
            pose("wave")
        }
        delay(650)
        return true
    }

    private suspend fun exitActor() {
        val root = actorRoot ?: return
        val g = geo
        if (reduce || g == null) {
            animate(220) { root.alpha = 1 - it }
        } else {
            pose("walk", mirror = !g.right)
            val from = actorLp?.x ?: g.homeX
            val y = actorLp?.y ?: g.winY
            animate(1000) { t -> moveActor(lerp(from, g.offX, t), y) }
        }
        removeActor()
        cache.trim()
    }

    private fun addActor(g: Geo, x: Int, y: Int): Boolean {
        removeActor()
        val sv = SpriteView(ctx, cache)
        val root = FrameLayout(ctx).apply {
            addView(sv, FrameLayout.LayoutParams(g.a, g.a, Gravity.BOTTOM))
            contentDescription = "拉麵"
            setOnClickListener {
                hop()
                if (phase == Phase.NUDGE && !closing) restartNudge()
            }
        }
        val p = lp(g.a, g.a + g.hop).apply { this.x = x; this.y = y }
        if (!add(root, p)) return false
        actorRoot = root; actor = sv; actorLp = p
        return true
    }

    private fun moveActor(x: Int, y: Int) {
        val p = actorLp ?: return
        if (p.x == x && p.y == y) return
        p.x = x; p.y = y
        update(actorRoot, p)
    }

    private fun removeActor() {
        remove(actorRoot)
        actorRoot = null; actor = null; actorLp = null
    }

    private fun addStrand(g: Geo) {
        val v = View(ctx).apply {
            background = GradientDrawable().apply { setColor(T.illNoodle.toArgb()); cornerRadius = px(2f).toFloat() }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val p = lp(px(4f), 1, touchable = false).apply { x = g.homeX + g.a / 2 - px(2f); y = 0 }
        if (add(v, p)) { strand = v; strandLp = p }
    }

    private fun setStrand(h: Int) {
        val p = strandLp ?: return
        p.height = max(1, h)
        update(strand, p)
    }

    private fun removeStrand() {
        remove(strand)
        strand = null; strandLp = null
    }

    /** 跳一下（點麵條、做完、落地）。 */
    private fun hop() {
        val v = actor ?: return
        val h = px(16f).toFloat()
        ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, -h, 0f, -h / 2, 0f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    // ---------------- 泡泡 ----------------

    private fun addBubble(g: Geo): Boolean {
        removeBubble()
        val o = OverlayOwner()
        val v = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            accessibilityPaneTitle = "拉麵：${bubble.title}"
            setContent { LamianTheme { BubbleContent(bubble) } }
        }
        o.resume()
        val p = lp(g.bubbleW, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = g.bubbleX
            y = g.bubbleBottom
        }
        if (!add(v, p)) { o.destroy(); return false }
        bubbleView = v; bubbleLp = p; owner = o
        return true
    }

    private fun removeBubble() {
        remove(bubbleView)
        owner?.destroy()
        bubbleView = null; bubbleLp = null; owner = null
    }

    private fun keepScreenOn(on: Boolean) {
        val p = bubbleLp ?: return
        val f = if (on) p.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else p.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        if (f == p.flags) return
        p.flags = f
        update(bubbleView, p)
    }

    private fun onPrimary() {
        val sp = spec ?: return
        if (closing) return
        when (sp) {
            is ReminderSpec.Stretch -> when (phase) {
                Phase.NUDGE -> startTiming(sp)
                Phase.TIMING -> finish(Result.DONE)
                Phase.DONE -> Unit
            }
            is ReminderSpec.Water -> finish(Result.DRANK)
            is ReminderSpec.Say -> finish(Result.ACK)
            ReminderSpec.Visit -> morph(ReminderSpec.Stretch(Store.nextExercise(), manual = true))
        }
    }

    private fun onSecondary() {
        val sp = spec ?: return
        if (closing) return
        when (sp) {
            is ReminderSpec.Stretch -> when (phase) {
                Phase.NUDGE -> finish(Result.SNOOZED)
                Phase.TIMING -> finish(Result.SKIPPED)
                Phase.DONE -> Unit
            }
            is ReminderSpec.Water, is ReminderSpec.Say -> finish(Result.SNOOZED)
            ReminderSpec.Visit -> {
                svc.recordWaterNow("補記一杯")
                stopTimers()
                celebrate("記上了！", "今天第 ${Store.current.today.water} 杯。", "drink")
                leave(true)
            }
        }
    }

    /** 第三個按鈕：只有打招呼泡泡有（暫停／恢復）。 */
    private fun onLink() {
        if (spec != ReminderSpec.Visit || closing) return
        stopTimers()
        val until = svc.togglePause(quiet = true)
        if (until != null) celebrate("好，先暫停", "暫停到 ${hhmm(until)}，自訂提醒照常。", "wave")
        else celebrate("恢復提醒", "接下來照常提醒你。", "wave")
        leave(true)
    }

    private fun startTiming(sp: ReminderSpec.Stretch) {
        phase = Phase.TIMING
        nudge?.cancel()
        val t = Store.current.today
        val total = sp.exercise.seconds * 1000L
        with(bubble) {
            eyebrow = "跟著做"; meta = "今天第 ${t.stretch + t.walk + 1} 組"
            primary = "完成"; secondary = "跳過"
            totalMs = total; remainingMs = total; timing = true
        }
        pose(demoPose(sp))
        keepScreenOn(true)
        val end = SystemClock.elapsedRealtime() + total
        countdown?.cancel()
        countdown = main.launch {
            while (true) {
                delay(250)
                val left = end - SystemClock.elapsedRealtime()
                bubble.remainingMs = left.coerceAtLeast(0)
                if (left <= 0) { finish(Result.DONE); break }
            }
        }
    }

    private fun finish(r: Result) {
        val sp = spec ?: return
        if (closing) return
        stopTimers()
        keepScreenOn(false)
        svc.onResult(sp, r)
        val t = Store.current.today
        when (r) {
            Result.DONE -> { celebrate("做完了！", "今天第 ${t.stretch + t.walk} 組。", "wave"); leave(true) }
            Result.DRANK -> { celebrate("喝了！", "今天第 ${t.water} 杯。", "drink"); leave(true) }
            else -> leave(false)
        }
    }

    private fun celebrate(title: String, line: String, poseName: String) {
        phase = Phase.DONE
        with(bubble) {
            timing = false; done = true; stepsOpen = false; peeking = false
            eyebrow = "已記錄"; meta = ""
            this.title = title; this.line = line
        }
        actorRoot?.alpha = 1f
        pose(poseName)
        hop()
    }

    private fun leave(celebrated: Boolean) {
        closing = true
        stopTimers()
        keepScreenOn(false)
        bubble.peeking = false
        actorRoot?.alpha = 1f
        scene?.cancel()
        scene = main.launch {
            if (celebrated) delay(1500) else { pose("wave"); delay(450) }
            bubble.shown = false
            delay(240)
            removeBubble()
            spec = null
            exitActor()
            svc.onOverlayClosed()
        }
    }

    /** 打招呼泡泡按「現在做一組」：麵條不走，泡泡換成伸展。 */
    private fun morph(next: ReminderSpec) {
        closing = true
        stopTimers()
        bubble.shown = false
        scene?.cancel()
        scene = main.launch {
            delay(240)
            removeBubble()
            spec = null
            if (!show(next)) svc.onOverlayFailed(next)
        }
    }

    // ---------------- 計時 ----------------

    /** 沒理多久探頭、多久自己走掉；碰到泡泡就重算。自訂提醒不會自己走。 */
    private fun restartNudge() {
        nudge?.cancel()
        val sp = spec ?: return
        if (closing || phase != Phase.NUDGE || sp.sticky) return
        val s = Store.current.settings
        nudge = main.launch {
            if (sp == ReminderSpec.Visit) {
                delay(8000)
            } else {
                val peekAt = s.peekAfterSec * 1000L
                val dismissAt = s.dismissAfterSec * 1000L
                if (!peeked) {
                    delay(peekAt)
                    peeked = true
                    attention()
                    delay((dismissAt - peekAt).coerceAtLeast(0))
                } else delay(dismissAt)
            }
            finish(Result.IGNORED)
        }
    }

    private fun attention() {
        if (reduce) { hop(); return }
        attentionJob?.cancel()
        attentionJob = main.launch {
            val root = actorRoot
            animate(200) { root?.alpha = 1 - it }
            bubble.peeking = true
            delay(2400)
            bubble.peeking = false
            animate(200) { root?.alpha = it }
        }
    }

    private fun stopTimers() {
        nudge?.cancel(); nudge = null
        countdown?.cancel(); countdown = null
        attentionJob?.cancel(); attentionJob = null
    }

    // ---------------- 螢幕關了、設定改了 ----------------

    /** 立刻收掉全部視窗。回傳進行到一半的提醒（打招呼、已完成的不算），讓服務解鎖後再排。 */
    fun hideNow(): ReminderSpec? {
        val sp = spec?.takeIf { it != ReminderSpec.Visit && phase != Phase.DONE && !closing }
        scene?.cancel(); scene = null
        stopTimers()
        removeBubble(); removeActor(); removeStrand(); removeDrag(); removeEdge()
        spec = null; closing = false
        cache.trim()
        return sp
    }

    /** 設定或螢幕方向改了：重新排位置。 */
    fun refresh() {
        val s = Store.current.settings
        if (edge != null && edgeKey != edgeKeyOf()) { removeEdge(); showEdge(animated = false) }
        val root = actorRoot
        if (root == null || spec == null || closing) return
        val g = measure()
        geo = g
        actorLp?.let { p ->
            p.width = g.a; p.height = g.a + g.hop; p.x = g.homeX; p.y = g.winY
            actor?.layoutParams = FrameLayout.LayoutParams(g.a, g.a, Gravity.BOTTOM)
            update(root, p)
        }
        bubbleLp?.let { p ->
            p.width = g.bubbleW; p.x = g.bubbleX; p.y = g.bubbleBottom
            update(bubbleView, p)
        }
        bubble.rightSide = g.right; bubble.tailFromStart = g.tailDp.dp; bubble.maxBox = g.maxBoxDp.dp
        if (s.character != character) {
            character = s.character
            actor?.let { it.play(character, it.currentPose, it.mirrored) }
            bubble.peekBack = cache.sheet(character, "peek-back")?.asImageBitmap()
            bubble.peekFront = cache.sheet(character, "peek-front")?.asImageBitmap()
        }
    }

    // ---------------- 平時：側邊探頭 ----------------

    private fun edgeKeyOf(): String = Store.current.settings.let { "${it.side}/${it.peekPos}/${it.character}/${it.actorSize}" }

    fun showEdge(animated: Boolean = true) {
        if (edge != null || busy || !Settings.canDrawOverlays(svc)) return
        val s = Store.current.settings
        val m = wm.currentWindowMetrics
        val b = m.bounds
        val ins = m.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
        val fig = 1.34f * px(s.actorSize.dp.toFloat())
        val w = max(px(52f), (0.33f * fig).roundToInt())
        val h = max(px(64f), (0.45f * fig).roundToInt())
        val cy = b.height() * when (s.peekPos) { PeekPos.TOP -> 0.2f; PeekPos.MIDDLE -> 0.47f; PeekPos.BOTTOM -> 0.74f }
        val right = s.side == Side.RIGHT
        val v = EdgePetView(ctx, cache, onTap = { svc.onEdgeTap() }, onDrag = ::onEdgeDrag).apply {
            character = s.character; leftSide = !right; figurePx = fig
        }
        val p = lp(w, h).apply {
            x = if (right) b.width() - ins.right - w else ins.left
            y = (cy - h / 2f).roundToInt()
        }
        if (!add(v, p)) return
        edge = v; edgeLp = p; edgeKey = edgeKeyOf()
        if (animated && !reduce) {
            v.translationX = if (right) w.toFloat() else -w.toFloat()
            v.animate().translationX(0f).setDuration(280).start()
        }
    }

    fun removeEdge() {
        remove(edge)
        edge = null; edgeLp = null
    }

    private suspend fun hideEdgeAnimated() {
        val v = edge ?: return
        if (!reduce) {
            val to = if (v.leftSide) -v.width.toFloat() else v.width.toFloat()
            animate(260) { v.translationX = to * it }
        }
        removeEdge()
    }

    private fun onEdgeDrag(phase: Int, x: Float, y: Float) {
        val s = Store.current.settings
        val a = px(s.actorSize.dp.toFloat())
        when (phase) {
            EdgePetView.DRAG_START -> {
                edge?.alpha = 0f // 不能設成看不見：視窗看不見就收不到這次拖曳的後續觸控
                val sv = SpriteView(ctx, cache).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                val p = lp(a, a, touchable = false)
                if (add(sv, p)) { dragView = sv; dragLp = p; sv.play(s.character, "hang") }
                moveDrag(x, y, a)
            }
            EdgePetView.DRAG_MOVE -> moveDrag(x, y, a)
            EdgePetView.DRAG_CANCEL -> { removeDrag(); edge?.alpha = 1f }
            EdgePetView.DRAG_END -> {
                removeDrag()
                val b = wm.currentWindowMetrics.bounds
                val side = if (x < b.width() / 2f) Side.LEFT else Side.RIGHT
                val pos = when {
                    y < b.height() * 0.342f -> PeekPos.TOP
                    y < b.height() * 0.618f -> PeekPos.MIDDLE
                    else -> PeekPos.BOTTOM
                }
                removeEdge()
                Store.updateSettings { it.copy(side = side, peekPos = pos) }
                showEdge()
                val where = when (pos) { PeekPos.TOP -> "上方"; PeekPos.MIDDLE -> "中間"; PeekPos.BOTTOM -> "下方" }
                Toast.makeText(svc, "好，改在${if (side == Side.RIGHT) "右" else "左"}邊${where}探頭", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 手指抓著麵條的手（圖的 10% 高度）。 */
    private fun moveDrag(x: Float, y: Float, a: Int) {
        val p = dragLp ?: return
        p.x = (x - a / 2f).roundToInt()
        p.y = (y - a * 0.1f).roundToInt()
        update(dragView, p)
    }

    private fun removeDrag() {
        remove(dragView)
        dragView = null; dragLp = null
    }

    // ---------------- 小工具 ----------------

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).roundToInt()

    /** ValueAnimator 包成可取消的 suspend；系統關掉動畫時會立刻跑完。 */
    private suspend fun animate(ms: Long, interp: TimeInterpolator = LinearInterpolator(), onFrame: (Float) -> Unit) =
        suspendCancellableCoroutine { c ->
            val va = ValueAnimator.ofFloat(0f, 1f).setDuration(ms)
            va.interpolator = interp
            va.addUpdateListener { onFrame(it.animatedValue as Float) }
            va.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (c.isActive) c.resume(Unit)
                }
            })
            // 取消一律發生在主執行緒（MainScope）
            c.invokeOnCancellation { va.removeAllUpdateListeners(); va.removeAllListeners(); va.cancel() }
            va.start()
        }
}
