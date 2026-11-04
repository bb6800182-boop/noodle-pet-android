package com.c520.lamian.data

import com.c520.lamian.engine.Schedule
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** 畫面、泡泡、通知共用的文字。 */

fun secText(s: Int): String = when {
    s < 60 -> "$s 秒"
    s % 60 == 0 -> "${s / 60} 分鐘"
    else -> "${s / 60} 分 ${s % 60} 秒"
}

/** 「每邊 30 秒・約 1 分鐘」：說明裡已經有秒數就不重複。 */
fun specLine(e: Exercise): String = if (e.spec.contains(secText(e.seconds))) e.spec else "${e.spec}・約 ${secText(e.seconds)}"

private fun two(n: Int) = n.toString().padStart(2, '0')

fun hhmm(minuteOfDay: Int): String = "${two(minuteOfDay / 60)}:${two(minuteOfDay % 60)}"

fun hhmm(millis: Long): String = hhmm(Schedule.minuteOfDay(millis))

val WEEK = listOf("", "一", "二", "三", "四", "五", "六", "日")

fun repeatText(r: Reminder): String = when (r.repeat) {
    Repeat.DAILY -> "每天"
    Repeat.WEEKDAYS -> "平日"
    Repeat.CUSTOM -> "每週" + r.days.sorted().joinToString("、") { WEEK[it] }
    Repeat.ONCE -> r.date.split("-").let { p -> if (p.size == 3) "${p[1].toInt()}/${p[2].toInt()} 一次" else "一次" }
}

fun actionText(r: Reminder, exerciseName: String?): String =
    when (r.action) {
        RemAction.SAY -> "說一句話"
        RemAction.WATER -> "喝水"
        RemAction.EXERCISE -> "帶動作：${exerciseName ?: "（動作已不存在）"}"
    } + if (r.screenOff == ScreenOff.WAIT) "・解鎖後才出現" else ""

fun greeting(now: ZonedDateTime = ZonedDateTime.now()): String = when (now.hour) {
    in 5..10 -> "早安"
    in 11..13 -> "午安"
    in 14..17 -> "下午好"
    else -> "晚上好"
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
        !s.enabled -> "拉麵關著，不會提醒你。"
        !live.screenActive -> "螢幕關著，休息中。"
        paused != null -> "暫停到 ${hhmm(paused)}，自訂提醒照常。"
        !s.stretchOn -> "滑手機提醒關著。"
        quietNow(s, now) -> "安靜時段，先不提醒伸展和喝水。"
        live.usedSince == null -> "準備中。"
        else -> {
            val used = ((now - live.usedSince) / 60_000L).toInt().coerceAtLeast(0)
            val due = live.usedSince + s.intervalMin * 60_000L
            val left = s.intervalMin - used
            when {
                forNotification && left > 0 -> "大約 ${hhmm(due)} 提醒你起來動一動。"
                left > 0 -> "已經連續滑了 $used 分鐘，再 $left 分鐘提醒你動一動。"
                else -> "已經連續滑了 $used 分鐘，該起來動一動了。"
            }
        }
    }
}
