package com.c520.lamian.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.c520.lamian.data.Store
import com.c520.lamian.engine.Alarms

/** 鬧鐘與通知按鈕的入口（不對外，只有自己的 PendingIntent 打得進來），交給常駐服務處理。 */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action.isNullOrEmpty()) return
        PetService.dispatch(context, intent)
    }
}

/** 開機、App 更新後重新啟動；改時間或時區時重排鬧鐘。 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Store.current.settings.enabled) return
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> if (Store.current.settings.autostart) PetService.start(context)
            Intent.ACTION_MY_PACKAGE_REPLACED -> PetService.start(context)
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                Alarms.rescheduleAll(context)
                PetService.instance?.evaluate()
            }
        }
    }
}
