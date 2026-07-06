package com.cylonid.nativealpha

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.graphics.Color
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TimePicker
import android.widget.Toast
import com.cylonid.nativealpha.activities.ToolbarBaseActivity
import com.cylonid.nativealpha.databinding.WebappSettingsBinding
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.DateUtils.convertStringToCalendar
import com.cylonid.nativealpha.util.DateUtils.getHourMinFormat
import com.cylonid.nativealpha.util.ProcessUtils.closeAllWebAppsAndProcesses
import com.cylonid.nativealpha.util.Utility
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Calendar

class WebAppSettingsActivity : ToolbarBaseActivity<WebappSettingsBinding>() {
    var webappID: Int = -1
    var webapp: WebApp? = null
    private var isGlobalWebApp: Boolean = false

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setToolbarTitle(getString(R.string.web_app_settings))

        webappID = intent.getIntExtra(Const.INTENT_WEBAPPID, -1)
        Utility.Assert(webappID != -1, "WebApp ID could not be retrieved.")
        isGlobalWebApp = webappID == DataManager.getInstance().settings.globalWebApp.ID

        if (isGlobalWebApp) {
            webapp = DataManager.getInstance().settings.globalWebApp
            prepareGlobalWebAppScreen()
        } else webapp = DataManager.getInstance().getWebAppIgnoringGlobalOverride(webappID, true)

        if (webapp == null) {
            finish()
            return
        }
        val modifiedWebapp = WebApp(webapp!!)
        binding.webapp = modifiedWebapp
        binding.activity = this@WebAppSettingsActivity

