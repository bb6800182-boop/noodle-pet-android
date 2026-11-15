package com.c520.lamian.data

import com.c520.lamian.L
import android.content.Context
import org.json.JSONArray

/** 動作清單：拉麵原有的 8 個動作（assets/exercises.json）＋3 個適合滑手機的新動作提案。 */
object ExerciseCatalog {

    private val PROPOSED get() = listOf(
        Exercise(
            "wrist", L.t("手腕伸展", "Wrist stretch"), L.t("手臂伸直、掌心朝前，另一手把手指往回扳。", "Arm straight, palm forward; pull the fingers back with the other hand."), L.t("每邊 15 秒", "15 sec each side"), 30,
            listOf(L.t("一手往前伸直，掌心朝前、指尖朝上。", "Hold one arm straight out, palm forward, fingers up."), L.t("另一手輕輕把手指往身體方向扳。", "With the other hand, gently pull the fingers toward you."), L.t("前臂內側有拉伸感，停 15 秒。", "Feel the stretch along the inner forearm. Hold 15 seconds."), L.t("換手再做一次。", "Switch hands and repeat.")),
            L.t("手肘打直但不要鎖死，拉到有感覺就好，不要硬扳。", "Keep the elbow straight but not locked. Stretch until you feel it; don't force it."), proposed = true, enabled = false,
        ),
        Exercise(
            "fingers", L.t("手指張握", "Finger spread and fist"), L.t("手掌用力張開再握拳，最後拇指畫圈。", "Spread the hand wide, make a fist, then circle the thumbs."), L.t("張握 10 下＋畫圈", "10 reps + circles"), 40,
            listOf(L.t("雙手五指盡量撐開，停 2 秒。", "Spread all fingers as wide as you can. Hold 2 seconds."), L.t("慢慢握拳再張開，這樣是一下，做 10 下。", "Slowly make a fist and open again. That is one rep; do 10."), L.t("拇指慢慢畫圈，正反各 5 圈。", "Circle the thumbs slowly, 5 times each way.")),
            L.t("動作放慢，拇指根部痠痛就減少次數。", "Go slowly. If the base of the thumb aches, do fewer reps."), proposed = true, enabled = false,
        ),
        Exercise(
            "far", L.t("看遠方", "Look far away"), L.t("看 6 公尺外的東西 20 秒，讓眼睛放鬆。", "Look at something 6 m (20 ft) away for 20 seconds to relax the eyes."), L.t("20 秒", "20 sec"), 20,
            listOf(L.t("找一個 6 公尺以外的目標，例如窗外。", "Pick a target more than 6 m (20 ft) away, such as out the window."), L.t("盯著看 20 秒，記得自然眨眼。", "Look at it for 20 seconds. Blink naturally."), L.t("閉上眼睛休息幾秒再回來。", "Close your eyes for a few seconds before you return.")),
            L.t("看遠方時放鬆眉頭，不要瞇眼。", "Relax your brow while looking; don't squint."), proposed = true, enabled = false,
        ),
    )

    fun load(context: Context): List<Exercise> {
        val text = context.assets.open(if (L.zh) "exercises.json" else "exercises.en.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONArray(text.removePrefix("\uFEFF"))
        val base = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val steps = o.optJSONArray("steps") ?: JSONArray()
            Exercise(
                id = o.getString("id"),
                name = o.getString("name"),
                instruction = o.optString("instruction"),
                spec = o.optString("spec"),
                seconds = o.optInt("seconds", 30),
                steps = (0 until steps.length()).map { steps.getString(it) },
                tip = o.optString("tip"),
            )
        }
        return base + PROPOSED
    }
}
