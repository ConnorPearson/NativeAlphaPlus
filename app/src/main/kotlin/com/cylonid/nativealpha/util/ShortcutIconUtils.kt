package com.cylonid.nativealpha.util

import android.content.Context
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.model.WebApp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


object ShortcutIconUtils {
    @JvmStatic
    fun deleteShortcuts(removableWebAppIds: List<Int>, context: Context) {
        val manager = context.getSystemService(
            ShortcutManager::class.java
        )
        for (info in manager.pinnedShortcuts) {
            val id = info.intent!!
                .getIntExtra(Const.INTENT_WEBAPPID, -1)
            if (removableWebAppIds.contains(id)) {
                manager.disableShortcuts(
                    listOf(info.id),
                    context.getString(R.string.webapp_already_deleted)
                )
            }
        }
    }

    @JvmStatic
    fun getIcon(context: Context, webApp: WebApp): Bitmap? {
        val file = File(context.filesDir, "webapp_icon_${webApp.ID}.png")
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            null
        }
    }

    @JvmStatic
    fun getIcon(context: Context, id: Int, url: String): Bitmap? {
        val file = File(context.filesDir, "webapp_icon_$id.png")
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            null
        }
    }

    @JvmStatic
    fun saveIcon(context: Context, webApp: WebApp, bitmap: Bitmap) {
        val file = File(context.filesDir, "webapp_icon_${webApp.ID}.png")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun saveIcon(context: Context, id: Int, bitmap: Bitmap) {
        val file = File(context.filesDir, "webapp_icon_$id.png")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun deleteIcon(context: Context, id: Int) {
        val file = File(context.filesDir, "webapp_icon_$id.png")
        if (file.exists()) {
            file.delete()
        }
    }

    @JvmStatic
    fun getWidthFromIcon(sizeString: String): Int {
        var xIndex = sizeString.indexOf("x")
        if (xIndex == -1) xIndex = sizeString.indexOf("×")
        if (xIndex == -1) xIndex = sizeString.indexOf("*")

        if (xIndex == -1) return 1
        val width = sizeString.substring(0, xIndex)

        return width.toInt()
    }
}