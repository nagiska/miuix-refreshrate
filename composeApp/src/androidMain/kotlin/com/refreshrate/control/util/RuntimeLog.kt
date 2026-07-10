package com.refreshrate.control.util

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RuntimeLog {
    private const val PREFS_NAME = "runtime_log"
    private const val KEY_LINES = "lines"
    private const val MAX_LINES = 10000
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    fun append(context: Context, tag: String, message: String) {
        init(context)
        Log.i(tag, message)
        val line = "${timeFormat.format(Date())} [$tag] $message"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_LINES, "") ?: ""
        val lines = if (current.isBlank()) emptyList() else current.lines()
        val next = (lines + line).takeLast(MAX_LINES).joinToString("\n")
        prefs.edit().putString(KEY_LINES, next).apply()
    }

    fun appendGlobal(tag: String, message: String) {
        appContext?.let { append(it, tag, message) } ?: Log.i(tag, message)
    }

    fun read(context: Context): List<String> {
        val text = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LINES, "") ?: ""
        return if (text.isBlank()) emptyList() else text.lines().asReversed()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LINES)
            .apply()
    }
}
