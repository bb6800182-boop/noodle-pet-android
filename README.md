# 拉麵手機版（noodle-pet-android）v0.1.1

手機上的提醒寵物：連續滑手機太久，麵條會從螢幕邊走出來（或垂降下來），親自示範一組伸展、陪你計時；也會提醒你喝水，或照你設的時間出來說一句話。

> **非官方改寫版**：角色、動作示範與說明來自 Pete Cybriwsky 的 [Noodle](https://tonitherigatoni.com/)（[原始碼](https://github.com/pcybriwsky/noodle)，MIT 授權），與原作者無關，也未經原作者背書。手機版的懸浮麵條、喝水、自訂提醒是這個版本另外做的，精靈圖沿用 [Windows 版拉麵](https://github.com/bb6800182-boop/noodle-pet)畫好的素材。想看 Pete 更多的作品：[Instagram @_re_pete](https://www.instagram.com/_re_pete/)。

## 下載

到 [Releases](https://github.com/bb6800182-boop/noodle-pet-android/releases/latest) 下載 `lamian.apk`（[介紹頁](https://c520.pages.dev/lamian-android/)有畫面和安裝步驟），手機 Android 13 以上。第一次安裝時 Android 會請你允許瀏覽器安裝應用程式；打開 App 後照首頁的提示，允許「顯示在其他應用程式上層」和「通知」。

## 功能

- **滑手機提醒**：螢幕亮著而且解鎖才算在用；連續用滿設定的分鐘數，麵條出來帶一組動作（8 個原有動作＋3 個新提案，可開關、改秒數、改說明、排順序，依序或隨機輪替）。關螢幕超過設定的分鐘數就算休息過，重新計時。
- **泡泡**：「開始」跟著做並倒數，「晚點」延後，「怎麼做」展開步驟。沒理它，過一段時間麵條會從泡泡上緣探頭，再過一段時間自己走掉；走掉的伸展在設定的時間內可以從通知或首頁補做。
- **喝水**：照時鐘每隔一段時間、只在設定的時段內提醒；麵條會拿著杯子喝。
- **自訂提醒**：每天／平日／自選星期／單次；到時間麵條說一句話，或帶一組動作、提醒喝水；螢幕關著時可以選「通知並震動」或「解鎖後再說」。自訂提醒要按「知道了」才收，暫停和安靜時段也照常提醒。
- **平時**：麵條隱藏（最省電），或貼在螢幕邊探頭——點一下出來打招呼，拖曳換邊、換高度。
- **不打擾**：通話中先不出來（伸展、喝水延到掛斷後，自訂提醒改發通知）、安靜時段、暫停 1 小時。
- 設定裡的數字都能改；系統開了「移除動畫」時，麵條直接出現、不走路。
- **總開關**：設定最上面的「開啟拉麵」。關掉就完全停止（不提醒、常駐通知和麵條都收起來、開機也不會自己啟動），今天的紀錄保留；打開就恢復。只想休息一下用「暫停 1 小時」。

## 省電的做法

- 沒有輪詢：服務只在螢幕開／關／解鎖、鬧鐘響、通話狀態改變時醒來；連續使用時間用這些事件算出來。
- 麵條只在提醒時才畫出來，收起來就移除視窗、放掉圖片；選「側邊探頭」才會常駐一個靜止的小圖。
- 不需要「使用情形存取」權限，不讀其他 App 的內容；沒有網路權限，資料只存在手機裡。

## 需要的權限

| 權限 | 用途 |
| --- | --- |
| 顯示在其他應用程式上層 | 麵條和泡泡畫在其他 App 上面（要到系統設定手動打開；沒開時改用通知提醒） |
| 通知 | 常駐通知（今天的次數、快速按鈕）、螢幕關著或通話中的提醒 |
| 精確鬧鐘（USE_EXACT_ALARM） | 自訂提醒和喝水準時（安裝時就允許） |
| 開機後執行 | 開機自動啟動（設定裡可以關） |
| 電池最佳化（選配） | 部分品牌要設成「不限制」，服務才不會被關掉 |

## 從原始碼編譯（Windows）

需要 JDK 17 以上（Android Studio 內附的 `jbr` 就可以）和 Android SDK（compileSdk 36），手機 Android 13 以上。

```bat
:: JAVA_HOME 指到 JDK（例如 C:\Program Files\Android\Android Studio\jbr），ANDROID_HOME 指到 Android SDK
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
```

- 專案放在 Dropbox 裡，編譯輸出改放 `%LOCALAPPDATA%\noodle-pet-android-build\`（見根目錄 `build.gradle.kts`），APK 在 `app\outputs\apk\debug\app-debug.apk`。
- 安裝：手機開「USB 偵錯」接上電腦，`adb install -r --user 0 app-debug.apk`；或把 APK 傳到手機上點開安裝。
- debug 版用專案裡的 `signing/debug.keystore` 簽章（跟著 Dropbox 同步、不進 git），兩台電腦編出來的 APK 可以直接互相覆蓋安裝。沒有這個檔時會退回各電腦自己的 `~/.android/debug.keystore`，那樣編出來的就不能覆蓋，要先移除舊版。
- 正式版要自己的簽章金鑰：在使用者的 `~/.gradle/gradle.properties`（不是專案資料夾）加上 `lamian.storeFile`、`lamian.storePassword`、`lamian.keyAlias`、`lamian.keyPassword`，再 `gradlew.bat assembleRelease`。金鑰不要放進版控。

## 發布新版

1. 升 `app/build.gradle.kts` 的 `versionCode`、`versionName`。
2. 在有正式版金鑰的電腦執行 `gradlew.bat assembleRelease`，把輸出的 `app-release.apk` 複製成 `dist\lamian-版本號.apk`。
3. 在 GitHub 的 Releases 建一筆新的 Release：tag 用 `v版本號`（例如 `v0.1.1`），附上 APK、檔名改成 `lamian.apk`，寫上更新內容和 APK 的 SHA-256（PowerShell：`Get-FileHash dist\lamian-版本號.apk`）。不要勾「Pre-release」，不然「最新版」的下載連結不會換到這一版。
4. 介紹頁和 README 的下載按鈕都連到 `releases/latest/download/lamian.apk`，會自動拿到新版。介紹頁（`site/`，由 C520 專案的 `deploy.ps1` 跟其他作品一起部署）只有內容要改時才需要重新部署。

正式版與 debug 版的簽章不同：手機上要換版本種類時，要先移除舊的。介紹頁的畫面（`site/shots/`）是實機截圖，背景用空白頁、裁掉狀態列，不放設定頁或任何個人資料。

## 專案結構

- `app/src/main/java/com/c520/lamian/`
  - `data/`：設定、動作、自訂提醒、今天的紀錄（SharedPreferences＋JSON，讀檔時校正到合法範圍）
  - `engine/`：連續使用計時、時段與提醒時間計算（純邏輯，有單元測試）、系統鬧鐘
  - `overlay/`：懸浮的麵條（精靈圖播放、走路／垂降、探頭、拖曳）與泡泡（Compose）
  - `service/`：常駐服務（螢幕事件、通話、排隊規則）、通知、廣播入口
  - `ui/`：App 畫面（今天／動作／提醒／設定），設計 token 在 `ui/Theme.kt`，對應 `DESIGN.md`
- `app/src/main/assets/`：精靈圖（5 個角色 × 14 個動作）與動作說明，來自 Windows 版拉麵
- `site/`：C520 網站上的介紹頁（https://c520.pages.dev/lamian-android/）
- `docs/mockup-v0.1.html`：動手前的模擬示意圖

## 授權與聲明

- 以 [MIT 授權](LICENSE)釋出。角色、動作示範與說明來自 [Noodle](https://github.com/pcybriwsky/noodle)（© 2026 Pete Cybriwsky，MIT），原始授權保留在 [`LICENSE-noodle.txt`](LICENSE-noodle.txt)；App 裡也看得到（設定 →「授權資訊」）。
- 伸展動作只是一般的活動建議，不能取代醫療建議；覺得痛或不舒服就停下來，有傷病請先問醫師或物理治療師。
