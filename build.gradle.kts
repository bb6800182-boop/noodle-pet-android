plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// 專案放在 Windows 的 Dropbox 裡時，Dropbox 會把新寫入的檔案換成雲端預留位置（reparse point），
// Gradle 就讀不到自己的編譯輸出。這時把編譯輸出放到 Dropbox 外面（%LOCALAPPDATA%）。
val localAppData: String? = System.getenv("LOCALAPPDATA")
if (localAppData != null && "Dropbox" in rootDir.path) {
    allprojects { layout.buildDirectory.set(File(localAppData, "noodle-pet-android-build/$name")) }
}
