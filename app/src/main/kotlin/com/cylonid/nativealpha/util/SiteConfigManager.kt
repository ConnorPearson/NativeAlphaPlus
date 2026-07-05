package com.cylonid.nativealpha.util

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

object SiteConfigManager {
    private var cachedSitesMap: Map<String, JSONObject>? = null

    fun getSiteConfig(context: Context, url: String?): JSONObject? {
        if (url == null) return null
        val map = getSitesMap(context)
        
        try {
            val host = Uri.parse(url).host?.lowercase() ?: return null
            Log.d("NativeAlpha", "getSiteConfig for host: $host")
            val site = map[host]
            if (site != null) return site

            for (key in map.keys) {
                if (host.endsWith(".$key") || key == host) {
                    return map[key]
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getSitesMap(context: Context): Map<String, JSONObject> {
        cachedSitesMap?.let { return it }

        val map = mutableMapOf<String, JSONObject>()
        try {
            val finalJson: JSONObject
            val internalFile = File(context.filesDir, "sites.json")
            
            val assetJson = JSONObject(
                context.assets.open("sites.json").bufferedReader().use { it.readText() }
            )

            if (internalFile.exists()) {
                val internalJson = JSONObject(
                    internalFile.readText(StandardCharsets.UTF_8)
                )
                
                // Merge logic (prioritize asset updates for existing keys, keep internal for custom keys)
                val it = assetJson.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    internalJson.put(key, assetJson.get(key))
                }
                finalJson = internalJson
            } else {
                finalJson = assetJson
            }

            val keys = finalJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = finalJson.getJSONObject(key)
            }
        } catch (e: Exception) {
            Log.e("SiteConfigManager", "Error loading sites config", e)
        }
        
        cachedSitesMap = map
        return map
    }
}
