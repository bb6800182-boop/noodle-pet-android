package com.c520.lamian

import android.app.Application
import com.c520.lamian.data.Store

class LamianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
