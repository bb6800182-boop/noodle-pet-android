package com.c520.lamian.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.random.Random

/**
 * App 的全部狀態：設定、動作、自訂提醒、今天的次數與紀錄。
 * 存在 SharedPreferences（JSON 字串），讀檔時一律校正到合法範圍，壞掉的資料退回預設值。
 */
object Store {
    private lateinit var prefs: SharedPreferences
    private lateinit var catalog: List<Exercise>
    private lateinit var flow: MutableStateFlow<AppState>

    val state: StateFlow<AppState> get() = flow
    val current: AppState get() = flow.value.rolled()

    fun init(context: Context) {
        if (::flow.isInitialized) return
        prefs = context.getSharedPreferences("lamian", Context.MODE_PRIVATE)
        catalog = ExerciseCatalog.load(context)
        flow = MutableStateFlow(load())
    }

    private fun todayKey(): String = LocalDate.now().toString()

    /** 跨日時把今天的次數歸零（紀錄保留，畫面只顯示今天的）。 */
    private fun AppState.rolled(): AppState =
        if (today.day == todayKey()) this else copy(today = Today(todayKey()))

    @Synchronized
    private fun update(f: (AppState) -> AppState) {
        val next = f(flow.value.rolled())
        flow.value = next
        persist(next)
    }

    /** 服務的即時狀態，不存檔。 */
    @Synchronized
    fun setLive(live: Live) {
        flow.value = flow.value.rolled().copy(live = live)
    }

    // ---------- 設定 ----------
    fun updateSettings(f: (Settings) -> Settings) = update { it.copy(settings = f(it.settings).sanitized()) }

    // ---------- 動作 ----------
    fun exercise(id: String): Exercise? = current.exercises.firstOrNull { it.id == id }

    /** 關掉最後一個啟用中的動作會被拒絕（回傳 false）。 */
    fun setExerciseEnabled(id: String, on: Boolean): Boolean {
        val list = current.exercises
        if (!on && list.count { it.enabled } <= 1 && list.firstOrNull { it.id == id }?.enabled == true) return false
        update { s -> s.copy(exercises = s.exercises.map { if (it.id == id) it.copy(enabled = on) else it }) }
        return true
    }

    fun setExerciseSeconds(id: String, seconds: Int) = update { s ->
        s.copy(exercises = s.exercises.map { if (it.id == id) it.copy(seconds = seconds.coerceIn(Limits.exerciseSeconds)) else it })
    }

    fun setExerciseSpec(id: String, spec: String) {
        val v = spec.trim().take(Limits.SPEC_TEXT_MAX)
        if (v.isEmpty()) return
        update { s -> s.copy(exercises = s.exercises.map { if (it.id == id) it.copy(spec = v) else it }) }
    }

    /** 在同一組（現有動作／提案）裡往前或往後移一格；已經在邊上就回傳 false。 */
    fun moveExercise(id: String, delta: Int): Boolean {
        val list = current.exercises.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return false
        val group = list.withIndex().filter { it.value.proposed == list[i].proposed }.map { it.index }
        val pos = group.indexOf(i) + delta
        if (pos !in group.indices) return false
        val j = group[pos]
        list[i] = list[j].also { list[j] = list[i] }
        update { it.copy(exercises = list) }
        return true
    }

    fun enabledExercises(state: AppState = current): List<Exercise> = state.exercises.filter { it.enabled }

    /** 輪到的下一個動作，並前進輪替位置。 */
    fun nextExercise(): Exercise {
        val s = current
        val on = enabledExercises(s).ifEmpty { s.exercises }
        if (s.settings.rotation == Rotation.RANDOM) return on[Random.nextInt(on.size)]
        val e = on[s.rotationIndex.mod(on.size)]
        update { it.copy(rotationIndex = (it.rotationIndex + 1).mod(10_000)) }
        return e
    }

