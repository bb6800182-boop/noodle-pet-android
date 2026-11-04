package com.c520.lamian.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import org.json.JSONObject
import kotlin.math.abs

/**
 * 拉麵已經畫好的精靈圖：assets/sprites/<角色>/<動作>.png（每格 240px，一列 8 格）＋ sprites.json（播放順序與每格毫秒）。
 * 只在麵條出來時解碼，收起來後 trim() 釋放。
 */
class SpriteCache(private val context: Context) {
    class Pose(val index: IntArray, val ms: IntArray)
    class Meta(val cell: Int, val cols: Int, val poses: Map<String, Pose>)

    private val metas = HashMap<String, Meta>()
    private val thumbs = HashMap<String, Bitmap>()
    private val sheets = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 4
    }

    @Synchronized
    fun meta(character: String): Meta = metas.getOrPut(character) {
        val text = context.assets.open("sprites/$character/sprites.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val o = JSONObject(text.removePrefix("\uFEFF"))
        val poses = HashMap<String, Pose>()
        val p = o.getJSONObject("poses")
        for (name in p.keys()) {
            val seq = p.getJSONObject(name).getJSONArray("seq")
            val idx = IntArray(seq.length()) { seq.getJSONArray(it).getInt(0) }
            val ms = IntArray(seq.length()) { maxOf(16, seq.getJSONArray(it).getInt(1)) }
            poses[name] = Pose(idx, ms)
        }
        Meta(o.getInt("cell") * o.optInt("scale", 1), o.getInt("cols"), poses)
    }

    fun has(character: String, pose: String): Boolean = runCatching { meta(character).poses.containsKey(pose) }.getOrDefault(false)

    fun sheet(character: String, pose: String): Bitmap? {
        val key = "$character/$pose"
        sheets[key]?.let { return it }
        val b = runCatching { context.assets.open("sprites/$key.png").use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return null
        sheets[key] = b
        return b
    }

    fun frameRect(meta: Meta, index: Int, out: Rect) {
        val col = index % meta.cols
        val row = index / meta.cols
        out.set(col * meta.cell, row * meta.cell, (col + 1) * meta.cell, (row + 1) * meta.cell)
    }

    /** 動作清單、角色選擇用的縮圖：只解碼第一格（不必把整張圖讀進來）。可在背景執行緒呼叫。 */
    @Synchronized
    fun firstFrame(character: String, pose: String): Bitmap? = thumbs["$character/$pose"] ?: runCatching {
        val m = meta(character)
        val r = Rect().also { frameRect(m, m.poses[pose]?.index?.firstOrNull() ?: 0, it) }
        context.assets.open("sprites/$character/$pose.png").use { input ->
            val d = BitmapRegionDecoder.newInstance(input) ?: return@use null
            try {
                if (r.intersect(0, 0, d.width, d.height)) d.decodeRegion(r, null) else null
            } finally {
                d.recycle()
            }
        }
    }.getOrNull()?.also { thumbs["$character/$pose"] = it }

    fun trim() = sheets.clear()
}

/** 播放一個動作的 View：每格停留時間照 sprites.json，靜止的動作幾乎不醒來。只用程式建立，不從 XML 載入。 */
@SuppressLint("ViewConstructor")
class SpriteView(context: Context, private val cache: SpriteCache) : View(context) {
    private var character = ""
    private var pose = ""
    private var sheet: Bitmap? = null
    private var data: SpriteCache.Pose? = null
    private var step = 0
    private val src = Rect()
    private val dst = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    var mirrored = false
        set(v) { field = v; invalidate() }

    private val tick = object : Runnable {
        override fun run() {
            val d = data ?: return
            step = (step + 1) % d.index.size
            invalidate()
            schedule()
        }
    }

    val currentPose: String get() = pose

    fun play(character: String, pose: String, mirror: Boolean = false) {
        mirrored = mirror
        val p = if (cache.has(character, pose)) pose else "idle"
        if (character == this.character && p == this.pose && sheet != null) return
        this.character = character
        this.pose = p
        data = cache.meta(character).poses[p]
        sheet = cache.sheet(character, p)
        step = 0
        removeCallbacks(tick)
        invalidate()
        schedule()
    }

    private fun schedule() {
        val d = data ?: return
        if (d.index.size > 1 && isAttachedToWindow) postDelayed(tick, d.ms[step].toLong())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(tick)
        schedule()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val b = sheet ?: return
        val d = data ?: return
        cache.frameRect(cache.meta(character), d.index[step], src)
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        if (mirrored) {
            canvas.save()
            canvas.scale(-1f, 1f, width / 2f, height / 2f)
        }
        canvas.drawBitmap(b, src, dst, paint)
        if (mirrored) canvas.restore()
    }
}

/**
 * 平時「側邊探頭」：探頭的麵條轉 90 度貼在螢幕邊，手剛好抓著螢幕邊緣，只露出頭。
 * 點一下叫牠出來；拖曳交給 onDrag（START/MOVE/END，座標是螢幕座標）。
 */
@SuppressLint("ViewConstructor")
class EdgePetView(
    context: Context,
    private val cache: SpriteCache,
    private val onTap: () -> Unit,
    private val onDrag: (phase: Int, rawX: Float, rawY: Float) -> Unit,
) : View(context) {
    var character = "rigatoni"
        set(v) { field = v; invalidate() }
    var leftSide = false
        set(v) { field = v; invalidate() }
    var figurePx = 0f

    private val src = Rect()
    private val dst = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    init {
        contentDescription = "拉麵在旁邊探頭，點一下叫牠出來，拖曳可以換位置"
        isClickable = true
    }

    /** 貼在螢幕邊：別讓系統的「邊緣滑動返回」搶走拖曳（只在大小改變時重設） */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
    }

    override fun onDraw(canvas: Canvas) {
        val m = runCatching { cache.meta(character) }.getOrNull() ?: return
        val e = figurePx
        // 手在圖的 43.5% 高度（實測精靈圖），圖心往螢幕外推 6.5%，手剛好落在螢幕邊
        val cx = if (leftSide) -0.065f * e else width + 0.065f * e
        val cy = height / 2f
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(if (leftSide) 90f else -90f)
        dst.set(-e / 2, -e / 2, e / 2, e / 2)
        src.set(0, 0, m.cell, m.cell)
        cache.sheet(character, "peek-back")?.let { canvas.drawBitmap(it, src, dst, paint) }
        cache.sheet(character, "peek-front")?.let { canvas.drawBitmap(it, src, dst, paint) }
        canvas.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop)) {
                    dragging = true
                    onDrag(DRAG_START, event.rawX, event.rawY)
                }
                if (dragging) onDrag(DRAG_MOVE, event.rawX, event.rawY)
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) onDrag(DRAG_END, event.rawX, event.rawY) else performClick()
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDrag(DRAG_CANCEL, event.rawX, event.rawY)
                dragging = false
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap()
        return true
    }

    companion object {
        const val DRAG_START = 0
        const val DRAG_MOVE = 1
        const val DRAG_END = 2
        const val DRAG_CANCEL = 3
    }
}
