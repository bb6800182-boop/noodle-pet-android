package com.c520.lamian

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

/**
 * 「關於」的聯繫作者、更多軟體（作者的每個 App 都一樣，照傾鬆滑的關於頁）。
 * 只開寄信的 App 和瀏覽器；App 自己不連網，也不需要任何權限。
 */
object About {
    const val EMAIL = "bb6800182@gmail.com"
    val HOME: String get() = L.t("https://c520.pages.dev/", "https://c520.pages.dev/en/")
    val CONTACT_TEXT: String get() = L.t("有建議、想要的功能，或遇到問題（bug），都歡迎寫信告訴我。", "Suggestions, feature requests or bugs? Send me an email.")
    val MORE_TEXT: String get() = L.t("作者的其他 App 都放在 C520，歡迎去看看。", "My other apps are on C520. Come take a look.")

    /** 打開寄信的 App，主旨和內文先填好 App 版本與手機型號；沒有寄信的 App 就提示信箱。 */
    fun mail(c: Context, app: String, version: String) {
        val body = L.t("（請寫下你的建議；回報問題的話，請說明做了什麼、發生什麼事）", "(Write your suggestion here. For a bug, please say what you did and what happened.)") +
            "\n\n\n\n——\n$app $version\n" +
            L.t("手機：", "Device: ") + "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n"
        val subject = L.t("${app}意見回饋（${version}）", "$app feedback ($version)")
        val uri = Uri.parse("mailto:$EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        open(c, Intent(Intent.ACTION_SENDTO, uri), L.t("找不到寄信的 App。請寄信到 $EMAIL。", "No email app found. Please write to $EMAIL."))
    }

    /** 用瀏覽器打開 C520 首頁（英文介面開英文版）。 */
    fun more(c: Context) = open(c, Intent(Intent.ACTION_VIEW, Uri.parse(HOME)), L.t("找不到瀏覽器。網址：$HOME", "No browser found. Address: $HOME"))

    private fun open(c: Context, i: Intent, fallback: String) {
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(c, fallback, Toast.LENGTH_LONG).show()
        }
    }
}