        setupSaveAndCancel(modifiedWebapp)
        setupDarkModeElements()
        setupPlusSettings()
        setupDesktopUserAgentHint()
        setupShortcutButton()
        setupSwitchListeners(webapp!!)
        setupSiteRulesLogic(modifiedWebapp)

    }

    override fun inflateBinding(layoutInflater: LayoutInflater): WebappSettingsBinding {
        return WebappSettingsBinding.inflate(layoutInflater)
    }

    private fun setupSwitchListeners(webapp: WebApp) {
        webapp.onSwitchExpertSettingsChanged(
            binding.switchExpertSettings,
            webapp.isShowExpertSettings
        )
        webapp.onSwitchOverrideGlobalSettingsChanged(
            binding.switchOverrideGlobal,
            webapp.isOverrideGlobalSettings
        )
    }
    
    private fun setupColorPreview(editText: EditText, preview: View) {
        fun updatePreview(s: String) {
            try {
                if (s.isNotEmpty()) {
                    preview.setBackgroundColor(Color.parseColor(s))
                } else {
                    preview.setBackgroundColor(Color.TRANSPARENT)
                }
            } catch (e: Exception) {
                preview.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePreview(s.toString().trim())
            }
        })
        updatePreview(editText.text.toString().trim())

        preview.setOnClickListener {
            val colors = arrayOf("#000000", "#FFFFFF", "#990033", "#1E1E1E", "#F5F5F5", "#2F3E59", "#232F3E", "#00288D", "#1F1F1F")
            val colorNames = arrayOf("AMOLED Black", "Pure White", "Signature Accent", "Dark Grey", "Light Grey", "Blitzortung Blue", "Amazon Dark", "PayPal Blue", "AccuWeather Dark")
            
            MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog)
                .setTitle("Pick a color")
                .setItems(colorNames) { _, which ->
                    editText.setText(colors[which])
                }
                .setNeutralButton("Custom") { _, _ ->
                    // Just let them type in the EditText
                }
                .show()
        }
    }

    private fun setupSiteRulesLogic(modifiedWebapp: WebApp) {
        val host = try {
            java.net.URL(modifiedWebapp.baseUrl).host.lowercase()
        } catch (e: Exception) {
            ""
        }
        if (host.isEmpty()) {
            binding.sectionSiteRules.visibility = View.GONE
            return
        }

        setupColorPreview(binding.editStatusBarColor, binding.viewStatusBarColorPreview)
        setupColorPreview(binding.editBottomBarColor, binding.viewBottomBarColorPreview)
        setupColorPreview(binding.editLoadingBarColor, binding.viewLoadingBarColorPreview)

        val internalFile = File(filesDir, "sites.json")
        if (!internalFile.exists()) {
            try {
                assets.open("sites.json").use { input ->
                    internalFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun loadRules() {
            val json = try {
                JSONObject(internalFile.readText(StandardCharsets.UTF_8))
            } catch (e: Exception) {
                JSONObject()
            }

            var siteConfig = json.optJSONObject(host)
            if (siteConfig == null) {
                // Try fuzzy match
                for (key in json.keys()) {
                    if (host.endsWith(".$key") || key == host) {
                        siteConfig = json.getJSONObject(key)
                        break
                    }
                }
            }

            siteConfig?.let {
                binding.txtWebAppName.setText(it.optString("title", binding.txtWebAppName.text.toString()))
                binding.editStatusBarColor.setText(it.optString("statusBarColor", ""))
                binding.editBottomBarColor.setText(it.optString("bottomBarColor", ""))
                binding.editLoadingBarColor.setText(it.optString("loadingBarColor", ""))
                
                val removeArray = it.optJSONArray("remove")
                val removeText = StringBuilder()
                if (removeArray != null) {
                    for (i in 0 until removeArray.length()) {
                        removeText.append(removeArray.getString(i)).append("\n")
                    }
                }
                binding.editDomRemoval.setText(removeText.toString().trim())

                val cssArray = it.optJSONArray("injectCss")
                val cssText = StringBuilder()
                if (cssArray != null) {
                    for (i in 0 until cssArray.length()) {
                        cssText.append(cssArray.getString(i)).append("\n")
                    }
                }
                binding.editInjectCss.setText(cssText.toString().trim())
            }
        }

        loadRules()

        binding.btnResetSiteRules.setOnClickListener {
            try {
                val originalJson = JSONObject(assets.open("sites.json").bufferedReader().use { it.readText() })
                var originalConfig = originalJson.optJSONObject(host)
                if (originalConfig == null) {
                    for (key in originalJson.keys()) {
                        if (host.endsWith(".$key") || key == host) {
                            originalConfig = originalJson.getJSONObject(key)
                            break
                        }
                    }
                }

                if (originalConfig != null) {
                    binding.txtWebAppName.setText(originalConfig.optString("title", host))
                    binding.editStatusBarColor.setText(originalConfig.optString("statusBarColor", ""))
                    binding.editBottomBarColor.setText(originalConfig.optString("bottomBarColor", ""))
                    binding.editLoadingBarColor.setText(originalConfig.optString("loadingBarColor", ""))
                    
                    val removeArray = originalConfig.optJSONArray("remove")
                    binding.editDomRemoval.setText(JSONArrayToString(removeArray))

                    val cssArray = originalConfig.optJSONArray("injectCss")
                    binding.editInjectCss.setText(JSONArrayToString(cssArray))
                } else {
                    binding.editStatusBarColor.setText("")
                    binding.editBottomBarColor.setText("")
                    binding.editLoadingBarColor.setText("")
                    binding.editDomRemoval.setText("")
                    binding.editInjectCss.setText("")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun JSONArrayToString(array: JSONArray?): String {
        if (array == null) return ""
        val sb = StringBuilder()
        for (i in 0 until array.length()) {
            sb.append(array.getString(i)).append("\n")
        }
        return sb.toString().trim()
    }

    private fun saveSiteRules(host: String) {
        if (host.isEmpty()) return
        val internalFile = File(filesDir, "sites.json")
        try {
            val json = if (internalFile.exists()) {
                JSONObject(internalFile.readText(StandardCharsets.UTF_8))
            } else {
                JSONObject()
            }

            val siteConfig = JSONObject()
            val title = binding.txtWebAppName.text.toString().trim()
            if (title.isNotEmpty()) siteConfig.put("title", title)

            val statusBar = binding.editStatusBarColor.text.toString().trim()
            if (statusBar.isNotEmpty()) siteConfig.put("statusBarColor", statusBar)
            
            val bottomBar = binding.editBottomBarColor.text.toString().trim()
            if (bottomBar.isNotEmpty()) siteConfig.put("bottomBarColor", bottomBar)

            val loadingBar = binding.editLoadingBarColor.text.toString().trim()
            if (loadingBar.isNotEmpty()) siteConfig.put("loadingBarColor", loadingBar)

            val removeText = binding.editDomRemoval.text.toString().trim()
            if (removeText.isNotEmpty()) {
                val removeArray = JSONArray()
                removeText.split("\n").forEach { if (it.trim().isNotEmpty()) removeArray.put(it.trim()) }
                siteConfig.put("remove", removeArray)
            }

            val cssText = binding.editInjectCss.text.toString().trim()
            if (cssText.isNotEmpty()) {
                val cssArray = JSONArray()
                cssText.split("\n").forEach { if (it.trim().isNotEmpty()) cssArray.put(it.trim()) }
                siteConfig.put("injectCss", cssArray)
            }

            json.put(host, siteConfig)
            internalFile.writeText(json.toString(2), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupSaveAndCancel(modifiedWebapp: WebApp) {
        binding.btnSave.setOnClickListener {
            val host = try {
                java.net.URL(modifiedWebapp.baseUrl).host.lowercase()
            } catch (e: Exception) {
                ""
            }
            saveSiteRules(host)

            val activityManager =
                getSystemService(ACTIVITY_SERVICE) as ActivityManager
            // Global web app => close all webview activities, save to global settings
            if (isGlobalWebApp) {
                closeAllWebAppsAndProcesses(
                    activityManager
                )
                DataManager.getInstance().settings.globalWebApp = modifiedWebapp
                DataManager.getInstance().saveGlobalSettings()
            } else {
                for (task in activityManager.appTasks) {
                    val id = task.taskInfo.baseIntent.getIntExtra(
                        Const.INTENT_WEBAPPID,
                        -1
                    )
                    if (id == webappID) task.finishAndRemoveTask()
                }
                for (processInfo in activityManager.runningAppProcesses) {
                    if (processInfo.processName.contains("web_sandbox_" + modifiedWebapp.containerId)) {
                        Process.killProcess(processInfo.pid)
                    }
                }
                DataManager.getInstance().replaceWebApp(modifiedWebapp)
            }

            val i = Intent(this@WebAppSettingsActivity, MainActivity::class.java)
            i.putExtra(Const.INTENT_WEBAPP_CHANGED, true)
            finish()
            startActivity(i)
        }

        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun setupDarkModeElements() {
        val txtBeginDarkMode = binding.textDarkModeBegin
        val txtEndDarkMode = binding.textDarkModeEnd

        txtBeginDarkMode.setOnClickListener {
            showTimePicker(
                txtBeginDarkMode
            )
        }
        txtEndDarkMode.setOnClickListener {
            showTimePicker(
                txtEndDarkMode
            )
        }

    }

    private fun setupDesktopUserAgentHint() {
        val txt = binding.txthintUserAgent
        txt.text = Html.fromHtml(getString(R.string.hint_user_agent), Html.FROM_HTML_MODE_LEGACY)
        txt.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupShortcutButton() {
        binding.btnRecreateShortcut.setOnClickListener {
            val frag = ShortcutDialogFragment.newInstance(webapp)
            frag.show(supportFragmentManager, "SCFetcher-" + webapp!!.ID)
        }
    }

    private fun setupPlusSettings() {
        // Show all settings regardless of flavor
    }

    private fun showTimePicker(txtField: EditText) {
        val c = convertStringToCalendar(txtField.text.toString())
        val timePickerDialog = TimePickerDialog(
            this@WebAppSettingsActivity, R.style.AppTheme,
            { timePicker: TimePicker?, selectedHour: Int, selectedMinute: Int ->
                val datetime = Calendar.getInstance()
                datetime[Calendar.HOUR_OF_DAY] = selectedHour
                datetime[Calendar.MINUTE] = selectedMinute
                txtField.setText(getHourMinFormat().format(datetime.time))
            }, c!![Calendar.HOUR_OF_DAY], c[Calendar.MINUTE], true
        )
        timePickerDialog.show()
    }

    private fun prepareGlobalWebAppScreen() {
        binding.btnRecreateShortcut.visibility = View.GONE
        binding.layoutWebAppName.visibility = View.GONE
        binding.switchOverrideGlobal.visibility = View.GONE
        binding.sectionSSL.visibility = View.GONE
        binding.sectionSandbox.visibility = View.GONE
        binding.labelTitle.visibility = View.GONE
        binding.layoutBaseUrl.visibility = View.GONE

        binding.globalSettingsInfoText.visibility = View.VISIBLE

        setToolbarTitle(getString(R.string.global_web_app_settings))
    }
}


