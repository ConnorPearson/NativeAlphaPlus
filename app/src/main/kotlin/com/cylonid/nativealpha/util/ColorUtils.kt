package com.cylonid.nativealpha.util

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes

object ColorUtils {

    @JvmStatic
    @ColorRes
    fun getColorResFromThemeAttr(context: Context, @AttrRes resId: Int, @ColorRes fallback: Int): Int {
        val typedValue = TypedValue()
        val theme = context.theme

        val success = theme.resolveAttribute(
            resId,
            typedValue,
            true
        )
        if (success && typedValue.resourceId != 0) {
            return typedValue.resourceId
        }
        return fallback
    }
}