    fun peekNextExercise(state: AppState = current): Exercise? {
        val on = enabledExercises(state)
        if (on.isEmpty() || state.settings.rotation == Rotation.RANDOM) return null
        return on[state.rotationIndex.mod(on.size)]
    }

    // ---------- 自訂提醒 ----------
    fun reminder(id: Int): Reminder? = current.reminders.firstOrNull { it.id == id }

    /** 新增（id = 0）或更新，回傳實際的 id。 */
    fun saveReminder(r: Reminder): Int {
        var id = r.id
        update { s ->
            if (id == 0) id = (s.reminders.maxOfOrNull { it.id } ?: 0) + 1
            val clean = r.copy(id = id, text = r.text.trim().take(Limits.REMINDER_TEXT_MAX), minuteOfDay = r.minuteOfDay.coerceIn(0, 1439))
            val others = s.reminders.filter { it.id != id }
            s.copy(reminders = (others + clean).sortedBy { it.minuteOfDay })
        }
        return id
    }

    fun deleteReminder(id: Int) = update { s -> s.copy(reminders = s.reminders.filter { it.id != id }) }

    fun setReminderEnabled(id: Int, on: Boolean) = update { s ->
        s.copy(reminders = s.reminders.map { if (it.id == id) it.copy(enabled = on) else it })
    }

    // ---------- 今天的次數與紀錄 ----------
    fun addLog(label: String, result: String = "", ok: Boolean = false) = update { s ->
        s.copy(log = (listOf(LogEntry(System.currentTimeMillis(), label, result, ok)) + s.log).take(80))
    }

    fun recordDone(e: Exercise) = update { s ->
        val t = if (e.id == "walk") s.today.copy(walk = s.today.walk + 1) else s.today.copy(stretch = s.today.stretch + 1)
        s.copy(today = t, log = (listOf(LogEntry(System.currentTimeMillis(), e.name, "完成", true)) + s.log).take(80),
            missed = s.missed?.takeIf { it.exerciseId != e.id })
    }

    fun recordWater(result: String = "喝了") = update { s ->
        s.copy(today = s.today.copy(water = s.today.water + 1),
            log = (listOf(LogEntry(System.currentTimeMillis(), "喝水", result, true)) + s.log).take(80))
    }

    fun setPaused(until: Long?) = update { it.copy(pausedUntil = until) }
    fun setMissed(m: Missed?) = update { it.copy(missed = m) }
    fun setNextWater(at: Long?) = update { it.copy(nextWaterAt = at) }

    // ---------- 存讀檔 ----------
    private fun persist(s: AppState) {
        prefs.edit()
            .putString("settings", settingsJson(s.settings).toString())
            .putString("exercises", JSONArray(s.exercises.map {
                JSONObject().put("id", it.id).put("on", it.enabled).put("sec", it.seconds).put("spec", it.spec)
            }).toString())
            .putString("reminders", JSONArray(s.reminders.map(::reminderJson)).toString())
            .putString("today", JSONObject().put("day", s.today.day).put("water", s.today.water).put("stretch", s.today.stretch).put("walk", s.today.walk).toString())
            .putString("log", JSONArray(s.log.map { JSONObject().put("at", it.at).put("label", it.label).put("result", it.result).put("ok", it.ok) }).toString())
            .putLong("pausedUntil", s.pausedUntil ?: 0L)
            .putString("missed", s.missed?.let { JSONObject().put("id", it.exerciseId).put("at", it.at).toString() })
            .putLong("nextWaterAt", s.nextWaterAt ?: 0L)
            .putInt("rotation", s.rotationIndex)
            .apply()
    }

