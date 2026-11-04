package com.c520.lamian.engine

import com.c520.lamian.data.Reminder
import com.c520.lamian.data.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** 時間計算（純邏輯）。時段一律用「當天第幾分鐘」，結束時間不含；開始大於結束代表跨午夜。 */
object Schedule {

    fun inWindow(minuteOfDay: Int, from: Int, to: Int): Boolean =
        if (from <= to) minuteOfDay in from until to else minuteOfDay >= from || minuteOfDay < to

    fun minuteOfDay(t: ZonedDateTime): Int = t.hour * 60 + t.minute

    fun minuteOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        minuteOfDay(Instant.ofEpochMilli(millis).atZone(zone))

    /** t 落在時段內就回傳 t，否則回傳下一個時段開始的時間。 */
    fun clampToWindow(t: ZonedDateTime, from: Int, to: Int): ZonedDateTime {
        if (from == to || inWindow(minuteOfDay(t), from, to)) return t
        val start = LocalTime.of(from / 60, from % 60)
        var candidate = t.toLocalDate().atTime(start).atZone(t.zone)
        if (!candidate.isAfter(t)) candidate = t.toLocalDate().plusDays(1).atTime(start).atZone(t.zone)
        return candidate
    }

    /** 下一次喝水提醒：現在＋間隔，落在時段外就延到下一個時段開始。 */
    fun nextWater(nowMillis: Long, everyMin: Int, from: Int, to: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val t = Instant.ofEpochMilli(nowMillis).atZone(zone).plusMinutes(everyMin.toLong())
        return clampToWindow(t, from, to).toInstant().toEpochMilli()
    }

    /** 這個提醒在 now 之後的下一次時間；關掉、單次已過期或日期壞掉時是 null。 */
    fun nextReminderAt(r: Reminder, now: ZonedDateTime): ZonedDateTime? {
        if (!r.enabled) return null
        val time = LocalTime.of(r.minuteOfDay / 60, r.minuteOfDay % 60)
        if (r.repeat == Repeat.ONCE) {
            val date = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: return null
            val t = date.atTime(time).atZone(now.zone)
            return if (t.isAfter(now)) t else null
        }
        if (r.repeat == Repeat.CUSTOM && r.days.isEmpty()) return null
        for (i in 0..7) {
            val d = now.toLocalDate().plusDays(i.toLong())
            val t = d.atTime(time).atZone(now.zone)
            if (!t.isAfter(now)) continue
            val dow = d.dayOfWeek.value
            val ok = when (r.repeat) {
                Repeat.DAILY -> true
                Repeat.WEEKDAYS -> dow <= 5
                Repeat.CUSTOM -> dow in r.days
                Repeat.ONCE -> false
            }
            if (ok) return t
        }
        return null
    }

    /** 所有提醒中最早的下一次，以及同一分鐘要一起跳的提醒 id。 */
    fun earliest(reminders: List<Reminder>, now: ZonedDateTime): Pair<ZonedDateTime, List<Int>>? {
        val next = reminders.mapNotNull { r -> nextReminderAt(r, now)?.let { it to r.id } }
        val min = next.minByOrNull { it.first.toInstant() }?.first ?: return null
        return min to next.filter { it.first.toInstant() == min.toInstant() }.map { it.second }
    }
}
