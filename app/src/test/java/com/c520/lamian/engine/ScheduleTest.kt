package com.c520.lamian.engine

import com.c520.lamian.data.Reminder
import com.c520.lamian.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private fun at(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone) // 11/4 是週三

    @Test fun windowNormalAndOvernight() {
        assertTrue(Schedule.inWindow(9 * 60, 8 * 60, 22 * 60))
        assertFalse(Schedule.inWindow(22 * 60, 8 * 60, 22 * 60))
        assertTrue(Schedule.inWindow(23 * 60 + 30, 23 * 60, 7 * 60))
        assertTrue(Schedule.inWindow(6 * 60, 23 * 60, 7 * 60))
        assertFalse(Schedule.inWindow(12 * 60, 23 * 60, 7 * 60))
    }

    @Test fun waterOutsideWindowMovesToNextStart() {
        val now = at(4, 21, 30).toInstant().toEpochMilli()
        val next = Schedule.nextWater(now, 60, 8 * 60, 22 * 60, zone)
        assertEquals(at(5, 8, 0).toInstant().toEpochMilli(), next)
        val noon = at(4, 12, 0).toInstant().toEpochMilli()
        assertEquals(at(4, 13, 0).toInstant().toEpochMilli(), Schedule.nextWater(noon, 60, 8 * 60, 22 * 60, zone))
    }

    @Test fun weekdaysSkipWeekend() {
        val r = Reminder(1, 15 * 60, Repeat.WEEKDAYS, text = "x")
        assertEquals(at(4, 15, 0), Schedule.nextReminderAt(r, at(4, 14, 0)))
        // 11/6 週五 16:00 之後 → 下週一 11/9
        assertEquals(at(9, 15, 0), Schedule.nextReminderAt(r, at(6, 16, 0)))
    }

    @Test fun customDaysAndOnce() {
        val r = Reminder(2, 9 * 60, Repeat.CUSTOM, days = setOf(2, 4), text = "x") // 週二、週四
        assertEquals(at(5, 9, 0), Schedule.nextReminderAt(r, at(4, 10, 0)))
        val once = Reminder(3, 8 * 60, Repeat.ONCE, date = "2026-11-07", text = "x")
        assertEquals(at(7, 8, 0), Schedule.nextReminderAt(once, at(4, 10, 0)))
        assertNull(Schedule.nextReminderAt(once, at(7, 9, 0)))
        assertNull(Schedule.nextReminderAt(r.copy(days = emptySet()), at(4, 10, 0)))
    }

    @Test fun earliestGroupsSameMinute() {
        val a = Reminder(1, 15 * 60, Repeat.DAILY, text = "a")
        val b = Reminder(2, 15 * 60, Repeat.WEEKDAYS, text = "b")
        val c = Reminder(3, 16 * 60, Repeat.DAILY, text = "c")
        val (t, ids) = Schedule.earliest(listOf(c, a, b), at(4, 14, 0))!!
        assertEquals(at(4, 15, 0), t)
        assertEquals(listOf(1, 2), ids.sorted())
    }
}
