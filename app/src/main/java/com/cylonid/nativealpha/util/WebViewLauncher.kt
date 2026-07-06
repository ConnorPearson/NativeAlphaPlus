package com.cylonid.nativealpha.util

import android.app.Activity
import android.content.Context
import com.cylonid.nativealpha.model.WebApp
import androidx.appcompat.app.AppCompatActivity
import com.cylonid.nativealpha.R
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.processphoenix.ProcessPhoenix
import android.content.Intent
import android.net.Uri
import java.lang.NullPointerException

object WebViewLauncher {
    @JvmStatic
    fun startWebView(webapp: WebApp, c: Context) {
        try {
            c.startActivity(createWebViewIntent(webapp, c))
        } catch (e: NullPointerException) {
            NotificationUtils.showInfoSnackbar(
                c as AppCompatActivity,
                c.getString(R.string.webview_activity_launch_failed),
                Snackbar.LENGTH_LONG
            )
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun startWebViewInNewProcess(webapp: WebApp, a: Activity) {
        try {
            ProcessPhoenix.triggerRebirth(a, createWebViewIntent(webapp, a))
        } catch (e: NullPointerException) {
            NotificationUtils.showInfoSnackbar(
                a,
                a.getString(R.string.webview_activity_launch_failed),
                Snackbar.LENGTH_LONG
            )
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun createWebViewIntent(webapp: WebApp, c: Context?): Intent? {
        val packageName = "com.cylonid.nativealpha"
        var webview_class: Class<*>? = null
        try {
            webview_class = if (webapp.containerId != Const.NO_CONTAINER) {
                Class.forName(packageName + ".__WebViewActivity_" + webapp.containerId)
            } else {
                Class.forName("$packageName.WebViewActivity")
            }
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
        if (webview_class == null) {
            return null
        }
        val intent = Intent(c, webview_class)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        
        if (webapp.isBiometricProtection) intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        intent.putExtra(Const.INTENT_WEBAPPID, webapp.ID)
        intent.data = Uri.parse(webapp.baseUrl + webapp.ID)
        intent.action = Intent.ACTION_VIEW
        return intent
    }
}