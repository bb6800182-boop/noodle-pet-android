package com.c520.lamian.overlay

import com.c520.lamian.data.Exercise
import com.c520.lamian.data.Reminder

/** 要麵條出來做的事。reminder 不是 null 代表是自訂提醒（要按「知道了」才收、不受暫停影響）。 */
sealed interface ReminderSpec {
    val reminder: Reminder?
    /** 同一件事的辨識碼：通知 id、排隊去重都用它 */
    val key: String

    data class Stretch(val exercise: Exercise, val manual: Boolean = false, override val reminder: Reminder? = null) : ReminderSpec {
        override val key: String get() = reminder?.let { "rem:${it.id}" } ?: "stretch"
    }

    data class Water(override val reminder: Reminder? = null) : ReminderSpec {
        override val key: String get() = reminder?.let { "rem:${it.id}" } ?: "water"
    }

    data class Say(override val reminder: Reminder) : ReminderSpec {
        override val key: String get() = "rem:${reminder.id}"
    }

    /** 點側邊探頭的麵條：出來打招呼 */
    data object Visit : ReminderSpec {
        override val reminder: Reminder? get() = null
        override val key: String get() = "visit"
    }
}

val ReminderSpec.sticky: Boolean get() = reminder != null
