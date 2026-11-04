package com.c520.lamian.data

import android.content.Context
import org.json.JSONArray

/** 動作清單：拉麵原有的 8 個動作（assets/exercises.json）＋3 個適合滑手機的新動作提案。 */
object ExerciseCatalog {

    private val PROPOSED = listOf(
        Exercise(
            "wrist", "手腕伸展", "手臂伸直、掌心朝前，另一手把手指往回扳。", "每邊 15 秒", 30,
            listOf("一手往前伸直，掌心朝前、指尖朝上。", "另一手輕輕把手指往身體方向扳。", "前臂內側有拉伸感，停 15 秒。", "換手再做一次。"),
            "手肘打直但不要鎖死，拉到有感覺就好，不要硬扳。", proposed = true, enabled = false,
        ),
        Exercise(
            "fingers", "手指張握", "手掌用力張開再握拳，最後拇指畫圈。", "張握 10 下＋畫圈", 40,
            listOf("雙手五指盡量撐開，停 2 秒。", "慢慢握拳再張開，這樣是一下，做 10 下。", "拇指慢慢畫圈，正反各 5 圈。"),
            "動作放慢，拇指根部痠痛就減少次數。", proposed = true, enabled = false,
        ),
        Exercise(
            "far", "看遠方", "看 6 公尺外的東西 20 秒，讓眼睛放鬆。", "20 秒", 20,
            listOf("找一個 6 公尺以外的目標，例如窗外。", "盯著看 20 秒，記得自然眨眼。", "閉上眼睛休息幾秒再回來。"),
            "看遠方時放鬆眉頭，不要瞇眼。", proposed = true, enabled = false,
        ),
    )

    fun load(context: Context): List<Exercise> {
        val text = context.assets.open("exercises.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
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