    private fun load(): AppState {
        val now = System.currentTimeMillis()
        val settings = prefs.getString("settings", null)?.let { runCatching { settingsFrom(JSONObject(it)) }.getOrNull() } ?: Settings()
        val reminders = prefs.getString("reminders", null)?.let { txt ->
            runCatching { JSONArray(txt).let { a -> (0 until a.length()).mapNotNull { reminderFrom(a.getJSONObject(it)) } } }.getOrNull()
        } ?: emptyList()
        val today = prefs.getString("today", null)?.let { txt ->
            runCatching { JSONObject(txt).let { Today(it.getString("day"), it.optInt("water"), it.optInt("stretch"), it.optInt("walk")) } }.getOrNull()
        }?.takeIf { it.day == todayKey() } ?: Today(todayKey())
        val log = prefs.getString("log", null)?.let { txt ->
            runCatching { JSONArray(txt).let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { LogEntry(it.getLong("at"), it.getString("label"), it.optString("result"), it.optBoolean("ok")) } } } }.getOrNull()
        } ?: emptyList()
        val missed = prefs.getString("missed", null)?.let { txt ->
            runCatching { JSONObject(txt).let { Missed(it.getString("id"), it.getLong("at")) } }.getOrNull()
        }
        return AppState(
            settings = settings,
            exercises = mergeExercises(prefs.getString("exercises", null)),
            reminders = reminders.sortedBy { it.minuteOfDay },
            today = today,
            log = log,
            pausedUntil = prefs.getLong("pausedUntil", 0L).takeIf { it > now },
            missed = missed,
            nextWaterAt = prefs.getLong("nextWaterAt", 0L).takeIf { it > 0L },
            rotationIndex = prefs.getInt("rotation", 0),
        )
    }

    private fun mergeExercises(json: String?): List<Exercise> {
        val base = catalog.associateBy { it.id }
        val out = mutableListOf<Exercise>()
        runCatching {
            if (json != null) {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val b = base[o.optString("id")] ?: continue
                    if (out.any { it.id == b.id }) continue
                    out += b.copy(
                        enabled = o.optBoolean("on", b.enabled),
                        seconds = o.optInt("sec", b.seconds).coerceIn(Limits.exerciseSeconds),
                        spec = o.optString("spec", b.spec).trim().ifEmpty { b.spec }.take(Limits.SPEC_TEXT_MAX),
                    )
                }
            }
        }
        catalog.filter { c -> out.none { it.id == c.id } }.forEach { out += it }
        if (out.none { it.enabled }) return out.map { if (!it.proposed) it.copy(enabled = true) else it }
        return out
    }

    private inline fun <reified T : Enum<T>> JSONObject.enumOr(key: String, def: T): T =
        optString(key).let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: def

    private fun settingsJson(s: Settings) = JSONObject()
        .put("enabled", s.enabled)
        .put("idleMode", s.idleMode.name).put("entrance", s.entrance.name).put("side", s.side.name)
        .put("actorSize", s.actorSize.name).put("peekPos", s.peekPos.name).put("character", s.character)
        .put("stretchOn", s.stretchOn).put("intervalMin", s.intervalMin).put("breakResetMin", s.breakResetMin)
        .put("waterOn", s.waterOn).put("waterEveryMin", s.waterEveryMin).put("waterFrom", s.waterFrom).put("waterTo", s.waterTo)
        .put("snoozeMin", s.snoozeMin).put("peekAfterSec", s.peekAfterSec).put("dismissAfterSec", s.dismissAfterSec)
        .put("recallWithinMin", s.recallWithinMin).put("expandSteps", s.expandSteps).put("holdInCall", s.holdInCall)
        .put("quietOn", s.quietOn).put("quietFrom", s.quietFrom).put("quietTo", s.quietTo)
        .put("autostart", s.autostart).put("rotation", s.rotation.name)

    private fun settingsFrom(o: JSONObject): Settings {
        val d = Settings()
        return Settings(
            enabled = o.optBoolean("enabled", d.enabled),
            idleMode = o.enumOr("idleMode", d.idleMode), entrance = o.enumOr("entrance", d.entrance), side = o.enumOr("side", d.side),
            actorSize = o.enumOr("actorSize", d.actorSize), peekPos = o.enumOr("peekPos", d.peekPos),
            character = o.optString("character", d.character),
            stretchOn = o.optBoolean("stretchOn", d.stretchOn), intervalMin = o.optInt("intervalMin", d.intervalMin),
            breakResetMin = o.optInt("breakResetMin", d.breakResetMin),
            waterOn = o.optBoolean("waterOn", d.waterOn), waterEveryMin = o.optInt("waterEveryMin", d.waterEveryMin),
            waterFrom = o.optInt("waterFrom", d.waterFrom), waterTo = o.optInt("waterTo", d.waterTo),
            snoozeMin = o.optInt("snoozeMin", d.snoozeMin), peekAfterSec = o.optInt("peekAfterSec", d.peekAfterSec),
            dismissAfterSec = o.optInt("dismissAfterSec", d.dismissAfterSec), recallWithinMin = o.optInt("recallWithinMin", d.recallWithinMin),
            expandSteps = o.optBoolean("expandSteps", d.expandSteps), holdInCall = o.optBoolean("holdInCall", d.holdInCall),
            quietOn = o.optBoolean("quietOn", d.quietOn), quietFrom = o.optInt("quietFrom", d.quietFrom), quietTo = o.optInt("quietTo", d.quietTo),
            autostart = o.optBoolean("autostart", d.autostart), rotation = o.enumOr("rotation", d.rotation),
        ).sanitized()
    }

    private fun reminderJson(r: Reminder) = JSONObject()
        .put("id", r.id).put("min", r.minuteOfDay).put("repeat", r.repeat.name).put("days", JSONArray(r.days.sorted()))
        .put("date", r.date).put("text", r.text).put("action", r.action.name).put("ex", r.exerciseId)
        .put("screenOff", r.screenOff.name).put("on", r.enabled)

    private fun reminderFrom(o: JSONObject): Reminder? {
        val text = o.optString("text").trim()
        if (text.isEmpty()) return null
        val days = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) }.filter { it in 1..7 }.toSet() } ?: emptySet()
        return Reminder(
            id = o.optInt("id").takeIf { it > 0 } ?: return null,
            minuteOfDay = o.optInt("min").coerceIn(0, 1439),
            repeat = o.enumOr("repeat", Repeat.DAILY), days = days, date = o.optString("date"),
            text = text.take(Limits.REMINDER_TEXT_MAX), action = o.enumOr("action", RemAction.SAY),
            exerciseId = o.optString("ex", "walk"), screenOff = o.enumOr("screenOff", ScreenOff.NOTIFY),
            enabled = o.optBoolean("on", true),
        )
    }
}

