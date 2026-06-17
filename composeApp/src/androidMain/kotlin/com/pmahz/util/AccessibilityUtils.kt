package com.pmahz.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.pmahz.service.KeepAliveAccessibilityService

object AccessibilityUtils {
    fun isKeepAliveServiceEnabled(context: Context): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                "enabled_accessibility_services"
            )
            if (TextUtils.isEmpty(enabledServices)) return false

            val target = ComponentName(context, KeepAliveAccessibilityService::class.java)
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                val item = splitter.next()
                if (!TextUtils.isEmpty(item)) {
                    val enabled = ComponentName.unflattenFromString(item)
                    if (target.equals(enabled)) return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}
