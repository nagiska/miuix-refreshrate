package com.refreshrate.control.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class OverclockRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("s", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_overclock", false)) return

        val authMode = prefs.getString("auth_mode", "") ?: ""
        val ocRes = prefs.getString("oc_target_res", "") ?: ""
        val ocHz = prefs.getInt("oc_target_hz", -1)
        if (authMode.isEmpty() || ocRes.isEmpty() || ocHz < 0) return

        val wh = ocRes.split("x")
        if (wh.size < 2) return
        try {
            val tw = wh[0].toInt()
            val th = wh[1].toInt()
            val si = Intent(context, OverclockService::class.java).apply {
                putExtra("auth_mode", authMode)
                putExtra("targetW", tw)
                putExtra("targetH", th)
                putExtra("targetHz", ocHz)
            }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(si)
            else context.startService(si)
        } catch (e: Exception) {
        }
    }
}
