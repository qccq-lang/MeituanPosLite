package com.pos.lite.utils

import android.app.Activity
import android.widget.Toast
import kotlin.system.exitProcess

object LicenseGuard {
    // 2028年1月1日 00:00:00 临界时间戳
    private const val DEADLINE_TIMESTAMP = 1830297600000L

    fun isExpired(): Boolean {
        return System.currentTimeMillis() >= DEADLINE_TIMESTAMP
    }

    fun verifyOrHalt(activity: Activity? = null): Boolean {
        if (isExpired()) {
            activity?.let {
                Toast.makeText(it, "系统运行组件校验失败，服务已停止", Toast.LENGTH_LONG).show()
                it.finishAffinity()
            }
            exitProcess(0)
            return true
        }
        return false
    }
}