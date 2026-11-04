package com.c520.lamian.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.c520.lamian.R
import com.c520.lamian.data.AppState
import com.c520.lamian.data.Exercise
import com.c520.lamian.data.Store
import com.c520.lamian.data.hhmm
import com.c520.lamian.data.statusLine
import com.c520.lamian.overlay.ReminderSpec
import com.c520.lamian.ui.MainActivity

/**
 * 通知：
 * - status（低）：常駐，今天的次數＋三個快速按鈕。
 * - remind（高、會震動）：螢幕關著、通話中、或沒有「顯示在其他應用程式上層」權限時的提醒。
 */
object Notifications {
    const val CH_STATUS = "status"
    const val CH_REMIND = "remind"
    const val ID_STATUS = 1
    private const val ID_MISSED = 2

    const val ACTION_WATER_NOW = "com.c520.lamian.n.WATER_NOW"
    const val ACTION_STRETCH_NOW = "com.c520.lamian.n.STRETCH_NOW"
    const val ACTION_PAUSE = "com.c520.lamian.n.PAUSE"
    const val ACTION_RESUME = "com.c520.lamian.n.RESUME"
    const val ACTION_RECALL = "com.c520.lamian.n.RECALL"
    const val ACTION_DRANK = "com.c520.lamian.n.DRANK"
    const val ACTION_ACK = "com.c520.lamian.n.ACK"
    const val ACTION_DONE = "com.c520.lamian.n.DONE"
    const val ACTION_SNOOZE = "com.c520.lamian.n.SNOOZE"
    const val EXTRA_KEY = "key"
    const val EXTRA_EXERCISE = "ex"

    private fun nm(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels(context: Context) {
        val nm = nm(context) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_status_desc)
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, context.getString(R.string.channel_remind), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_remind_desc)
                enableVibration(true)
            },
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 按鈕一律送到不對外的 ActionReceiver；requestCode 依動作＋對象區分，避免互相覆蓋。 */
    private fun action(context: Context, title: String, action: String, key: String = "", unlock: Boolean = false, ex: String? = null): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            context, (action + key).hashCode(),
            Intent(context, ActionReceiver::class.java).setAction(action).putExtra(EXTRA_KEY, key).putExtra(EXTRA_EXERCISE, ex),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_lamian), title, pi)
            .setAuthenticationRequired(unlock)
            .build()
    }

    // ---------- 常駐 ----------

    fun status(context: Context, st: AppState = Store.current, now: Long = System.currentTimeMillis()): Notification {
        val t = st.today
        val paused = (st.pausedUntil ?: 0L) > now
        return Notification.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_lamian)
            .setContentTitle("今天 ${t.water} 杯水・${t.stretch + t.walk} 組伸展")
            .setContentText(statusLine(st, now, forNotification = true))
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .addAction(action(context, "喝了一杯", ACTION_WATER_NOW))
            .addAction(action(context, "現在做一組", ACTION_STRETCH_NOW, unlock = true))
            .addAction(if (paused) action(context, "恢復提醒", ACTION_RESUME) else action(context, "暫停 1 小時", ACTION_PAUSE))
            .build()
    }

    fun postStatus(context: Context) = runCatching { nm(context)?.notify(ID_STATUS, status(context)) }

    // ---------- 錯過的伸展 ----------

    fun postMissed(context: Context, e: Exercise, at: Long, withinMin: Int) = runCatching {
        val n = Notification.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_lamian)
            .setContentTitle("剛剛的「${e.name}」還沒做")
            .setContentText("$withinMin 分鐘內可以補做")
            .setWhen(at).setShowWhen(true)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setTimeoutAfter(withinMin * 60_000L)
            .addAction(action(context, "補做", ACTION_RECALL, unlock = true))
            .build()
        nm(context)?.notify(ID_MISSED, n)
    }

    fun cancelMissed(context: Context) = runCatching { nm(context)?.cancel(ID_MISSED) }

    // ---------- 提醒 ----------

    private fun idFor(sp: ReminderSpec): Int = sp.reminder?.let { 1000 + it.id } ?: when (sp) {
        is ReminderSpec.Water -> 3
        else -> 4
    }

    /**
     * alert＝false：螢幕關掉時把進行到一半的提醒改成安靜通知（不再震一次）。
     * inCall：通話中只先告訴你，掛斷後麵條再出來。fallback：沒有懸浮權限，只能靠通知。
     */
    fun postRemind(context: Context, sp: ReminderSpec, alert: Boolean, inCall: Boolean = false, fallback: Boolean = false) = runCatching {
        val r = sp.reminder
        val key = sp.key
        val tag = r?.let { "提醒・${hhmm(it.minuteOfDay)}" }
        val later = if (fallback) "" else "解鎖後麵條會"
        val b = Notification.Builder(context, if (alert) CH_REMIND else CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_lamian)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setShowWhen(true)
        when (sp) {
            is ReminderSpec.Water -> {
                b.setContentTitle(tag ?: "喝水時間")
                    .setContentText(if (inCall) "通話中，掛斷後麵條再出來提醒你。" else if (fallback) "喝口水吧！" else "喝口水吧！${later}拿著杯子等你。")
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .addAction(action(context, "喝了", ACTION_DRANK, key))
                    .addAction(action(context, "晚點", ACTION_SNOOZE, key))
            }
            is ReminderSpec.Say -> {
                b.setContentTitle(sp.reminder.text)
                    .setContentText(if (inCall) "通話中，掛斷後麵條再出來提醒你。" else tag)
                    .setPublicVersion(publicVersion(context, tag))
                    .addAction(action(context, "知道了", ACTION_ACK, key))
                    .addAction(action(context, "晚點", ACTION_SNOOZE, key))
            }
            is ReminderSpec.Stretch -> {
                val e = sp.exercise
                if (r != null) {
                    b.setContentTitle(r.text)
                        .setContentText(if (inCall) "通話中，掛斷後麵條再出來帶你做「${e.name}」。" else if (fallback) "${tag}・${e.name}：${e.instruction}" else "${tag}・${later}帶你做「${e.name}」")
                        .setPublicVersion(publicVersion(context, tag))
                } else {
                    b.setContentTitle("起來動一動：${e.name}").setContentText(e.instruction)
                }
                if (fallback) b.addAction(action(context, "做完了", ACTION_DONE, key, ex = e.id))
                b.addAction(action(context, "晚點", ACTION_SNOOZE, key, ex = e.id))
            }
            ReminderSpec.Visit -> return@runCatching
        }
        nm(context)?.notify(idFor(sp), b.build())
    }

    private fun publicVersion(context: Context, tag: String?): Notification =
        Notification.Builder(context, CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat_lamian)
            .setContentTitle("拉麵")
            .setContentText(tag ?: "有一則提醒")
            .build()

    fun cancelRemind(context: Context, sp: ReminderSpec) = runCatching { nm(context)?.cancel(idFor(sp)) }
}
