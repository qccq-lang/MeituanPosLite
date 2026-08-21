package com.pos.lite

import android.app.Application
import com.pos.lite.data.AppDatabase
import com.pos.lite.data.Staff
import com.pos.lite.utils.LicenseGuard

class App : Application() {
    companion object {
        lateinit var instance: App
            private set
        var currentStaff: Staff? = null
    }

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 埋点 1：应用进程启动强校验
        if (LicenseGuard.isExpired()) {
            LicenseGuard.verifyOrHalt()
        }
    }
}