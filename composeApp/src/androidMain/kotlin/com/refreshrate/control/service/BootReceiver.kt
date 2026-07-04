package com.refreshrate.control.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        OverclockRestartReceiver().onReceive(context, intent)
    }
}
