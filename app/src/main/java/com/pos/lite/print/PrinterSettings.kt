package com.pos.lite.utils

import android.content.Context

object PrinterSettings {
    private const val PREFS_NAME = "pos_printer_settings"
    private const val KEY_DEFAULT_PRINT = "default_print"
    private const val KEY_DEFAULT_DRAWER = "default_drawer"

    // 默认不打印 (false)
    fun isDefaultPrintEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEFAULT_PRINT, false)
    }

    fun setDefaultPrintEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DEFAULT_PRINT, enabled).apply()
    }

    // 默认不弹开钱箱 (false)
    fun isDefaultDrawerEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEFAULT_DRAWER, false)
    }

    fun setDefaultDrawerEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DEFAULT_DRAWER, enabled).apply()
    }
}