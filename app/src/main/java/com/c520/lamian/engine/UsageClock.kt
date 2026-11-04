package com.c520.lamian.engine

/**
 * 連續使用計時（純邏輯，方便單元測試）。
 * 螢幕亮著且解鎖才算「使用中」；關螢幕暫停計時，關超過休息門檻就算休息過、下次從 0 開始。
 */
class UsageClock(private var breakResetMs: Long) {
    private var accumulatedMs = 0L
    private var sessionStart: Long? = null
    private var offSince: Long? = null

    val active: Boolean get() = sessionStart != null

    fun setBreakReset(ms: Long) { breakResetMs = ms }

    /** 螢幕亮著且已解鎖。回傳 true 表示剛休息過（計時歸零）。 */
    fun onActive(now: Long): Boolean {
        if (sessionStart != null) return false
        val off = offSince
        val rested = off != null && now - off >= breakResetMs
        if (rested) accumulatedMs = 0
        offSince = null
        sessionStart = now
        return rested
    }

    /** 關螢幕。只鎖住沒解鎖又關掉時，休息起點維持原本那次關螢幕。 */
    fun onInactive(now: Long) {
        val s = sessionStart
        if (s != null) {
            accumulatedMs += (now - s).coerceAtLeast(0)
            sessionStart = null
            offSince = now
        } else if (offSince == null) {
            offSince = now
        }
    }

    fun usedMs(now: Long): Long = accumulatedMs + (sessionStart?.let { (now - it).coerceAtLeast(0) } ?: 0L)

    /** 做完、跳過或沒理：重新計時。 */
    fun reset(now: Long) = setUsed(now, 0L)

    /** 按「晚點」：把已用時間設成「再用幾分鐘就提醒」。 */
    fun setUsed(now: Long, ms: Long) {
        accumulatedMs = ms.coerceAtLeast(0)
        if (sessionStart != null) sessionStart = now
    }

    /** 已關螢幕多久（亮著時是 0）。 */
    fun offForMs(now: Long): Long = offSince?.let { now - it } ?: 0L
}
