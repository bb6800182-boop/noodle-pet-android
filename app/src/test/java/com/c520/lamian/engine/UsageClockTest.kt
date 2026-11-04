package com.c520.lamian.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageClockTest {
    private val min = 60_000L

    @Test fun countsOnlyWhileActive() {
        val c = UsageClock(5 * min)
        c.onActive(0)
        c.onInactive(10 * min)
        assertEquals(10 * min, c.usedMs(12 * min))
        c.onActive(12 * min)
        assertEquals(13 * min, c.usedMs(15 * min))
    }

    @Test fun shortBreakKeepsCounting() {
        val c = UsageClock(5 * min)
        c.onActive(0)
        c.onInactive(20 * min)
        assertFalse(c.onActive(24 * min))
        assertEquals(26 * min, c.usedMs(30 * min))
    }

    @Test fun longBreakResets() {
        val c = UsageClock(5 * min)
        c.onActive(0)
        c.onInactive(20 * min)
        assertTrue(c.onActive(25 * min))
        assertEquals(2 * min, c.usedMs(27 * min))
    }

    @Test fun lockedScreenOnOffKeepsFirstOffTime() {
        val c = UsageClock(5 * min)
        c.onActive(0)
        c.onInactive(10 * min)
        c.onInactive(13 * min) // 亮了一下沒解鎖又關掉
        assertTrue(c.onActive(16 * min))
    }

    @Test fun snoozeSetsRemaining() {
        val c = UsageClock(5 * min)
        c.onActive(0)
        c.setUsed(31 * min, 20 * min)
        assertEquals(25 * min, c.usedMs(36 * min))
        c.reset(40 * min)
        assertEquals(0L, c.usedMs(40 * min))
    }
}
