plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.c520.lamian"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.c520.lamian"
        // 跟曆刻響一樣從 Android 13 起跳：通知權限、USE_EXACT_ALARM 都有，省掉舊版相容分支
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    // 正式版簽章：金鑰路徑與密碼放在使用者的 ~/.gradle/gradle.properties（lamian.*），不進版控。
    // 沒有設定的電腦照樣能編 debug 版。
    val releaseStore = providers.gradleProperty("lamian.storeFile").orNull
    signingConfigs {
        // debug 版兩台電腦共用同一把金鑰（signing/debug.keystore，跟著 Dropbox 同步、不進 git），
        // 在哪台編出來的都能直接覆蓋安裝。沒有這個檔時照舊用各電腦自己的 ~/.android/debug.keystore。
        val sharedDebug = rootProject.file("signing/debug.keystore")
        if (sharedDebug.exists()) {
            getByName("debug") {
                storeFile = sharedDebug
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("lamian.storePassword").get()
                keyAlias = providers.gradleProperty("lamian.keyAlias").get()
                keyPassword = providers.gradleProperty("lamian.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // 資安相關的 lint 一律當錯誤
        error += listOf(
            "ExportedReceiver", "ExportedService", "UnsafeImplicitIntentLaunch",
            "MutableImplicitPendingIntent", "UnspecifiedRegisterReceiverFlag",
        )
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
}
