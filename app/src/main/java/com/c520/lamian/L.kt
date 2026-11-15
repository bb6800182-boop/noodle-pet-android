package com.c520.lamian

import java.util.Locale

/**
 * 介面語言：手機是中文就顯示中文，其他語言一律英文。
 * 中英文寫在同一行（L.t("中文", "English")），改字時才不會漏改一邊。
 */
object L {
    val zh: Boolean get() = Locale.getDefault().language == "zh"
    fun t(zh: String, en: String): String = if (this.zh) zh else en
}
