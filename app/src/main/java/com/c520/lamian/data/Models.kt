package com.c520.lamian.data

enum class IdleMode { HIDDEN, PEEK }
enum class Entrance { WALK, DROP }
enum class Side { RIGHT, LEFT }
enum class ActorSize(val dp: Int) { S(88), M(112), L(136) }
enum class PeekPos { TOP, MIDDLE, BOTTOM }
enum class Rotation { ORDER, RANDOM }

/** 所有可自訂的設定。時間一律存「當天第幾分鐘」。 */
data class Settings(
    /** 總開關：關掉就完全停止（不提醒、沒有常駐通知、麵條不出現） */
    val enabled: Boolean = true,
    val idleMode: IdleMode = IdleMode.HIDDEN,
    val entrance: Entrance = Entrance.WALK,
    val side: Side = Side.RIGHT,
    val actorSize: ActorSize = ActorSize.M,
    val peekPos: PeekPos = PeekPos.MIDDLE,
    val character: String = "rigatoni",
    val stretchOn: Boolean = true,
    val intervalMin: Int = 30,
    val breakResetMin: Int = 5,
    val waterOn: Boolean = true,
    val waterEveryMin: Int = 60,
    val waterFrom: Int = 8 * 60,
    val waterTo: Int = 22 * 60,
    val snoozeMin: Int = 10,
    val peekAfterSec: Int = 20,
    val dismissAfterSec: Int = 60,
    val recallWithinMin: Int = 30,
    val expandSteps: Boolean = false,
    val holdInCall: Boolean = true,
    val quietOn: Boolean = false,
    val quietFrom: Int = 23 * 60,
    val quietTo: Int = 7 * 60,
    val autostart: Boolean = true,
    val rotation: Rotation = Rotation.ORDER,
)

/** 數字設定的可調範圍：設定畫面的輸入檢查與讀檔時的校正都用這份。 */
object Limits {
    val interval = 5..240
    val breakReset = 1..60
    val waterEvery = 15..240
    val snooze = 1..120
    val peekAfter = 5..120
    val dismissAfter = 10..600
    val recallWithin = 0..120
    val exerciseSeconds = 10..600
    const val REMINDER_TEXT_MAX = 40
    const val SPEC_TEXT_MAX = 20
}

enum class Repeat { DAILY, WEEKDAYS, CUSTOM, ONCE }
enum class RemAction { SAY, EXERCISE, WATER }
enum class ScreenOff { NOTIFY, WAIT }

data class Reminder(
    val id: Int,
    val minuteOfDay: Int,
    val repeat: Repeat,
    /** 1＝週一 … 7＝週日（java.time.DayOfWeek.value） */
    val days: Set<Int> = emptySet(),
    /** 單次提醒的日期，yyyy-MM-dd */
    val date: String = "",
    val text: String,
    val action: RemAction = RemAction.SAY,
    val exerciseId: String = "walk",
    val screenOff: ScreenOff = ScreenOff.NOTIFY,
    val enabled: Boolean = true,
)

data class Exercise(
    val id: String,
    val name: String,
    val instruction: String,
    val spec: String,
    val seconds: Int,
    val steps: List<String>,
    val tip: String,
    /** 新動作提案：還沒有麵條示範動畫 */
    val proposed: Boolean = false,
    val enabled: Boolean = true,
)

data class LogEntry(val at: Long, val label: String, val result: String, val ok: Boolean)

data class Today(val day: String, val water: Int = 0, val stretch: Int = 0, val walk: Int = 0)

data class Missed(val exerciseId: String, val at: Long)

/** 服務即時狀態（不存檔），給 App 首頁與通知顯示。 */
data class Live(
    val serviceRunning: Boolean = false,
    val screenActive: Boolean = true,
    /** 換算成「從什麼時候開始連續滑」：已用時間 = 現在 − usedSince。螢幕關著時是 null */
    val usedSince: Long? = null,
)

data class AppState(
    val settings: Settings,
    val exercises: List<Exercise>,
    val reminders: List<Reminder>,
    val today: Today,
    val log: List<LogEntry>,
    val pausedUntil: Long?,
    val missed: Missed?,
    val nextWaterAt: Long?,
    val rotationIndex: Int,
    val live: Live = Live(),
)

/** 伸展、喝水、自訂提醒的「動作 id → 精靈圖動作名」：沒有專屬示範動畫的用 idle。 */
val DEMO_POSES = setOf("chin-tuck", "doorway", "scap", "t-ext", "hip-flexor", "neck-side", "calf-raise", "walk")

val CHARACTERS = listOf(
    Triple("rigatoni", "Toni", "水管麵"),
    Triple("spaghetti", "Sammy", "直麵"),
    Triple("penne", "Patty", "筆管麵"),
    Triple("lasagna", "Lenny", "千層麵"),
    Triple("sprout", "Sprout", "小芽"),
)
