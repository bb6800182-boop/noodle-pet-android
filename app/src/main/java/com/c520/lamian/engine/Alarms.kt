package com.c520.lamian.engine

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.c520.lamian.data.Store
import com.c520.lamian.service.ActionReceiver
import java.time.ZonedDateTime

/**
 * 系統鬧鐘：喝水（下一次）、自訂提醒（最早的下一個）、自訂提醒的「晚點」。
 * 螢幕關著也要準時，所以都用 setExactAndAllowWhileIdle（USE_EXACT_ALARM 安裝時就允許）。
 */
object Alarms {
    const val ACTION_WATER = "com.c520.lamian.alarm.WATER"
    const val ACTION_REMINDER = "com.c520.lamian.alarm.REMINDER"
    const val ACTION_SNOOZE = "com.c520.lamian.alarm.SNOOZE"
    const val EXTRA_IDS = "ids"
    const val EXTRA_ID = "id"

    private const val REQ_WATER = 1
    private const val REQ_REMINDER = 2
    private const val REQ_SNOOZE = 10_000

    private fun pending(context: Context, req: Int, action: String, fill: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            context, req,
            Intent(context, ActionReceiver::class.java).setAction(action).apply(fill),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    // 有先檢查 canScheduleExactAlarms()；權限來自 USE_EXACT_ALARM（lint 只認得 SCHEDULE_EXACT_ALARM）
    @SuppressLint("MissingPermission")
    private fun setAt(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun cancel(context: Context, pi: PendingIntent) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pi)
    }

    /** at = null 代表取消。 */
    fun setWater(context: Context, at: Long?) {
        val pi = pending(context, REQ_WATER, ACTION_WATER)
        if (at == null) cancel(context, pi) else setAt(context, at, pi)
    }

    /** 依目前的自訂提醒，排最早的下一個（同一分鐘的一起帶）。 */
    fun scheduleReminders(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val next = Schedule.earliest(Store.current.reminders, now)
        if (next == null) {
            cancel(context, pending(context, REQ_REMINDER, ACTION_REMINDER))
            return
        }
        val ids = next.second.toIntArray()
        setAt(context, next.first.toInstant().toEpochMilli(), pending(context, REQ_REMINDER, ACTION_REMINDER) { putExtra(EXTRA_IDS, ids) })
    }

    fun snooze(context: Context, reminderId: Int, at: Long) {
        if (reminderId <= 0) return
        setAt(context, at, pending(context, REQ_SNOOZE + reminderId, ACTION_SNOOZE) { putExtra(EXTRA_ID, reminderId) })
    }

    fun cancelSnooze(context: Context, reminderId: Int) {
        if (reminderId > 0) cancel(context, pending(context, REQ_SNOOZE + reminderId, ACTION_SNOOZE))
    }

    /** 總開關關掉：喝水、自訂提醒、所有「晚點」都取消。 */
    fun cancelAll(context: Context) {
        setWater(context, null)
        cancel(context, pending(context, REQ_REMINDER, ACTION_REMINDER))
        Store.current.reminders.forEach { cancelSnooze(context, it.id) }
    }

    fun rescheduleAll(context: Context) {
        if (!Store.current.settings.enabled) return
        scheduleReminders(context)
        val st = Store.current
        setWater(context, st.nextWaterAt.takeIf { st.settings.waterOn })
    }
}
