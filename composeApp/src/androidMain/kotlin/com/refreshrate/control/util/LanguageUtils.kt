package com.refreshrate.control.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object LanguageUtils {
    const val LANG_ZH = "zh"
    const val LANG_ZH_TW = "zh-rTW"
    const val LANG_EN = "en"
    const val LANG_JA = "ja"
    const val LANG_KO = "ko"
    const val LANG_RU = "ru"
    const val LANG_UG = "ug"
    const val LANG_SYSTEM = "system"

    private const val PREFS_NAME = "s"
    private const val KEY_LANG = "language"

    fun applyLanguage(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lang = prefs.getString(KEY_LANG, LANG_ZH) ?: LANG_ZH
        setLocale(context, lang)
    }

    fun setLanguageAndRecreate(activity: Activity, lang: String) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, lang).apply()
        setLocale(activity, lang)
        activity.recreate()
    }

    fun getCurrentLang(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, LANG_ZH) ?: LANG_ZH
    }

    private fun resolve(lang: String): Locale {
        if (LANG_SYSTEM == lang) {
            return Resources.getSystem().configuration.locales[0]
        }
        if (LANG_ZH_TW == lang) {
            return Locale(LANG_ZH, "TW")
        }
        return Locale(lang)
    }

    fun wrap(context: Context): Context {
        val lang = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANG, LANG_ZH) ?: LANG_ZH
        val locale = resolve(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun setLocale(context: Context, lang: String) {
        val locale = resolve(lang)
        Locale.setDefault(locale)
        val res = context.resources
        val config = res.configuration
        config.setLocale(locale)
        res.updateConfiguration(config, res.displayMetrics)
    }
}
