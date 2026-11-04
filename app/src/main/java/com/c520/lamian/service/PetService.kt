package com.c520.lamian.service

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings as SysSettings
import android.widget.Toast
import com.c520.lamian.data.IdleMode
import com.c520.lamian.data.Live
import com.c520.lamian.data.Missed
import com.c520.lamian.data.RemAction
import com.c520.lamian.data.Reminder
import com.c520.lamian.data.Repeat
import com.c520.lamian.data.ScreenOff
import com.c520.lamian.data.Settings
import com.c520.lamian.data.Store
import com.c520.lamian.data.blockedUntil
import com.c520.lamian.data.hhmm
import com.c520.lamian.data.statusLine
import com.c520.lamian.data.validMissed
import com.c520.lamian.engine.Alarms
import com.c520.lamian.engine.Schedule
import com.c520.lamian.engine.UsageClock
import com.c520.lamian.overlay.PetOverlay
import com.c520.lamian.overlay.PetOverlay.Result
import com.c520.lamian.overlay.ReminderSpec
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * 常駐服務：平常什麼都不做，只在螢幕開／關／解鎖、鬧鐘響、通話狀態改變時醒來。
 * 螢幕亮著且解鎖才計算連續使用時間；時間到、喝水時間到、自訂提醒到了，就叫麵條出來（或發通知）。
 */
class PetService : Service() {

    companion object {
        @Volatile
        var instance: PetService? = null
            private set

        /** 總開關關著就不啟動。 */
        fun start(context: Context, from: Intent? = null) {
            if (!Store.current.settings.enabled) return
            val i = Intent(context, PetService::class.java)
            if (from != null) {
                i.action = from.action
                from.extras?.let { i.putExtras(it) }
            }
            runCatching { context.startForegroundService(i) }
        }

        /** 鬧鐘與通知按鈕：服務在跑就直接處理，不然先叫起服務再處理。 */
        fun dispatch(context: Context, intent: Intent) {
            if (!Store.current.settings.enabled) return
            instance?.handle(intent) ?: start(context, intent)
        }

        /** 總開關：關掉就停掉服務、取消所有鬧鐘與通知；打開就重新啟動。今天的紀錄都保留。 */
        fun setEnabled(context: Context, on: Boolean) {
            Store.updateSettings { it.copy(enabled = on) }
            if (on) {
                start(context)
                return
            }
            context.stopService(Intent(context, PetService::class.java))
            Alarms.cancelAll(context)
            context.getSystemService(NotificationManager::class.java)?.cancelAll()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private lateinit var overlay: PetOverlay
    private lateinit var clock: UsageClock
    private lateinit var audio: AudioManager

    /** 等麵條有空（或解鎖後）才出來的提醒 */
    private val queue = ArrayDeque<ReminderSpec>()
    /** 通話中先扣著的提醒 */
    private val held = mutableListOf<ReminderSpec>()
    private var active = false
    private var inCall = false
    private var lastSettings: Settings? = null

    private val stretchCheck = Runnable { checkStretch() }
    private val drainNow = Runnable { drain() }
    private val expire = Runnable { evaluate() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onInactive()
                Intent.ACTION_SCREEN_ON -> if (!keyguardLocked()) onActive() else evaluate()
                Intent.ACTION_USER_PRESENT -> onActive()
            }
        }
    }
    private val modeListener = AudioManager.OnModeChangedListener { onCallMode(it) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.ensureChannels(this)
        val started = runCatching {
            val n = Notifications.status(this)
            if (Build.VERSION.SDK_INT >= 34) startForeground(Notifications.ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(Notifications.ID_STATUS, n)
        }.isSuccess
        val s = Store.current.settings
        lastSettings = s
        clock = UsageClock(s.breakResetMin * 60_000L)
        overlay = PetOverlay(this)
        audio = getSystemService(AudioManager::class.java)
        if (!started || !s.enabled) { stopSelf(); return }

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            },
            RECEIVER_NOT_EXPORTED,
        )
        audio.addOnModeChangedListener(mainExecutor, modeListener)
        inCall = isCallMode(audio.mode)

        val now = System.currentTimeMillis()
        if (getSystemService(PowerManager::class.java).isInteractive && !keyguardLocked()) {
            active = true
            clock.onActive(now)
        } else clock.onInactive(now)

