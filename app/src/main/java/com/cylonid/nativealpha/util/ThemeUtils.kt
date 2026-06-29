package com.cylonid.nativealpha.util

import androidx.appcompat.app.AppCompatDelegate
import com.cylonid.nativealpha.model.DataManager

object ThemeUtils {
    @JvmStatic
    fun applyTheme() {
        val themeId = DataManager.getInstance().settings.themeId
        when (themeId) {
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