/** 把讀進來或使用者輸入的設定校正到合法範圍。 */
fun Settings.sanitized(): Settings {
    val peek = peekAfterSec.coerceIn(Limits.peekAfter)
    // 喝水時段不跨午夜（設定畫面也這樣檢查）；壞掉的資料退回預設
    val wf = waterFrom.coerceIn(0, 1439)
    val wt = waterTo.coerceIn(0, 1439)
    val okWater = wf < wt
    return copy(
        character = character.takeIf { c -> CHARACTERS.any { it.first == c } } ?: "rigatoni",
        intervalMin = intervalMin.coerceIn(Limits.interval),
        breakResetMin = breakResetMin.coerceIn(Limits.breakReset),
        waterEveryMin = waterEveryMin.coerceIn(Limits.waterEvery),
        waterFrom = if (okWater) wf else 8 * 60,
        waterTo = if (okWater) wt else 22 * 60,
        snoozeMin = snoozeMin.coerceIn(Limits.snooze),
        peekAfterSec = peek,
        dismissAfterSec = dismissAfterSec.coerceIn(Limits.dismissAfter).coerceAtLeast(peek + 1),
        recallWithinMin = recallWithinMin.coerceIn(Limits.recallWithin),
        quietFrom = quietFrom.coerceIn(0, 1439),
        quietTo = quietTo.coerceIn(0, 1439),
    )
}
