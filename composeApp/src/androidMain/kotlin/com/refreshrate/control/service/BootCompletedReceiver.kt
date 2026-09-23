package com.refreshrate.control.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** 开机后恢复全局刷新率锁定(若开启时处于锁定状态)。 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED != intent.action) return
        val prefs = context.getSharedPreferences("s", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("global_overclock", false)) return
        if (prefs.getString("global_oc_res", "").isNullOrEmpty()) return
        val i = Intent(context, GlobalOverclockService::class.java).setAction(GlobalOverclockService.ACTION_START)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        } catch (_: Exception) { }
    }
}
