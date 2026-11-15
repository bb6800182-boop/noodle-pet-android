package com.c520.lamian.data

import com.c520.lamian.L
import com.c520.lamian.engine.Schedule
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** 畫面、泡泡、通知共用的文字。 */

fun secText(s: Int): String = when {
    s < 60 -> L.t("$s 秒", "$s sec")
    s % 60 == 0 -> L.t("${s / 60} 分鐘", "${s / 60} min")
    else -> L.t("${s / 60} 分 ${s % 60} 秒", "${s / 60} min ${s % 60} sec")
}

/** 「每邊 30 秒・約 1 分鐘」：說明裡已經有秒數就不重複。 */
fun specLine(e: Exercise): String = if (e.spec.contains(secText(e.seconds))) e.spec else L.t("${e.spec}・約 ${secText(e.seconds)}", "${e.spec} · about ${secText(e.seconds)}")

private fun two(n: Int) = n.toString().padStart(2, '0')

fun hhmm(minuteOfDay: Int): String = "${two(minuteOfDay / 60)}:${two(minuteOfDay % 60)}"

fun hhmm(millis: Long): String = hhmm(Schedule.minuteOfDay(millis))

val WEEK: List<String>
    get() = if (L.zh) listOf("", "一", "二", "三", "四", "五", "六", "日") else listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun repeatText(r: Reminder): String = when (r.repeat) {
    Repeat.DAILY -> L.t("每天", "Every day")
    Repeat.WEEKDAYS -> L.t("平日", "Weekdays")
    Repeat.CUSTOM -> L.t("每週", "Weekly ") + r.days.sorted().joinToString(L.t("、", ", ")) { WEEK[it] }
    Repeat.ONCE -> r.date.split("-").let { p -> if (p.size == 3) L.t("${p[1].toInt()}/${p[2].toInt()} 一次", "Once on ${p[1].toInt()}/${p[2].toInt()}") else L.t("一次", "Once") }
}

fun actionText(r: Reminder, exerciseName: String?): String =
    when (r.action) {
        RemAction.SAY -> L.t("說一句話", "Say a line")
        RemAction.WATER -> L.t("喝水", "Water")
        RemAction.EXERCISE -> L.t("帶動作：", "Exercise: ") + (exerciseName ?: L.t("（動作已不存在）", "(exercise removed)"))
    } + if (r.screenOff == ScreenOff.WAIT) L.t("・解鎖後才出現", " · after unlock") else ""

fun greeting(now: ZonedDateTime = ZonedDateTime.now()): String = when (now.hour) {
    in 5..10 -> L.t("早安", "Good morning")
    in 11..13 -> L.t("午安", "Good day")
    in 14..17 -> L.t("下午好", "Good afternoon")
    else -> L.t("晚上好", "Good evening")
}

fun quietNow(s: Settings, now: Long): Boolean =
    s.quietOn && s.quietFrom != s.quietTo && Schedule.inWindow(Schedule.minuteOfDay(now), s.quietFrom, s.quietTo)

/** 伸展、喝水被「暫停」或「安靜時段」擋住時，擋到什麼時候；沒擋住是 null。 */
fun blockedUntil(st: AppState, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    val s = st.settings
    val paused = st.pausedUntil?.takeIf { it > now }
    val quiet = if (quietNow(s, now)) {
        Schedule.clampToWindow(Instant.ofEpochMilli(now).atZone(zone), s.quietTo, s.quietFrom).toInstant().toEpochMilli()
    } else null
    return listOfNotNull(paused, quiet).maxOrNull()
}

/** 錯過的伸展還能不能補做。 */
fun validMissed(st: AppState, now: Long): Missed? {
    val within = st.settings.recallWithinMin
    return st.missed?.takeIf { within > 0 && now - it.at in 0..within * 60_000L }
}

/**
 * 目前狀態的一句話。forNotification＝true 時不寫「已經滑了幾分鐘」（通知不會每分鐘更新），改寫預計提醒的時間。
 */
fun statusLine(st: AppState, now: Long, forNotification: Boolean = false): String {
    val s = st.settings
    val live = st.live
    val paused = st.pausedUntil?.takeIf { it > now }
    return when {
        !s.enabled -> L.t("拉麵關著，不會提醒你。", "Lamian is off. No reminders.")
        !live.screenActive -> L.t("螢幕關著，休息中。", "Screen off. Resting.")
        paused != null -> L.t("暫停到 ${hhmm(paused)}，自訂提醒照常。", "Paused until ${hhmm(paused)}. Custom reminders still come.")
        !s.stretchOn -> L.t("滑手機提醒關著。", "Screen-time reminders are off.")
        quietNow(s, now) -> L.t("安靜時段，先不提醒伸展和喝水。", "Quiet hours: no stretch or water reminders for now.")
        live.usedSince == null -> L.t("準備中。", "Getting ready.")
        else -> {
            val used = ((now - live.usedSince) / 60_000L).toInt().coerceAtLeast(0)
            val due = live.usedSince + s.intervalMin * 60_000L
            val left = s.intervalMin - used
            when {
                forNotification && left > 0 -> L.t("大約 ${hhmm(due)} 提醒你起來動一動。", "I'll remind you to move at about ${hhmm(due)}.")
                left > 0 -> L.t("已經連續滑了 $used 分鐘，再 $left 分鐘提醒你動一動。", "$used min of screen time so far. A reminder to move comes in $left min.")
                else -> L.t("已經連續滑了 $used 分鐘，該起來動一動了。", "$used min of screen time. Time to get up and move.")
            }
        }
    }
}
