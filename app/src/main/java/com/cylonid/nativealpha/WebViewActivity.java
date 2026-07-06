package com.cylonid.nativealpha;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ShareCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.cylonid.nativealpha.databinding.DialogHttpAuthBinding;
import com.cylonid.nativealpha.helper.AdblockLifecycleHelper;
import com.cylonid.nativealpha.helper.AdblockProviderApiHelper;
import com.cylonid.nativealpha.helper.BiometricPromptHelper;
import com.cylonid.nativealpha.helper.IconPopupMenuHelper;
import com.cylonid.nativealpha.model.AdblockConfig;
import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.SandboxManager;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.util.DateUtils;
import com.cylonid.nativealpha.util.EntryPointUtils;
import com.cylonid.nativealpha.util.LocaleUtils;
import com.cylonid.nativealpha.util.NotificationUtils;
import com.cylonid.nativealpha.util.Utility;
import com.cylonid.nativealpha.util.WebViewLauncher;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import io.github.edsuns.adfilter.AdFilter;
import io.github.edsuns.adfilter.Filter;
import pub.devrel.easypermissions.EasyPermissions;

import static com.cylonid.nativealpha.util.Const.CODE_OPEN_FILE;

import org.json.JSONArray;
import org.json.JSONObject;

public class WebViewActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

    //Constants for touchlistener
    private static final int NONE = 0;
    private static final int SWIPE = 1;
    private static final int TRESHOLD = 100;
    int webappID = -1;
    private WebView wv;
    private ProgressBar progressBar;
    private boolean currently_reloading = true;
    private GeolocationPermissions.Callback mGeoPermissionRequestCallback = null;
    private String mGeoPermissionRequestOrigin = null;
    private DownloadManager.Request dl_request = null;
    private Map<String, String> CUSTOM_HEADERS;
    protected ValueCallback<Uri[]> filePathCallback;

    private boolean quitOnNextBackpress = false;
    private Handler reload_handler = null;
    private WebApp webapp = null;
    private String urlOnFirstPageload = "";
    private boolean fallbackToDefaultLongClickBehaviour = false;
    private PopupMenu mPopupMenu = null;

    private AdFilter adFilter;

    private AdblockProviderApiHelper adblockProviderApiHelper;
    private AdblockLifecycleHelper adblockLifecycleHelper;

    private Map<String, JSONObject> cachedSitesMap = new HashMap<>();
    private final List<String> sessionAddedRules = new ArrayList<>();

    private void loadSitesConfig() {
        try {
            File internalFile = new File(getFilesDir(), "sites.json");
            InputStream is;
            
            if (internalFile.exists()) {
                is = new FileInputStream(internalFile);
                Log.d("NativeAlpha", "Loading sites config from internal storage");
            } else {
                is = getAssets().open("sites.json");
                Log.d("NativeAlpha", "Loading sites config from assets");
            }

            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            JSONObject json = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
            json = normalizeJsonKeys(json); // Migrate legacy keys to hostnames
            cachedSitesMap.clear();

            for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                String key = it.next();
                cachedSitesMap.put(key, json.getJSONObject(key));
            }

        } catch (Exception e) {
            Log.e("NativeAlpha", "Error loading sites config", e);
        }
    }

    private JSONObject normalizeJsonKeys(JSONObject json) {
        JSONObject normalized = new JSONObject();
        try {
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String normalizedKey = Utility.getCanonicalHost(key);
                if (normalizedKey.isEmpty()) continue;
                
                if (normalized.has(normalizedKey)) {
                    JSONObject existing = normalized.getJSONObject(normalizedKey);
                    JSONObject current = json.getJSONObject(key);
                    Iterator<String> currentKeys = current.keys();
                    while (currentKeys.hasNext()) {
                        String subKey = currentKeys.next();
                        if (subKey.equals("remove") || subKey.equals("injectCss")) {
                            JSONArray existingArr = existing.optJSONArray(subKey);
                            JSONArray currentArr = current.optJSONArray(subKey);
                            if (existingArr != null && currentArr != null) {
                                for (int i = 0; i < currentArr.length(); i++) {
                                    String val = currentArr.getString(i);
                                    boolean exists = false;
                                    for (int k = 0; k < existingArr.length(); k++) {
                                        if (existingArr.getString(k).equals(val)) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) existingArr.put(val);
                                }
                                continue;
                            }
                        }
                        existing.put(subKey, current.get(subKey));
                    }
                } else {
                    normalized.put(normalizedKey, json.getJSONObject(key));
                }
            }
        } catch (Exception e) {
            Log.e("NativeAlpha", "Error normalizing JSON", e);
        }
        return normalized;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        adblockLifecycleHelper = new AdblockLifecycleHelper(this);
        adblockLifecycleHelper.trySyncOperation(() -> adFilter = AdFilter.Companion.get(getApplicationContext()));

        adblockProviderApiHelper = new AdblockProviderApiHelper(adFilter);
        webappID = getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1);
        EntryPointUtils.entryPointReached(this);
        webapp = DataManager.getInstance().getWebApp(webappID);
        if (webapp == null) {
            // Toast is shown in getWebApp method
            finish();
        } else {
            if(webapp.isBiometricProtection()) {
                new BiometricPromptHelper(WebViewActivity.this).showPrompt(() -> setupWebView(), () -> finish(), getString(R.string.bioprompt_restricted_webapp));
            } else {
                setupWebView();
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupWebView() {

        String processName = Application.getProcessName();
        String packageName = this.getPackageName();

        boolean hasSandboxing = SandboxManager.getInstance() != null;
        // Sandboxed Web App is openend in main process using an old shortcut
        if (packageName.equals(processName) && webapp.isUseContainer() && hasSandboxing) {
            WebViewLauncher.startWebViewInNewProcess(webapp, this);
        }

        if (!packageName.equals(processName) && hasSandboxing) {
            if (SandboxManager.getInstance().isSandboxUsedByAnotherApp(webapp)) {
                SandboxManager.getInstance().unregisterWebAppFromSandbox(webapp.getContainerId());
                WebViewLauncher.startWebViewInNewProcess(webapp, this);
            }
            try {
                SandboxManager.getInstance().registerWebAppToSandbox(webapp);
                WebView.setDataDirectorySuffix(webapp.getContainerId() + webapp.getAlphanumericBaseUrl() + "_" + webapp.getID());
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }

        loadSitesConfig();

        setContentView(R.layout.full_webview);

        if(webapp.isKeepAwake()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        String url = webapp.getBaseUrl();

        wv = findViewById(R.id.webview);
        wv.setBackgroundColor(Color.BLACK);
        wv.addJavascriptInterface(new WebAppInterface(), "NativeAlpha");
        progressBar = findViewById(R.id.progressBar);

        List<AdblockConfig> adblockConfigs = DataManager.getInstance().getSettings().getGlobalWebApp().getAdBlockSettings();
        if (webapp.isUseAdblock() && !adblockConfigs.isEmpty()) {
            wv.setVisibility(View.GONE);
            wv = findViewById(R.id.adblockwebview);
            wv.setVisibility(View.VISIBLE);
            wv.setBackgroundColor(Color.BLACK);
            wv.addJavascriptInterface(new WebAppInterface(), "NativeAlpha");

            adFilter.setupWebView(wv);
            adblockLifecycleHelper.beforeAdblockOperation(() -> adblockProviderApiHelper.synchronizeAdblockProviderWithSettings(adblockConfigs));

            adFilter.getViewModel().getOnDirty().observe(this, none -> wv.clearCache(false)
            );

            adFilter.getViewModel().getEnabledFilterCount().observe(this, count -> {
                if (count == adblockConfigs.size()) {
                    adblockLifecycleHelper.afterAdblockOperation();
                }
            });
        }

        String fieldName = Stream.of(WebViewActivity.class.getDeclaredFields()).filter(f -> f.getType() == WebView.class).findFirst().orElseThrow(null).getName();
        String uaString = wv.getSettings().getUserAgentString().replace("; " + fieldName, "");
        wv.getSettings().setUserAgentString(uaString);
        if (webapp.isUseCustomUserAgent()) {
            if(webapp.getUserAgent() != null && !webapp.getUserAgent().equals("")) {
                wv.getSettings().setUserAgentString(webapp.getUserAgent().replace("\0", "").replace("\n", "").replace("\r", ""));
            }
        }

        if (webapp.isShowFullscreen()) {
            this.hideSystemBars();
        } else if(DataManager.getInstance().getSettings().getAlwaysShowSoftwareButtons()) {
            this.showSystemBars();
        }
        wv.setWebViewClient(new CustomBrowser());
        wv.getSettings().setSafeBrowsingEnabled(false);

        // Battery Optimization: Use cache mode when in power save mode
        if (webapp.isBatterySaverCaching()) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(android.content.Context.POWER_SERVICE);
            if (pm != null && pm.isPowerSaveMode()) {
                wv.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK);
            } else {
                wv.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
            }
        } else {
            wv.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
        }

        wv.getSettings().setDomStorageEnabled(true);
        wv.getSettings().setDatabaseEnabled(true);
        wv.getSettings().setAllowFileAccess(true);
        wv.getSettings().setBlockNetworkLoads(false);
//        wv.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        this.setDarkModeIfNeeded();

        wv.getSettings().setJavaScriptEnabled(webapp.isAllowJs());

        CookieManager.getInstance().setAcceptCookie(webapp.isAllowCookies());
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, webapp.isAllowThirdPartyCookies());

        if (webapp.isBlockImages())
            wv.getSettings().setBlockNetworkImage(true);

        if (webapp.isRequestDesktop()) {
            wv.getSettings().setUserAgentString(Const.DESKTOP_USER_AGENT);
            wv.getSettings().setUseWideViewPort(true);
            wv.getSettings().setLoadWithOverviewMode(true);

            wv.getSettings().setSupportZoom(true);
            wv.getSettings().setBuiltInZoomControls(true);
            wv.getSettings().setDisplayZoomControls(false);

            wv.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
            wv.setScrollbarFadingEnabled(false);

        }
        if(webapp.isEnableZooming()) {
            wv.getSettings().setSupportZoom(true);
            wv.getSettings().setBuiltInZoomControls(true);
        }

        CUSTOM_HEADERS = initCustomHeaders(webapp.isSendSavedataRequest());
        applySiteSystemBars(url);
        loadURL(wv, url);
        wv.setWebChromeClient(new CustomWebChromeClient());
        wv.setOnLongClickListener(view -> {
            if(webapp.getAlwaysUseFallbackContextMenu()) return false;
            if(fallbackToDefaultLongClickBehaviour) {
                fallbackToDefaultLongClickBehaviour = false;
                return false;
            }
            showWebViewPopupMenu();
            return true;
        });


        wv.setDownloadListener((dl_url, userAgent, contentDisposition, mimeType, contentLength) -> {

            if (mimeType.equals("application/pdf")) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(dl_url));
                startActivity(i);
            } else {
                if(dl_url != null && !dl_url.equals("")) {
                    if(dl_url.startsWith("blob:")) {
                        dl_url = dl_url.replace("blob:", "");
                        try {
                            dl_url = URLDecoder.decode(dl_url, "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                    DownloadManager.Request request = null;
                    try {
                        request = new DownloadManager.Request(
                                Uri.parse(dl_url));
                    }
                    catch(Exception e) {
                        NotificationUtils.showInfoSnackbar(this, getString(R.string.file_download), Snackbar.LENGTH_SHORT);
                    }
                  String file_name = Utility.getFileNameFromDownload(dl_url, contentDisposition, mimeType);

                  request.setMimeType(mimeType);
                  request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(dl_url));
                  request.addRequestHeader("User-Agent", userAgent);
                  request.setTitle(file_name);
                  request.allowScanningByMediaScanner();
                  request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                  request.setDestinationInExternalPublicDir(
                          Environment.DIRECTORY_DOWNLOADS, file_name);

                  DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                      String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
                      if (!EasyPermissions.hasPermissions(WebViewActivity.this, perms)) {
                          dl_request = request;
                          EasyPermissions.requestPermissions(WebViewActivity.this, getString(R.string.permission_storage_rationale), Const.PERMISSION_RC_STORAGE, perms);
                      } else {
                          if (dm != null) {
                              dm.enqueue(request);
                              NotificationUtils.showInfoSnackbar(this, getString(R.string.file_download), Snackbar.LENGTH_SHORT);
                          }
                      }
                  }
                  //No storage permission needed for Android 10+
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                      if (dm != null) {
                          dm.enqueue(request);
                          NotificationUtils.showInfoSnackbar(this, getString(R.string.file_download), Snackbar.LENGTH_SHORT);
                      }
                  }
                }

            }
        });
        wv.setOnTouchListener(new View.OnTouchListener() {
            private int mode = NONE;
            private float startX;
            private float stopX;
            private float startY;
            private float stopY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (webapp == null || webapp.isRequestDesktop())
                    return false;

                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_POINTER_DOWN:
                        // This happens when you touch the screen with two fingers
                        mode = SWIPE;
                        // You can also use event.getY(1) or the average of the two
                        startX = event.getX(0);
                        startY = event.getY(0);
                        return true;

                    case MotionEvent.ACTION_POINTER_UP:
                        // This happens when you release the second finger
                        mode = NONE;
                        if (Math.abs(startX - stopX) > TRESHOLD) {
                            if (startX > stopX) {
                                if (event.getPointerCount() == 3 && DataManager.getInstance().getSettings().isThreeFingerMultitouch()) {
                                    WebViewLauncher.startWebView(DataManager.getInstance().getPredecessor(webappID), WebViewActivity.this);
                                    finish();
                                } else if (DataManager.getInstance().getSettings().isTwoFingerMultitouch()) {
                                    if (wv.canGoForward())
                                        wv.goForward();
                                }
                            } else {
                                if (event.getPointerCount() == 3 && DataManager.getInstance().getSettings().isThreeFingerMultitouch()) {
                                    WebViewLauncher.startWebView(DataManager.getInstance().getSuccessor(webappID), WebViewActivity.this);
                                    finish();
                                } else if (DataManager.getInstance().getSettings().isTwoFingerMultitouch())
                                    onBackPressed();

                            }
                            return true;
                        }
                        if (DataManager.getInstance().getSettings().isMultitouchReload() && Math.abs(startY - stopY) > TRESHOLD) {
                            if (stopY > startY) {
                                currently_reloading = true;
                                wv.reload();
                            }
                            return true;
                        }
                    case MotionEvent.ACTION_MOVE:
                        if (mode == SWIPE) {
                            stopX = event.getX(0);
                            stopY = event.getY(0);
                        }
                        return false;
                }
                return false;
            }
        });
    }

    private JSONObject getSiteConfig(String url) {
        if (cachedSitesMap == null || url == null) return null;
        try {
            String host = Utility.getCanonicalHost(url);
            if (host.isEmpty()) return null;

            JSONObject site = cachedSitesMap.get(host);
            if (site != null) return site;

            // Fuzzy match for other subdomains (e.g., map.blitzortung.org matches blitzortung.org)
            for (String key : cachedSitesMap.keySet()) {
                if (host.endsWith("." + key)) {
                    return cachedSitesMap.get(key);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void applySiteSystemBars(String url) {
        try {
            JSONObject site = getSiteConfig(url);

            boolean isDarkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            if (webapp != null) {
                boolean needsForcedDarkMode = (webapp.isUseTimespanDarkMode() &&
                        DateUtils.isInInterval(DateUtils.convertStringToCalendar(webapp.getTimespanDarkModeBegin()), Calendar.getInstance(), DateUtils.convertStringToCalendar(webapp.getTimespanDarkModeEnd())))
                        || (!webapp.isUseTimespanDarkMode() && webapp.isForceDarkMode());
                if (needsForcedDarkMode) isDarkMode = true;
            }

            String defaultColor = isDarkMode ? "#000000" : "#F5F5F5";

            String statusBar = (site != null) ? site.optString("statusBarColor", defaultColor) : defaultColor;
            if (statusBar.isEmpty()) statusBar = defaultColor;

            String bottomBar = (site != null) ? site.optString("bottomBarColor", defaultColor) : defaultColor;
            if (bottomBar.isEmpty()) bottomBar = defaultColor;

            // Failsafe: If in dark/AMOLED mode, ignore light colors from JSON to prevent blinding the user
            if (isDarkMode) {
                if (statusBar != null && ColorUtils.calculateLuminance(Color.parseColor(statusBar)) > 0.5) {
                    statusBar = defaultColor;
                }
                if (bottomBar != null && ColorUtils.calculateLuminance(Color.parseColor(bottomBar)) > 0.5) {
                    bottomBar = defaultColor;
                }
            }

            String loadingBar = (site != null) ? site.optString("loadingBarColor", null) : null;

            Window window = getWindow();
            WindowInsetsControllerCompat windowInsetsController = new WindowInsetsControllerCompat(window, window.getDecorView());

            int statusBarColor = Color.parseColor(statusBar);
            window.setStatusBarColor(statusBarColor);
            windowInsetsController.setAppearanceLightStatusBars(ColorUtils.calculateLuminance(statusBarColor) > 0.5);

            int navBarColor = Color.parseColor(bottomBar);
            window.setNavigationBarColor(navBarColor);
            windowInsetsController.setAppearanceLightNavigationBars(ColorUtils.calculateLuminance(navBarColor) > 0.5);

            if (loadingBar != null && !loadingBar.isEmpty() && progressBar != null) {
                progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor(loadingBar)));
            }

        } catch (Exception e) {
            Log.e("NativeAlpha", "Error applying system bar colors", e);
        }
    }
    @SuppressLint("RequiresFeature")
    private void setDarkModeIfNeeded() {
        boolean needsForcedDarkMode = webapp.isUseTimespanDarkMode() &&
                DateUtils.isInInterval(DateUtils.convertStringToCalendar(webapp.getTimespanDarkModeBegin()), Calendar.getInstance(), DateUtils.convertStringToCalendar(webapp.getTimespanDarkModeEnd()))
                || (!webapp.isUseTimespanDarkMode() && webapp.isForceDarkMode());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean isForceDarkSupported = WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK);
            boolean isForceDarkStrategySupported = WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY);
            boolean isAlgorithmicDarkeningSupported = WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING);

            if (needsForcedDarkMode) {
                wv.setBackgroundColor(Color.BLACK);
                wv.setForceDarkAllowed(true);
                getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                if (isForceDarkSupported) {
                    WebSettingsCompat.setForceDark(wv.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
                }
                if (isForceDarkStrategySupported) {
                    WebSettingsCompat.setForceDarkStrategy(wv.getSettings(), WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING);
                }
                if (isAlgorithmicDarkeningSupported) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.getSettings(), true);
                }
            } else {
                getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

                if (isForceDarkSupported) {
                    WebSettingsCompat.setForceDark(wv.getSettings(), WebSettingsCompat.FORCE_DARK_OFF);
                }
                if (isForceDarkStrategySupported) {
                    WebSettingsCompat.setForceDarkStrategy(wv.getSettings(), WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY);
                }
                if (isAlgorithmicDarkeningSupported) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.getSettings(), false);
                }
            }
        }

    }

    @SuppressLint("NonConstantResourceId")
    private void showWebViewPopupMenu() {
        View center = findViewById(R.id.anchorCenterScreen);
        mPopupMenu = IconPopupMenuHelper.getMenu(center, R.menu.wv_context_menu, WebViewActivity.this);

        String currentUrl = wv.getUrl();
        String title = "";
        if (currentUrl != null) {
            title = currentUrl.length() < 32 ? currentUrl : currentUrl.substring(0, 32) + "…";
        }
        SpannableString spanStringWebAppTitle = new SpannableString(title);

        // The item is disabled because it has no click action, but we want to override the disabled style (text color)
        int colorOnSurface = MaterialColors.getColor(center, R.attr.colorOnSurface, Color.BLACK);
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(colorOnSurface);
        spanStringWebAppTitle.setSpan(foregroundColorSpan, 0,     spanStringWebAppTitle.length(), 0);

        spanStringWebAppTitle.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, spanStringWebAppTitle.length(), 0);
        mPopupMenu.getMenu().getItem(0).setTitle(spanStringWebAppTitle);

        for (int i = 0; i < mPopupMenu.getMenu().size(); i++) {
            MenuItem item = mPopupMenu.getMenu().getItem(i);
            SpannableString spanString = new SpannableString(item.getTitle());
            spanString.setSpan(foregroundColorSpan, 0, spanString.length(),0);
            item.setTitle(spanString);
        }
        if(wv.canGoForward()) mPopupMenu.getMenu().getItem(2).setVisible(true);
        if(BuildConfig.DEBUG) {
            mPopupMenu.getMenu().getItem(6).setVisible(true);
        }
        mPopupMenu.setOnMenuItemClickListener(menuItem -> {
            switch(menuItem.getItemId()) {
                case R.id.cmItemForward:
                    wv.goForward();
                    return true;
                case R.id.cmItemBack:
                    onBackPressed();
                    return true;
                case R.id.cmItemReload:
                    wv.reload();
                    return true;
                case R.id.cmItemCopyUrl:
                    ClipboardManager clipboard =  getSystemService(ClipboardManager.class);
                    ClipData clip = ClipData.newPlainText("URL", wv.getUrl());
                    clipboard.setPrimaryClip(clip);
                    return true;
                case R.id.cmItemShareUrl:
                    new ShareCompat.IntentBuilder(WebViewActivity.this)
                            .setType("text/plain")
                            .setChooserTitle("Share URL")
                            .setText(wv.getUrl())
                            .startChooser();
                    return true;
                case R.id.cmItemRemoveElements:
                    enterElementSelectionMode();
                    return true;
                case R.id.cmItemCloseWebApp:
                    finishAndRemoveTask();
                    return true;
                case R.id.cmFallbackContextmenuTemp:
                    fallbackToDefaultLongClickBehaviour = true;
                    return true;
                case R.id.cmMainMenu:
                    Intent intent = new Intent(this, MainActivity.class);
                    startActivity(intent);
                    return true;
                case R.id.cmShowAdblockProviders:
                    StringBuilder message = new StringBuilder();
                    for(Map.Entry<String, Filter> entry :  Objects.requireNonNull(AdFilter.Companion.get().getViewModel().getFilters().getValue()).entrySet()) {
                        Filter filter = entry.getValue();
                        message.append(filter.getUrl()).append(" has downloaded: ").append(filter.hasDownloaded()).append("\n\n");
                }
                    NotificationUtils.showToast(this, message.toString());
                    return true;

            }
            return false;
        });

        mPopupMenu.show();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        this.setDarkModeIfNeeded();
    }

    @Override
    public void onBackPressed() {
        if (webapp == null) return;

        if(wv.canGoBack()) {
            wv.goBack();
            return;
        }

        if(quitOnNextBackpress) {
            quitOnNextBackpress = false;
            moveTaskToBack(true);
            return;
        }

        loadURL(wv, webapp.getBaseUrl());
        quitOnNextBackpress = true;

    }

    @Override
    protected void onResume() {
        super.onResume();
        int new_id = getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1);

        if (new_id != webappID) {
            WebApp new_webapp = DataManager.getInstance().getWebApp(new_id);
            WebViewLauncher.startWebViewInNewProcess(new_webapp, this);
        }

        wv.onResume();
        wv.resumeTimers();

        // Battery Optimization: Re-enable JS if it was frozen
        if (webapp.isFreezeJsInBg() && webapp.isAllowJs()) {
            wv.getSettings().setJavaScriptEnabled(true);
        }

        this.setDarkModeIfNeeded();


        if(webapp.isBiometricProtection()) {
            View fullActivityView = findViewById(R.id.webviewActivity);
            fullActivityView.setVisibility(View.GONE);
            new BiometricPromptHelper(WebViewActivity.this).showPrompt(() -> fullActivityView.setVisibility(View.VISIBLE), () -> finish(), getString(R.string.bioprompt_restricted_webapp));
        }
        if (webapp.isAutoreload()) {
            reload_handler = new Handler();
            reload();
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

        wv.evaluateJavascript("document.querySelectorAll('audio').forEach(x => x.pause());document.querySelectorAll('video').forEach(x => x.pause());", null);

        // Battery Optimization: Disable JS in background
        if (webapp.isFreezeJsInBg() && webapp.isAllowJs()) {
            wv.getSettings().setJavaScriptEnabled(false);
        }

        wv.onPause();
        wv.pauseTimers();
        if(mPopupMenu != null) mPopupMenu.dismiss();

        if (webapp.isClearCache() || DataManager.getInstance().getSettings().isClearCache())
            wv.clearCache(true);

        if (reload_handler != null) {
            reload_handler.removeCallbacksAndMessages(null);
            Log.d("CLEANUP", "Stopped reload handler");
        }
    }

    private void reload() {
        reload_handler.postDelayed(() -> {
            // Battery Optimization: Skip reload if battery saver is on
            if (webapp.isBatterySaverReload()) {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(android.content.Context.POWER_SERVICE);
                if (pm != null && pm.isPowerSaveMode()) {
                    reload(); // Reschedule but don't load
                    return;
                }
            }

            currently_reloading = true;
            wv.reload();
            reload();
        }, webapp.getTimeAutoreload() * 1000L);
    }

    public WebView getWebView() {
        return wv;
    }

    private Map<String, String> initCustomHeaders(boolean save_data) {
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("DNT", "1");
        extraHeaders.put("X-REQUESTED-WITH", "");
        if (save_data)
            extraHeaders.put("Save-Data", "on");
        return Collections.unmodifiableMap(extraHeaders);
    }

    private void loadURL(final WebView view, final String url) {
        final WebApp webApp = DataManager.getInstance().getWebApp(webappID);
        if (url.contains("http://") && !webApp.isAllowHttp()) {
            final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(WebViewActivity.this, R.style.AppTheme_AlertDialog);

            builder.setTitle(getString(R.string.no_https_dialog_title));
            builder.setMessage(getString(R.string.no_https_dialog_msg));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(getString(R.string.no_https_dialog_accept), (dialog, id) -> {
                webApp.setAllowHttp(true);
                webApp.setOverrideGlobalSettings(true);
                DataManager.getInstance().saveWebAppData();
                view.loadUrl(url, CUSTOM_HEADERS);
            });
            builder.setNegativeButton(getString(android.R.string.cancel), (dialog, id) -> finish());
            final AlertDialog dialog = builder.create();
            dialog.show();
        } else
            view.loadUrl(url, CUSTOM_HEADERS);

    }
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if(controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());

                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void showSystemBars() {

        if(webapp.isShowFullscreen()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            getWindow().setDecorFitsSystemWindows(true);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
    @FunctionalInterface
    interface PermissionGrantedCallback {
        void execute();
    }

    private void enablePermissionBoolOnWebApp(PermissionGrantedCallback successCallback) {
        webapp.setOverrideGlobalSettings(true);
        successCallback.execute();
        DataManager.getInstance().replaceWebApp(webapp);
        wv.reload();
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> list) {
        if (requestCode == Const.PERMISSION_RC_LOCATION) {
            enablePermissionBoolOnWebApp(() -> webapp.setAllowLocationAccess(true));
            this.handleGeoPermissionCallback(true);
        }
        if (requestCode == Const.PERMISSION_CAMERA) {
            enablePermissionBoolOnWebApp(() -> webapp.setCameraPermission(true));
        }
        if (requestCode == Const.PERMISSION_RC_STORAGE) {
            if (dl_request != null) {
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(dl_request);
                    NotificationUtils.showInfoSnackbar(this, getString(R.string.file_download), Snackbar.LENGTH_SHORT);
                }
                dl_request = null;

            }
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        if (requestCode == Const.PERMISSION_RC_LOCATION) {
            this.handleGeoPermissionCallback(false);
        }
    }

    private void handleGeoPermissionCallback(boolean allow) {
        if (mGeoPermissionRequestCallback != null) {
            mGeoPermissionRequestCallback.invoke(mGeoPermissionRequestOrigin, allow, false);
            mGeoPermissionRequestCallback = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent intent) {

        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == RESULT_CANCELED && requestCode == CODE_OPEN_FILE) {
            this.filePathCallback.onReceiveValue(null);
        } else if (resultCode == RESULT_OK && requestCode == CODE_OPEN_FILE) {
            filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
            filePathCallback = null;
        }
    }


    private class CustomWebChromeClient extends android.webkit.WebChromeClient {
        private View mCustomView;
        private WebChromeClient.CustomViewCallback mCustomViewCallback;
        private int mOriginalOrientation;
        private int mOriginalSystemUiVisibility;

        private void handlePermissionRequest(String resId,
                                             boolean currentState,
                                             String[] androidPermissions,
                                             int requestCode,
                                             List<String> permissionsToGrant,
                                             String[] webkitPermission,
                                             PermissionGrantedCallback successCallback) {
            boolean androidPermissionsMissing = !EasyPermissions.hasPermissions(WebViewActivity.this, androidPermissions);
            if (currentState && androidPermissionsMissing) {
                ActivityCompat.requestPermissions(WebViewActivity.this, androidPermissions, requestCode);
                return;
            }
            if (currentState && !androidPermissionsMissing) {
                permissionsToGrant.addAll(Arrays.asList(webkitPermission));
                handleGeoPermissionCallback(true);
                return;
            }

            new MaterialAlertDialogBuilder(WebViewActivity.this, R.style.AppTheme_AlertDialog).setTitle(getPermissionRequestStringResource("dialog_permission_", resId, "_title"))
                    .setMessage(getPermissionRequestStringResource("dialog_permission_", resId, "_txt"))
                    .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                        enablePermissionBoolOnWebApp(successCallback);
                        handleGeoPermissionCallback(true);
                        permissionsToGrant.addAll(Arrays.asList(webkitPermission));
                        if (androidPermissionsMissing) {
                            ActivityCompat.requestPermissions(WebViewActivity.this, androidPermissions, requestCode);
                        }
                    }).setNegativeButton(android.R.string.no, (dialog, id) -> handleGeoPermissionCallback(false)).create().show();
        }

        private String getPermissionRequestStringResource(String prefix, String variable, String suffix) {
            return getString(WebViewActivity.this.getResources().getIdentifier(prefix + variable + suffix, "string", WebViewActivity.this.getPackageName()));
        }

        @Override
        public boolean onShowFileChooser(
                WebView webView, ValueCallback<Uri[]> pFilePathCallback,
                WebChromeClient.FileChooserParams fileChooserParams) {
            filePathCallback = pFilePathCallback;
            try {
                Intent intent = fileChooserParams.createIntent();
                startActivityForResult(intent, CODE_OPEN_FILE);
            } catch (Exception e) {
                NotificationUtils.showInfoSnackbar(WebViewActivity.this, getString(R.string.no_filemanager), Snackbar.LENGTH_LONG);
                e.printStackTrace();
            }
            return true;
        }

        @Override
        public Bitmap getDefaultVideoPoster() {
            final Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawARGB(0, 0, 0, 0);
            return bitmap;
        }

        public void onHideCustomView() {
            ((FrameLayout) getWindow().getDecorView()).removeView(this.mCustomView);
            this.mCustomView = null;
            getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
            setRequestedOrientation(this.mOriginalOrientation);
            this.mCustomViewCallback.onCustomViewHidden();
            this.mCustomViewCallback = null;
            showSystemBars();
        }

        public void onShowCustomView(View pView, WebChromeClient.CustomViewCallback pViewCallback) {
            if (this.mCustomView != null) {
                onHideCustomView();
                return;
            }
            this.mCustomView = pView;
            this.mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            this.mOriginalOrientation = getRequestedOrientation();
            this.mCustomViewCallback = pViewCallback;
            ((FrameLayout) getWindow().getDecorView()).addView(this.mCustomView, new FrameLayout.LayoutParams(-1, -1));
            hideSystemBars();
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            List<String> permissionsToGrant = new ArrayList<>();

            boolean containsDrmRequest = Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID);
            boolean containsCameraRequest = Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
            boolean containsMicrophoneRequest = Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE);

            if (containsDrmRequest) {
                this.handlePermissionRequest("drm", webapp.isDrmAllowed(), new String[]{}, -1, permissionsToGrant, new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID}, () -> webapp.setDrmAllowed(true));
            }
            if (containsCameraRequest) {
                this.handlePermissionRequest("camera", webapp.isCameraPermission(), new String[]{Manifest.permission.CAMERA}, Const.PERMISSION_CAMERA, permissionsToGrant, new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}, () -> webapp.setCameraPermission(true));
            }

            if (containsMicrophoneRequest) {
                this.handlePermissionRequest("microphone", webapp.isMicrophonePermission(), new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS}, Const.PERMISSION_AUDIO, permissionsToGrant, new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE}, () -> webapp.setMicrophonePermission(true));
            }

            request.grant(permissionsToGrant.toArray(new String[0]));
        }


        public void onProgressChanged(WebView view, int progress) {

            if (DataManager.getInstance().getSettings().isShowProgressbar() || currently_reloading) {
                if (progressBar.getVisibility() == ProgressBar.GONE && progress < 100) {
                    progressBar.setVisibility(ProgressBar.VISIBLE);
                }

                progressBar.setProgress(progress);

                if (progress == 100) {
                    progressBar.setVisibility(ProgressBar.GONE);
                    currently_reloading = false;
                }
            }
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(final String origin,
                                                       final GeolocationPermissions.Callback callback) {
            mGeoPermissionRequestCallback = callback;
            mGeoPermissionRequestOrigin = origin;
            this.handlePermissionRequest("location", webapp.isAllowLocationAccess(), new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, Const.PERMISSION_RC_LOCATION, Arrays.asList(new String[]{}), new String[]{}, () -> webapp.setAllowLocationAccess(true));

        }
    }

    private void showHttpAuthDialog(final HttpAuthHandler handler, String host, String realm) {
        DialogHttpAuthBinding localBinding = DialogHttpAuthBinding.inflate(LayoutInflater.from(this));
        new MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog)
                .setView(localBinding.getRoot())
                .setTitle(getString(R.string.http_auth_title))
                .setMessage(getString(R.string.enter_http_auth_credentials, realm, host))
                .setPositiveButton(getString(R.string.ok), (dialog, whichButton) -> {
                    String username = localBinding.username.getText().toString();
                    String password = localBinding.password.getText().toString();

                    handler.proceed(username, password);

                })
                .setNegativeButton(getString(R.string.cancel), (dialog, whichButton) -> handler.cancel())
                .show();
    }
    private void applySiteRules(WebView view, String url) {
        try {
            JSONObject site = getSiteConfig(url);
            if (site == null) return;

            StringBuilder cssToInject = new StringBuilder();

            // 1. Convert 'remove' array to hiding CSS
            JSONArray remove = site.optJSONArray("remove");
            if (remove != null && remove.length() > 0) {
                for (int j = 0; j < remove.length(); j++) {
                    if (j > 0) cssToInject.append(",");
                    cssToInject.append(remove.getString(j));
                }
                cssToInject.append(" { display: none !important; visibility: hidden !important; pointer-events: none !important; } ");
            }

            // 2. Add existing injectCss
            JSONArray css = site.optJSONArray("injectCss");
            if (css != null) {
                for (int j = 0; j < css.length(); j++) {
                    cssToInject.append(css.getString(j));
                }
            }

            if (cssToInject.length() > 0) {
                String js = "(function() {" +
                        "  var run = function() {" +
                        "    var style = document.getElementById('na-site-rules') || document.createElement('style');" +
                        "    style.id = 'na-site-rules';" +
                        "    var css = " + JSONObject.quote(cssToInject.toString()) + ";" +
                        "    if (style.textContent !== css) style.textContent = css;" +
                        "    if (!style.parentElement) (document.head || document.documentElement).appendChild(style);" +
                        "  };" +
                        "  if (document.readyState === 'loading') {" +
                        "    document.addEventListener('DOMContentLoaded', run);" +
                        "  } else { run(); }" +
                        "  /* Robust removal via Shadow-aware Observer */" +
                        "  var selectors = " + (remove != null ? remove.toString() : "[]") + ";" +
                        "  if (selectors.length > 0) {" +
                        "    var hideInRoot = function(root) {" +
                        "      selectors.forEach(function(s) {" +
                        "        try {" +
                        "          var elms = root.querySelectorAll(s);" +
                        "          for(var i=0; i<elms.length; i++) {" +
                        "            if (elms[i].style.display !== 'none') elms[i].style.display = 'none';" +
                        "          }" +
                        "        } catch(e) {}" +
                        "      });" +
                        "      /* Pierce Shadow DOM lazily */" +
                        "      var all = root.querySelectorAll('*');" +
                        "      for(var i=0; i<all.length; i++) if(all[i].shadowRoot) hideInRoot(all[i].shadowRoot);" +
                        "    };" +
                        "    var timer = null;" +
                        "    var apply = function() {" +
                        "      if (timer) clearTimeout(timer);" +
                        "      timer = setTimeout(function(){ hideInRoot(document); }, 100);" +
                        "    };" +
                        "    apply();" +
                        "    new MutationObserver(apply).observe(document.documentElement, {childList:true, subtree:true});" +
                        "  }" +
                        "})();";
                view.evaluateJavascript(js, null);
            }

        } catch (Exception e) {
            Log.e("NativeAlpha", "Error in applySiteRules", e);
        }
    }
    private void enterElementSelectionMode() {
        try {
            InputStream is = getAssets().open("element_selector.js");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String js = new String(buffer, StandardCharsets.UTF_8);
            wv.evaluateJavascript(js, null);
        } catch (IOException e) {
            Log.e("NativeAlpha", "Error loading element_selector.js", e);
        }
    }

    private class WebAppInterface {
        @android.webkit.JavascriptInterface
        public void onElementSelected(String selector) {
            runOnUiThread(() -> {
                new MaterialAlertDialogBuilder(WebViewActivity.this, R.style.AppTheme_AlertDialog)
                        .setTitle("Remove Element?")
                        .setMessage("Do you want to permanently hide elements matching:\n" + selector)
                        .setPositiveButton("Remove", (dialog, which) -> {
                            saveRemovalRule(selector);
                            sessionAddedRules.add(selector);
                            wv.reload();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }

        @android.webkit.JavascriptInterface
        public void undoLastRemoval() {
            runOnUiThread(() -> {
                if (sessionAddedRules.isEmpty()) {
                    NotificationUtils.showToast(WebViewActivity.this, "No elements removed in this session");
                    return;
                }
                String lastSelector = sessionAddedRules.remove(sessionAddedRules.size() - 1);
                removeRemovalRule(lastSelector);
                wv.reload();
                NotificationUtils.showToast(WebViewActivity.this, "Restored: " + lastSelector);
            });
        }

        @android.webkit.JavascriptInterface
        public void openSettings() {
            runOnUiThread(() -> {
                Intent intent = new Intent(WebViewActivity.this, WebAppSettingsActivity.class);
                intent.putExtra(Const.INTENT_WEBAPPID, webappID);
                startActivity(intent);
            });
        }
    }

    private void removeRemovalRule(String selector) {
        try {
            String host = Utility.getCanonicalHost(wv.getUrl());
            if (host.isEmpty()) return;

            File internalFile = new File(getFilesDir(), "sites.json");
            if (!internalFile.exists()) return;

            String content = new String(java.nio.file.Files.readAllBytes(internalFile.toPath()), StandardCharsets.UTF_8);
            JSONObject json = normalizeJsonKeys(new JSONObject(content));
            
            String keyToUse = host;
            if (!json.has(host)) {
                for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                    String key = it.next();
                    if (host.endsWith("." + key)) {
                        keyToUse = key;
                        break;
                    }
                }
            }
            
            JSONObject siteConfig = json.optJSONObject(keyToUse);

            if (siteConfig != null) {
                JSONArray removeArray = siteConfig.optJSONArray("remove");
                if (removeArray != null) {
                    JSONArray newArray = new JSONArray();
                    for (int i = 0; i < removeArray.length(); i++) {
                        if (!removeArray.getString(i).equals(selector)) {
                            newArray.put(removeArray.getString(i));
                        }
                    }
                    siteConfig.put("remove", newArray);
                    json.put(keyToUse, siteConfig);

                    try (FileOutputStream fos = new FileOutputStream(internalFile)) {
                        fos.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                    }
                    cachedSitesMap.put(keyToUse, siteConfig);
                }
            }
        } catch (Exception e) {
            Log.e("NativeAlpha", "Error removing rule", e);
        }
    }

    private void saveRemovalRule(String selector) {
        try {
            String host = Utility.getCanonicalHost(wv.getUrl());
            if (host.isEmpty()) return;

            File internalFile = new File(getFilesDir(), "sites.json");
            String content = "";
            if (internalFile.exists()) {
                FileInputStream fis = new FileInputStream(internalFile);
                byte[] data = new byte[(int) internalFile.length()];
                int readCount = fis.read(data);
                fis.close();
                content = new String(data, 0, readCount, StandardCharsets.UTF_8);
            } else {
                InputStream is = getAssets().open("sites.json");
                byte[] buffer = new byte[is.available()];
                int readCount = is.read(buffer);
                is.close();
                content = new String(buffer, 0, readCount, StandardCharsets.UTF_8);
            }

            JSONObject json = normalizeJsonKeys(new JSONObject(content));
            
            // Try to find the most specific existing key that matches this host
            String keyToUse = host;
            if (!json.has(host)) {
                for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                    String key = it.next();
                    if (host.endsWith("." + key)) {
                        keyToUse = key;
                        break;
                    }
                }
            }
            
            JSONObject siteConfig = json.optJSONObject(keyToUse);
            if (siteConfig == null) siteConfig = new JSONObject();

            JSONArray removeArray = siteConfig.optJSONArray("remove");
            if (removeArray == null) removeArray = new JSONArray();
            removeArray.put(selector);

            siteConfig.put("remove", removeArray);
            json.put(keyToUse, siteConfig);

            FileOutputStream fos = new FileOutputStream(internalFile);
            fos.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
            fos.close();

            cachedSitesMap.put(keyToUse, siteConfig);
        } catch (Exception e) {
            Log.e("NativeAlpha", "Error saving removal rule", e);
        }
    }

    private class CustomBrowser extends WebViewClient {

        private AdFilter adFilter = AdFilter.Companion.get();

        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
            showHttpAuthDialog(handler, host, realm);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            applySiteRules(view, url);
            super.onPageCommitVisible(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            applySiteRules(view, url);

            if (url.equals("about:blank")) {
                String langExtension = LocaleUtils.getFileEnding();
                wv.loadUrl("file:///android_asset/errorSite/error_" + langExtension + ".html");
                return;
            }

            if (webapp.isRequestDesktop()) {
                view.evaluateJavascript("""
                        var needsForcedWidth = document.documentElement.clientWidth < 1200;
                        if(needsForcedWidth) {
                          document.querySelector('meta[name="viewport"]').setAttribute('content', 'width=1200px, initial-scale=' + (document.documentElement.clientWidth / 1200));
                        }
                       """, null);
            }
            view.evaluateJavascript("document.addEventListener(\"visibilitychange\", (event) => { event.stopImmediatePropagation(); });", null);

            super.onPageFinished(view, url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            applySiteSystemBars(url);
            applySiteRules(view, url);
            adFilter.performScript(view, url);

            super.onPageStarted(view, url, favicon);
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if(urlOnFirstPageload.equals("")) urlOnFirstPageload = request.getUrl().toString();

            if(webapp.isUseAdblock()) {
                return (adFilter.shouldIntercept(view, request)).getResourceResponse();
            }
            if (webapp.isBlockThirdPartyRequests()) {
                Uri uri = request.getUrl();
                Uri webapp_uri = Uri.parse(webapp.getBaseUrl());

                if(uri.getHost() != null) {
                    if (!uri.getHost().endsWith(webapp_uri.getHost())) {
                        return new WebResourceResponse("text/plain", "utf-8", null);
                    }
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {

            //This option is hidden in "expert settings"
            if (webapp.isIgnoreSslErrors()) {
                handler.proceed();
                return;
            }

            final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(WebViewActivity.this, R.style.AppTheme_AlertDialog);

            String message = getString(R.string.ssl_error_msg_line1) + " ";
            switch (error.getPrimaryError()) {
                case SslError.SSL_UNTRUSTED:
                    message += getString(R.string.ssl_error_unknown_authority) + "\n";
                    break;
                case SslError.SSL_EXPIRED:
                    message += getString(R.string.ssl_error_expired) + "\n";
                    break;
                case SslError.SSL_IDMISMATCH:
                    message += getString(R.string.ssl_error_id_mismatch) + "\n";
                    break;
                case SslError.SSL_NOTYETVALID:
                    message += getString(R.string.ssl_error_notyetvalid) + "\n";
                    break;
            }
            message += getString(R.string.ssl_error_msg_line2) + "\n";

            builder.setTitle(getString(R.string.ssl_error_title));
            builder.setMessage(message);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(getString(android.R.string.cancel), (dialog, id) -> handler.cancel());
            builder.setNegativeButton(getString(R.string.load_anyway), (dialog, id) -> handler.proceed());
            final AlertDialog dialog = builder.create();
            dialog.show();
//            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setPadding(5, 5, 5, 5);
//            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setBackgroundColor(ContextCompat.getColor(WebViewActivity.this, android.R.color.holo_orange_light));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(WebViewActivity.this, android.R.color.holo_red_dark));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(WebViewActivity.this, android.R.color.holo_green_dark));
        }

        @Override
        public void onLoadResource(WebView view, String url) {
            super.onLoadResource(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            runOnUiThread(() -> setDarkModeIfNeeded());
            String url = request.getUrl().toString();

            if (webapp == null) return true;

            if (url.startsWith("tel:")) {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
                startActivity(intent);
                return true;
            }
            if (url.startsWith("mailto:")) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }

            if (webapp.isOpenUrlExternal()) {
                String base_url = webapp.getBaseUrl();
                Uri uri = Uri.parse(base_url);
                String host = uri.getHost();
                if (!url.contains(host)) {
                    view.getContext().startActivity(
                            new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
            }
            loadURL(view, url);
            return true;
        }
    }
}


