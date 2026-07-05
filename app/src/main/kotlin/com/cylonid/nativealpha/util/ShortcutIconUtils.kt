package com.cylonid.nativealpha.util

import android.content.Context
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.model.WebApp
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream


object ShortcutIconUtils {
    private const val ICON_DIRECTORY = "webapp_icons"

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
    fun saveIcon(context: Context, webAppId: Int, bitmap: Bitmap) {
        val directory = File(context.filesDir, ICON_DIRECTORY)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        // Resize for consistent list display
        val maxSize = 192
        val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val width = if (ratio > 1) maxSize else (maxSize * ratio).toInt()
            val height = if (ratio > 1) (maxSize / ratio).toInt() else maxSize
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }

        val file = File(directory, "icon_$webAppId.png")
        try {
            FileOutputStream(file).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun getIcon(context: Context, webApp: WebApp): Bitmap? {
        return getIcon(context, webApp.ID, webApp.baseUrl)
    }

    @JvmStatic
    fun getIcon(context: Context, id: Int, baseUrl: String): Bitmap? {
        // 1. Check for a cached icon in internal storage (captured or custom)
        val file = File(context.filesDir, "$ICON_DIRECTORY/icon_$id.png")
        if (file.exists()) {
            return BitmapFactory.decodeFile(file.absolutePath)
        }

        // 2. Check sites.json for a bundled icon mapping
        val siteConfig = SiteConfigManager.getSiteConfig(context, baseUrl)
        val bundledIconName = siteConfig?.optString("icon")
        if (bundledIconName != null && bundledIconName.isNotEmpty()) {
            try {
                val inputStream: InputStream = context.assets.open("site_icons/$bundledIconName")
                return BitmapFactory.decodeStream(inputStream)
            } catch (e: Exception) {
                // Asset not found or error loading
            }
        }

        return null
    }

    @JvmStatic
    fun deleteIcon(context: Context, webAppId: Int) {
        val file = File(context.filesDir, "$ICON_DIRECTORY/icon_$webAppId.png")
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