        ensureWater(now)
        Alarms.scheduleReminders(this)
        scope.launch { Store.state.map { it.settings }.distinctUntilChanged().collect { onSettings(it) } }
        scope.launch { Store.state.map { it.reminders }.distinctUntilChanged().drop(1).collect { Alarms.scheduleReminders(this@PetService) } }
        scope.launch {
            Store.state.map { statusSig() }.distinctUntilChanged().collect { if (Store.current.settings.enabled) Notifications.postStatus(this@PetService) }
        }
        evaluate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null) handle(intent)
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        // 先停掉會重發通知的流程，再把常駐通知收掉
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { audio.removeOnModeChangedListener(modeListener) }
        overlay.destroy()
        Store.setLive(Live(serviceRunning = false))
        super.onDestroy()
    }

    // ---------------- 外部事件 ----------------

    fun handle(intent: Intent) {
        val key = intent.getStringExtra(Notifications.EXTRA_KEY).orEmpty()
        val ex = intent.getStringExtra(Notifications.EXTRA_EXERCISE)
        when (intent.action) {
            Alarms.ACTION_WATER -> onWaterAlarm()
            Alarms.ACTION_REMINDER -> onReminderAlarm(intent.getIntArrayExtra(Alarms.EXTRA_IDS) ?: IntArray(0))
            // 「晚點」是使用者自己按的：單次提醒響過後雖然關掉了，晚點照樣要再提醒（在 App 裡關掉或刪除時會取消）
            Alarms.ACTION_SNOOZE -> Store.reminder(intent.getIntExtra(Alarms.EXTRA_ID, 0))?.let { fire(specFor(it)) }
            Notifications.ACTION_WATER_NOW -> { recordWaterNow("補記一杯"); toast("記上了，今天第 ${Store.current.today.water} 杯") }
            Notifications.ACTION_STRETCH_NOW -> manualStretch()
            Notifications.ACTION_PAUSE -> if (!paused()) togglePause(quiet = false)
            Notifications.ACTION_RESUME -> if (paused()) togglePause(quiet = false)
            Notifications.ACTION_RECALL -> recall()
            Notifications.ACTION_DRANK -> onNoteAction(key, ex, Result.DRANK)
            Notifications.ACTION_ACK -> onNoteAction(key, ex, Result.ACK)
            Notifications.ACTION_DONE -> onNoteAction(key, ex, Result.DONE)
            Notifications.ACTION_SNOOZE -> onNoteAction(key, ex, Result.SNOOZED)
        }
    }

    private fun keyguardLocked() = getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun onActive() {
        if (active) return
        active = true
        val now = System.currentTimeMillis()
        // 關螢幕夠久算休息過：重新計時，排著的伸展也不用了
        if (clock.onActive(now)) queue.removeAll { it is ReminderSpec.Stretch && it.reminder == null && !it.manual }
        evaluate()
        handler.postDelayed(drainNow, 700)
    }

    private fun onInactive() {
        clock.onInactive(System.currentTimeMillis())
        if (!active) return
        active = false
        handler.removeCallbacks(stretchCheck)
        handler.removeCallbacks(drainNow)
        overlay.hideNow()?.let { sp ->
            queue.removeAll { it.key == sp.key }
            queue.addFirst(sp)
            if (sp is ReminderSpec.Water || sp.reminder != null) Notifications.postRemind(this, sp, alert = false)
        }
        updateLive()
    }

    private fun isCallMode(m: Int) = m == AudioManager.MODE_RINGTONE || m == AudioManager.MODE_IN_CALL ||
        m == AudioManager.MODE_IN_COMMUNICATION || m == AudioManager.MODE_CALL_SCREENING ||
        m == AudioManager.MODE_CALL_REDIRECT || m == AudioManager.MODE_COMMUNICATION_REDIRECT

    private fun onCallMode(mode: Int) {
        val call = isCallMode(mode)
        if (call == inCall) return
        inCall = call
        if (!call) {
            val h = held.toList()
            held.clear()
            h.forEach(::fire)
        }
    }

    private fun onSettings(s: Settings) {
        if (!s.enabled) { stopSelf(); return }
        val prev = lastSettings
        lastSettings = s
        clock.setBreakReset(s.breakResetMin * 60_000L)
        if (prev != null && (prev.waterOn != s.waterOn || prev.waterEveryMin != s.waterEveryMin || prev.waterFrom != s.waterFrom || prev.waterTo != s.waterTo)) {
            setNextWater(nextWaterFrom(System.currentTimeMillis()))
        }
        overlay.refresh()
        evaluate()
    }

    /** 重新檢查：暫停或補做過期了沒、什麼時候該提醒動一動、側邊探頭、首頁與通知的狀態。 */
    fun evaluate() {
        if (!Store.current.settings.enabled) return
        val now = System.currentTimeMillis()
        val st = Store.current
        if (st.pausedUntil != null && st.pausedUntil <= now) {
            Store.setPaused(null)
            Store.addLog("暫停結束", "照常提醒")
        }
        if (st.missed != null && validMissed(st, now) == null) {
            Store.setMissed(null)
            Notifications.cancelMissed(this)
        }
        handler.removeCallbacks(expire)
        val cur = Store.current
        val next = listOfNotNull(
            cur.pausedUntil,
            cur.missed?.let { it.at + cur.settings.recallWithinMin * 60_000L },
        ).filter { it > now }.minOrNull()
        if (active && next != null) handler.postDelayed(expire, next - now + 500)
        scheduleStretch()
        syncEdge()
        updateLive()
        Notifications.postStatus(this)
    }

    private fun statusSig(): String {
        val st = Store.current
        val now = System.currentTimeMillis()
        return "${st.today.water}/${st.today.stretch + st.today.walk}/${paused()}/${statusLine(st, now, forNotification = true)}"
    }

    private fun updateLive() {
        val now = System.currentTimeMillis()
        Store.setLive(Live(serviceRunning = true, screenActive = active, usedSince = if (active) now - clock.usedMs(now) else null))
    }

    fun syncEdge() {
        if (overlay.busy) return
        if (active && Store.current.settings.idleMode == IdleMode.PEEK) overlay.showEdge() else overlay.removeEdge()
    }

    // ---------------- 伸展 ----------------

    private fun scheduleStretch() {
        handler.removeCallbacks(stretchCheck)
        val st = Store.current
        val s = st.settings
        if (!active || !s.stretchOn) return
        val now = System.currentTimeMillis()
        val block = blockedUntil(st, now)
        val wait = if (block != null) block - now + 1000 else s.intervalMin * 60_000L - clock.usedMs(now)
        handler.postDelayed(stretchCheck, wait.coerceAtLeast(0))
    }

    private fun checkStretch() {
        val st = Store.current
        val s = st.settings
        if (!active || !s.stretchOn) return
        val now = System.currentTimeMillis()
        if (blockedUntil(st, now) != null || clock.usedMs(now) < s.intervalMin * 60_000L) { evaluate(); return }
        val pending = (queue + held + listOfNotNull(overlay.current)).any { it is ReminderSpec.Stretch && it.reminder == null }
        if (!pending) fire(ReminderSpec.Stretch(Store.nextExercise()))
    }

    fun manualStretch(): Boolean {
        if (overlay.busy) { toast("麵條正在忙，先處理目前這張"); return false }
        fire(ReminderSpec.Stretch(Store.nextExercise(), manual = true))
        return true
    }

    fun recall() {
        val m = validMissed(Store.current, System.currentTimeMillis()) ?: return
        val e = Store.exercise(m.exerciseId) ?: return
        if (overlay.busy) { toast("麵條正在忙，先處理目前這張"); return }
        Store.setMissed(null)
        Notifications.cancelMissed(this)
        fire(ReminderSpec.Stretch(e, manual = true))
    }

    // ---------------- 喝水 ----------------

    private fun nextWaterFrom(now: Long): Long? {
        val s = Store.current.settings
        return if (s.waterOn) Schedule.nextWater(now, s.waterEveryMin, s.waterFrom, s.waterTo) else null
    }

    private fun setNextWater(at: Long?) {
        Store.setNextWater(at)
        Alarms.setWater(this, at)
    }

    private fun ensureWater(now: Long) {
        val st = Store.current
        setNextWater(if (st.settings.waterOn) st.nextWaterAt?.takeIf { it > now } ?: nextWaterFrom(now) else null)
    }

    private fun onWaterAlarm() {
        val st = Store.current
        val s = st.settings
        val now = System.currentTimeMillis()
        if (!s.waterOn) { setNextWater(null); return }
        val block = blockedUntil(st, now)
        val zone = ZoneId.systemDefault()
        if (block != null) {
            setNextWater(Schedule.clampToWindow(Instant.ofEpochMilli(block).atZone(zone), s.waterFrom, s.waterTo).toInstant().toEpochMilli())
            return
        }
        if (s.waterFrom != s.waterTo && !Schedule.inWindow(Schedule.minuteOfDay(now), s.waterFrom, s.waterTo)) {
            setNextWater(Schedule.clampToWindow(Instant.ofEpochMilli(now).atZone(zone), s.waterFrom, s.waterTo).toInstant().toEpochMilli())
            return
        }
        setNextWater(nextWaterFrom(now))
        fire(ReminderSpec.Water())
    }

    /** 首頁、通知、打招呼泡泡的「喝了一杯」。 */
    fun recordWaterNow(result: String) {
        Store.recordWater(result)
        setNextWater(nextWaterFrom(System.currentTimeMillis()))
    }

    // ---------------- 自訂提醒 ----------------

    fun specFor(r: Reminder): ReminderSpec = when (r.action) {
        RemAction.SAY -> ReminderSpec.Say(r)
        RemAction.WATER -> ReminderSpec.Water(r)
        RemAction.EXERCISE -> ReminderSpec.Stretch(
            Store.exercise(r.exerciseId) ?: Store.enabledExercises().firstOrNull() ?: Store.current.exercises.first(),
            reminder = r,
        )
    }

    private fun onReminderAlarm(ids: IntArray) {
        for (id in ids.distinct()) {
            val r = Store.reminder(id) ?: continue
            if (!r.enabled) continue
            if (r.repeat == Repeat.ONCE) Store.setReminderEnabled(id, false)
            fire(specFor(r))
        }
        Alarms.scheduleReminders(this)
    }

    // ---------------- 暫停 ----------------

    private fun paused() = (Store.current.pausedUntil ?: 0L) > System.currentTimeMillis()

    /** 切換暫停 1 小時；回傳暫停到什麼時候（恢復時是 null）。quiet：泡泡自己會說，不另外跳提示。 */
    fun togglePause(quiet: Boolean): Long? {
        val until = if (paused()) null else System.currentTimeMillis() + 60 * 60_000L
        Store.setPaused(until)
        if (until != null) {
            Store.addLog("暫停 1 小時")
            if (!quiet) toast("暫停到 ${hhmm(until)}，自訂提醒照常")
        } else {
            Store.addLog("恢復提醒")
            if (!quiet) toast("恢復提醒")
        }
        evaluate()
        return until
    }

    // ---------------- 叫麵條出來 ----------------

    /** 規則照示意圖：通話中先扣著；螢幕關著發通知並排隊；麵條在忙就排隊。 */
    fun fire(sp: ReminderSpec) {
        val s = Store.current.settings
        if (inCall && s.holdInCall) {
            if (held.none { it.key == sp.key }) held += sp
            if (sp.reminder != null) Notifications.postRemind(this, sp, alert = true, inCall = true)
            return
        }
        if (!active) {
            if (sp is ReminderSpec.Water || sp.reminder?.screenOff == ScreenOff.NOTIFY) Notifications.postRemind(this, sp, alert = true)
            // 螢幕關著本來就是在休息，滑手機提醒不用補
            if (!(sp is ReminderSpec.Stretch && sp.reminder == null && !sp.manual)) enqueue(sp)
            return
        }
        if (overlay.busy) { enqueue(sp); return }
        show(sp)
    }

    private fun enqueue(sp: ReminderSpec) {
        if (queue.none { it.key == sp.key }) queue.addLast(sp)
    }

    private fun show(sp: ReminderSpec) {
        if (!SysSettings.canDrawOverlays(this)) { fallback(sp); return }
        if (overlay.show(sp)) Notifications.cancelRemind(this, sp) else enqueue(sp)
    }

    /** 沒有懸浮權限：改用通知。滑手機提醒發一次就重新計時，免得一直響。 */
    private fun fallback(sp: ReminderSpec) {
        if (sp == ReminderSpec.Visit) return
        Notifications.postRemind(this, sp, alert = true, fallback = true)
        if (sp is ReminderSpec.Stretch && sp.reminder == null) {
            clock.reset(System.currentTimeMillis())
            scheduleStretch()
        }
    }

    private fun drain() {
        while (active && !overlay.busy) {
            val sp = queue.removeFirstOrNull() ?: return
            show(sp)
        }
    }

    fun onEdgeTap() {
        if (active && !overlay.busy) show(ReminderSpec.Visit)
    }

    fun onOverlayClosed() {
        syncEdge()
        handler.removeCallbacks(drainNow)
        handler.postDelayed(drainNow, 350)
    }

    fun onOverlayFailed(sp: ReminderSpec) {
        fallback(sp)
        syncEdge()
    }

    // ---------------- 結果 ----------------

    private fun labelOf(sp: ReminderSpec): String = when (sp) {
        is ReminderSpec.Water -> "喝水"
        is ReminderSpec.Say -> "提醒：${sp.reminder.text}"
        is ReminderSpec.Stretch -> sp.exercise.name
        ReminderSpec.Visit -> ""
    }

    private fun onNoteAction(key: String, ex: String?, r: Result) {
        val sp = queue.firstOrNull { it.key == key } ?: held.firstOrNull { it.key == key } ?: specFromKey(key, ex) ?: return
        queue.removeAll { it.key == key }
        held.removeAll { it.key == key }
        Notifications.cancelRemind(this, sp)
        onResult(sp, r, viaNotification = true)
    }

    private fun specFromKey(key: String, ex: String?): ReminderSpec? = when {
        key == "water" -> ReminderSpec.Water()
        key == "stretch" -> ex?.let { Store.exercise(it) }?.let { ReminderSpec.Stretch(it) }
        key.startsWith("rem:") -> key.removePrefix("rem:").toIntOrNull()?.let { Store.reminder(it) }?.let(::specFor)
        else -> null
    }

    /** 麵條泡泡或通知按鈕的結果：記次數、重新計時、排「晚點」。 */
    fun onResult(sp: ReminderSpec, r: Result, viaNotification: Boolean = false) {
        val now = System.currentTimeMillis()
        val s = Store.current.settings
        val label = labelOf(sp)
        when (r) {
            Result.DONE -> {
                (sp as? ReminderSpec.Stretch)?.let {
                    Store.recordDone(it.exercise)
                    if (Store.current.missed == null) Notifications.cancelMissed(this)
                }
                clock.reset(now)
            }
            Result.DRANK -> recordWaterNow(if (viaNotification) "喝了（通知）" else "喝了")
            Result.SNOOZED -> {
                val ms = s.snoozeMin * 60_000L
                val rem = sp.reminder
                when {
                    rem != null -> Alarms.snooze(this, rem.id, now + ms)
                    sp is ReminderSpec.Stretch -> clock.setUsed(now, ((s.intervalMin - s.snoozeMin).coerceAtLeast(0)) * 60_000L)
                    sp is ReminderSpec.Water -> setNextWater(now + ms)
                }
                Store.addLog(label, "晚點")
                toast("好，${s.snoozeMin} 分鐘後再提醒")
            }
            Result.SKIPPED -> {
                clock.reset(now)
                Store.addLog(label, "跳過")
            }
            Result.IGNORED -> if (sp != ReminderSpec.Visit) {
                if (sp is ReminderSpec.Stretch) {
                    clock.reset(now)
                    if (s.recallWithinMin > 0) {
                        Store.setMissed(Missed(sp.exercise.id, now))
                        Notifications.postMissed(this, sp.exercise, now, s.recallWithinMin)
                        toast("先收起來了，${s.recallWithinMin} 分鐘內可以從通知列補做")
                    }
                }
                Store.addLog(label, "沒理")
            }
            Result.ACK -> Store.addLog(label, "知道了", ok = true)
        }
        evaluate()
    }

    /** 給 App「現在試一次」：直接叫麵條出來跑一次這個提醒（不影響排程）。 */
    fun preview(r: Reminder): Boolean {
        if (overlay.busy) { toast("麵條正在忙，先處理目前這張"); return false }
        show(specFor(r))
        return true
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

}
