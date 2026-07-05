package com.cylonid.nativealpha;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
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
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
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

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.cylonid.nativealpha.helper.BiometricPromptHelper;
import com.cylonid.nativealpha.helper.IconPopupMenuHelper;
import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.SandboxManager;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.util.DateUtils;
import com.cylonid.nativealpha.util.EntryPointUtils;
import com.cylonid.nativealpha.util.LocaleUtils;
import com.cylonid.nativealpha.util.MediaKeepAliveService;
import com.cylonid.nativealpha.util.NotificationUtils;
import com.cylonid.nativealpha.util.ShortcutIconUtils;
import com.cylonid.nativealpha.util.SiteConfigManager;
import com.cylonid.nativealpha.util.Utility;
import com.cylonid.nativealpha.util.WebViewLauncher;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private MediaSession mediaSession;
    private BroadcastReceiver mediaReceiver;
    private boolean userWantsPlaying = true;
    private String urlOnFirstPageload = "";
    private boolean fallbackToDefaultLongClickBehaviour = false;
    private PopupMenu mPopupMenu = null;
    private boolean backgroundPlaybackStarted = false;

    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData()));
                    filePathCallback = null;
                }
            }
    );

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d("NativeAlpha", "WebViewActivity onNewIntent. TaskID: " + getTaskId());
        setIntent(intent);
        int newWebappID = intent.getIntExtra(Const.INTENT_WEBAPPID, -1);
        if (newWebappID != -1 && newWebappID != webappID) {
            webappID = newWebappID;
            webapp = DataManager.getInstance().getWebApp(webappID);
            setupWebView();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Set isolation suffix BEFORE super.onCreate
        int tempWebappID = getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1);
        String processName = Application.getProcessName();
        String packageName = getPackageName();

        if (tempWebappID != -1) {
            DataManager.getInstance().loadAppData();
            webapp = DataManager.getInstance().getWebApp(tempWebappID);
            if (webapp != null && webapp.isUseContainer() && SandboxManager.getInstance() != null) {
                if (!packageName.equals(processName)) {
                    try {
                        WebView.setDataDirectorySuffix(webapp.getContainerId() + webapp.getAlphanumericBaseUrl() + "_" + webapp.getID());
                    } catch (IllegalStateException ignored) {}
                }
            }
        }

        super.onCreate(savedInstanceState);
        Log.d("NativeAlpha", "WebViewActivity onCreate PID: " + android.os.Process.myPid());

        webappID = tempWebappID;
        webapp = DataManager.getInstance().getWebApp(webappID);
        if (webapp == null) {
            finish();
            return;
        }

        // 2. Multi-process re-launch logic
        if (packageName.equals(processName) && webapp.isUseContainer() && SandboxManager.getInstance() != null) {
            WebViewLauncher.startWebViewInNewProcess(webapp, this);
            return;
        }

        if (!packageName.equals(processName) && SandboxManager.getInstance() != null) {
            if (SandboxManager.getInstance().isSandboxUsedByAnotherApp(webapp)) {
                SandboxManager.getInstance().unregisterWebAppFromSandbox(webapp.getContainerId());
                WebViewLauncher.startWebViewInNewProcess(webapp, this);
                return;
            }
            try {
                SandboxManager.getInstance().registerWebAppToSandbox(webapp);
            } catch (Exception ignored) {}
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (wv != null && wv.canGoBack()) {
                    wv.goBack();
                } else if (webapp != null && webapp.isAllowMediaPlaybackInBackground()) {
                    moveTaskToBack(true);
                } else if (quitOnNextBackpress) {
                    quitOnNextBackpress = false;
                    moveTaskToBack(true);
                } else {
                    if (webapp != null) loadURL(wv, webapp.getBaseUrl());
                    quitOnNextBackpress = true;
                }
            }
        });

        // 3. Simple component initialization
        EntryPointUtils.entryPointReached(this);
        
        if (webapp.isBiometricProtection()) {
            new BiometricPromptHelper(this).showPrompt(this::setupWebView, this::finish, getString(R.string.bioprompt_restricted_webapp));
        } else {
            setupWebView();
        }
    }

    private void updatePlaybackState(int state) {
        if (mediaSession != null) {
            PlaybackState playbackState = new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)
                    .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .build();
            mediaSession.setPlaybackState(playbackState);
        }
    }

    private void initMediaSession() {
        if (webapp != null && webapp.isAllowMediaPlaybackInBackground()) {
            if (mediaSession != null) {
                mediaSession.setActive(true);
                return;
            }
            
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> Log.d("NativeAlpha", "Audio focus changed: " + focusChange);

            AudioFocusRequest afr = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build();
            am.requestAudioFocus(afr);

            mediaSession = new MediaSession(this, "NativeAlphaMedia");
            
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, webapp.getTitle())
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Native Alpha++")
                    .build();
            mediaSession.setMetadata(metadata);
            
            mediaSession.setActive(true);
            updatePlaybackState(PlaybackState.STATE_PLAYING);
            
            mediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    Log.d("NativeAlpha", "MediaSession onPlay");
                    userWantsPlaying = true;
                    if (wv != null) wv.evaluateJavascript("window._naUserWantsPlaying = true; document.querySelectorAll('video, audio').forEach(m => { m.play(); if (m.tagName === 'VIDEO') m.style.display = 'block'; })", null);
                    updatePlaybackState(PlaybackState.STATE_PLAYING);
                }

                @Override
                public void onPause() {
                    Log.d("NativeAlpha", "MediaSession onPause");
                    userWantsPlaying = false;
                    if (webapp.isAllowMediaPlaybackInBackground() && wv != null) {
                         wv.evaluateJavascript("window._naUserWantsPlaying = false; document.querySelectorAll('video, audio').forEach(m => m.pause())", null);
                         updatePlaybackState(PlaybackState.STATE_PAUSED);
                    }
                }
            });

            if (mediaReceiver == null) {
                mediaReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (mediaSession == null) return;
                        if ("com.cylonid.nativealpha.ACTION_PLAY".equals(intent.getAction())) {
                            mediaSession.getController().getTransportControls().play();
                        } else if ("com.cylonid.nativealpha.ACTION_PAUSE".equals(intent.getAction())) {
                            mediaSession.getController().getTransportControls().pause();
                        }
                    }
                };
                IntentFilter filter = new IntentFilter();
                filter.addAction("com.cylonid.nativealpha.ACTION_PLAY");
                filter.addAction("com.cylonid.nativealpha.ACTION_PAUSE");
                ContextCompat.registerReceiver(this, mediaReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
            }
        }
    }

    private void setupWebView() {
        setContentView(R.layout.full_webview);

        if(webapp.isKeepAwake()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        String url = webapp.getBaseUrl();
        wv = findViewById(R.id.webview);
        if (wv == null) {
            finish();
            return;
        }
        wv.setBackgroundColor(Color.BLACK);
        progressBar = findViewById(R.id.progressBar);

        String uaString = wv.getSettings().getUserAgentString();
        wv.getSettings().setUserAgentString(uaString.replace("; wv", "")); // Safe cleaning

        if (webapp.isUseCustomUserAgent()) {
            if(webapp.getUserAgent() != null && !webapp.getUserAgent().isEmpty()) {
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
        wv.getSettings().setDomStorageEnabled(true);
        wv.getSettings().setDatabaseEnabled(true);
        wv.getSettings().setAllowFileAccess(true);
        wv.getSettings().setBlockNetworkLoads(false);
        wv.getSettings().setOffscreenPreRaster(true);
        this.setDarkModeIfNeeded();

        wv.getSettings().setJavaScriptEnabled(webapp.isAllowJs());
        
        try {
            JSONObject site = SiteConfigManager.INSTANCE.getSiteConfig(this, url);
            if (site != null && site.has("userAgent")) {
                wv.getSettings().setUserAgentString(site.getString("userAgent"));
            }
        } catch (Exception e) {
            Log.e("NativeAlpha", "Error setting user agent from site config", e);
        }

        if (webapp.isAllowMediaPlaybackInBackground()) {
            wv.getSettings().setMediaPlaybackRequiresUserGesture(false);
            wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false);
        }

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
        wv.addJavascriptInterface(new WebAppInterface(), "NativeAlpha");
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
            if ("application/pdf".equals(mimeType)) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(dl_url));
                startActivity(i);
            } else {
                if(dl_url != null && !dl_url.isEmpty()) {
                    String final_dl_url;
                    if(dl_url.startsWith("blob:")) {
                        String rawBlob = dl_url.replace("blob:", "");
                        String decodedValue;
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                decodedValue = URLDecoder.decode(rawBlob, StandardCharsets.UTF_8);
                            } else {
                                //noinspection deprecation
                                decodedValue = URLDecoder.decode(rawBlob, "UTF-8");
                            }
                        } catch (Exception ignored) {
                            decodedValue = rawBlob;
                        }
                        final_dl_url = decodedValue;
                    } else {
                        final_dl_url = dl_url;
                    }
                    try {
                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(final_dl_url));
                        String file_name = Utility.getFileNameFromDownload(final_dl_url, contentDisposition, mimeType);
                        request.setMimeType(mimeType);
                        request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(final_dl_url));
                        request.addRequestHeader("User-Agent", userAgent);
                        request.setTitle(file_name);
                        request.allowScanningByMediaScanner();
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file_name);

                        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
                            if (!EasyPermissions.hasPermissions(this, perms)) {
                                dl_request = request;
                                EasyPermissions.requestPermissions(this, getString(R.string.permission_storage_rationale), Const.PERMISSION_RC_STORAGE, perms);
                            } else if (dm != null) {
                                dm.enqueue(request);
                                NotificationUtils.showInfoSnackbar(this, getString(R.string.file_download), Snackbar.LENGTH_SHORT);
                            }
                        } else if (dm != null) {
                            dm.enqueue(request);
                            NotificationUtils.showInfoSnackbar(this, getString(R.string.file_download), Snackbar.LENGTH_SHORT);
                        }
                    } catch(Exception e) {
                        NotificationUtils.showInfoSnackbar(this, getString(R.string.file_download), Snackbar.LENGTH_SHORT);
                    }
                }
            }
        });

        wv.setOnTouchListener(new View.OnTouchListener() {
            private int mode = NONE;
            private float startX, stopX, startY, stopY;
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (webapp != null && webapp.isRequestDesktop()) return false;
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_POINTER_DOWN:
                        mode = SWIPE;
                        startX = event.getX(0);
                        startY = event.getY(0);
                        return true;
                    case MotionEvent.ACTION_POINTER_UP:
                        mode = NONE;
                        if (Math.abs(startX - stopX) > TRESHOLD) {
                            if (startX > stopX) {
                                if (event.getPointerCount() == 3 && DataManager.getInstance().getSettings().isThreeFingerMultitouch()) {
                                    WebViewLauncher.startWebView(DataManager.getInstance().getPredecessor(webappID), WebViewActivity.this);
                                    finish();
                                } else if (DataManager.getInstance().getSettings().isTwoFingerMultitouch()) {
                                    if (wv != null && wv.canGoForward()) wv.goForward();
                                }
                            } else {
                                if (event.getPointerCount() == 3 && DataManager.getInstance().getSettings().isThreeFingerMultitouch()) {
                                    WebViewLauncher.startWebView(DataManager.getInstance().getSuccessor(webappID), WebViewActivity.this);
                                    finish();
                                } else if (DataManager.getInstance().getSettings().isTwoFingerMultitouch()) {
                                    getOnBackPressedDispatcher().onBackPressed();
                                }
                            }
                            return true;
                        }
                        if (DataManager.getInstance().getSettings().isMultitouchReload() && Math.abs(startY - stopY) > TRESHOLD) {
                            if (stopY > startY && wv != null) {
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

    private void applySiteSystemBars(String url) {
        try {
            JSONObject site = SiteConfigManager.INSTANCE.getSiteConfig(this, url);
            if (site == null) return;
            String statusBar = site.optString("statusBarColor", "");
            String bottomBar = site.optString("bottomBarColor", "");
            String loadingBar = site.optString("loadingBarColor", "");
            Window window = getWindow();
            WindowInsetsControllerCompat windowInsetsController = new WindowInsetsControllerCompat(window, window.getDecorView());
            if (!statusBar.isEmpty()) {
                int color = Color.parseColor(statusBar);
                window.setStatusBarColor(color);
                windowInsetsController.setAppearanceLightStatusBars(ColorUtils.calculateLuminance(color) > 0.5);
            }
            if (!bottomBar.isEmpty()) {
                int color = Color.parseColor(bottomBar);
                window.setNavigationBarColor(color);
                windowInsetsController.setAppearanceLightNavigationBars(ColorUtils.calculateLuminance(color) > 0.5);
            }
            if (!loadingBar.isEmpty() && progressBar != null) {
                progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor(loadingBar)));
            }
        } catch (Exception e) {
            Log.e("NativeAlpha", "applySiteSystemBars error", e);
        }
    }

    @SuppressLint({"RequiresFeature", "deprecation"})
    private void setDarkModeIfNeeded() {
        if (webapp == null || wv == null) return;
        
        Calendar begin = DateUtils.convertStringToCalendar(webapp.getTimespanDarkModeBegin());
        Calendar end = DateUtils.convertStringToCalendar(webapp.getTimespanDarkModeEnd());
        
        boolean needsForcedDarkMode = webapp.isUseTimespanDarkMode() && begin != null && end != null &&
                DateUtils.isInInterval(begin, Calendar.getInstance(), end)
                || (!webapp.isUseTimespanDarkMode() && webapp.isForceDarkMode());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean isForceDarkStrategySupported = WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY);
            boolean isAlgorithmicDarkeningSupported = WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING);

            if (needsForcedDarkMode) {
                wv.setBackgroundColor(Color.BLACK);
                getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    wv.getSettings().setAlgorithmicDarkeningAllowed(true);
                } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    wv.getSettings().setAlgorithmicDarkeningAllowed(false);
                } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
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
        if (center == null || wv == null) return;
        mPopupMenu = IconPopupMenuHelper.getMenu(center, R.menu.wv_context_menu, this);
        String currentUrl = wv.getUrl();
        String title = (currentUrl != null && currentUrl.length() >= 32) ? currentUrl.substring(0, 32) + "…" : (currentUrl != null ? currentUrl : "");
        SpannableString spanStringWebAppTitle = new SpannableString(title);
        int colorOnSurface = MaterialColors.getColor(center, R.attr.colorOnSurface, Color.BLACK);
        spanStringWebAppTitle.setSpan(new ForegroundColorSpan(colorOnSurface), 0, spanStringWebAppTitle.length(), 0);
        spanStringWebAppTitle.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, spanStringWebAppTitle.length(), 0);
        mPopupMenu.getMenu().getItem(0).setTitle(spanStringWebAppTitle);
        for (int i = 0; i < mPopupMenu.getMenu().size(); i++) {
            MenuItem item = mPopupMenu.getMenu().getItem(i);
            SpannableString spanString = new SpannableString(item.getTitle());
            spanString.setSpan(new ForegroundColorSpan(colorOnSurface), 0, spanString.length(),0);
            item.setTitle(spanString);
        }
        if(wv.canGoForward()) mPopupMenu.getMenu().getItem(2).setVisible(true);
        if((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) mPopupMenu.getMenu().getItem(6).setVisible(true);
        mPopupMenu.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.cmItemForward) { wv.goForward(); return true; }
            if (id == R.id.cmItemBack) { getOnBackPressedDispatcher().onBackPressed(); return true; }
            if (id == R.id.cmItemReload) { wv.reload(); return true; }
            if (id == R.id.cmItemCopyUrl) {
                ClipboardManager clipboard =  getSystemService(ClipboardManager.class);
                clipboard.setPrimaryClip(ClipData.newPlainText("URL", wv.getUrl()));
                return true;
            }
            if (id == R.id.cmItemShareUrl) {
                new ShareCompat.IntentBuilder(this).setType("text/plain").setChooserTitle("Share URL").setText(wv.getUrl()).startChooser();
                return true;
            }
            if (id == R.id.cmItemCloseWebApp) {
                if (webapp != null && webapp.isAllowMediaPlaybackInBackground()) moveTaskToBack(true);
                else finishAndRemoveTask();
                return true;
            }
            if (id == R.id.cmFallbackContextmenuTemp) { fallbackToDefaultLongClickBehaviour = true; return true; }
            if (id == R.id.cmMainMenu) { startActivity(new Intent(this, MainActivity.class)); return true; }
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
    protected void onResume() {
        super.onResume();
        Log.d("NativeAlpha", "WebViewActivity onResume. webapp: " + (webapp != null ? webapp.getTitle() : "null"));
        backgroundPlaybackStarted = false;
        int new_id = getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1);

        if (new_id != webappID) {
            WebApp new_webapp = DataManager.getInstance().getWebApp(new_id);
            WebViewLauncher.startWebViewInNewProcess(new_webapp, this);
        }

        if (wv != null) {
            wv.onResume();
            wv.resumeTimers();
            wv.evaluateJavascript("window._naRealVisibilityState = 'visible';", null);
        }
        MediaKeepAliveService.stop(this);
        bg_keepalive_handler.removeCallbacks(bg_keepalive_runnable);
        this.setDarkModeIfNeeded();
        if (webapp.isBiometricProtection()) {
            View fullActivityView = findViewById(R.id.webviewActivity);
            if (fullActivityView != null) fullActivityView.setVisibility(View.GONE);
            new BiometricPromptHelper(this).showPrompt(() -> {
                 if (fullActivityView != null) fullActivityView.setVisibility(View.VISIBLE);
            }, this::finish, getString(R.string.bioprompt_restricted_webapp));
        }
        if (webapp != null && webapp.isAutoreload()) {
            reload_handler = new Handler();
            reload();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        if (webapp != null && webapp.isAllowMediaPlaybackInBackground()) {
            startBackgroundPlayback();
        }
        super.onUserLeaveHint();
    }

    private void startBackgroundPlayback() {
        if (backgroundPlaybackStarted || webapp == null) return;
        backgroundPlaybackStarted = true;
        Log.d("NativeAlpha", "Starting background playback transition");
        if (wv != null) {
            wv.evaluateJavascript("window._naRealVisibilityState = 'hidden'; (function() { return !document.querySelectorAll('video, audio')[0]?.paused; })();", value -> {
                userWantsPlaying = "true".equals(value);
                if (userWantsPlaying) {
                    initMediaSession(); 
                    MediaKeepAliveService.start(this, webapp, mediaSession != null ? mediaSession.getSessionToken() : null);
                    bg_burst_count = 25;
                    bg_keepalive_handler.removeCallbacks(bg_keepalive_runnable);
                    bg_keepalive_runnable.run();
                }
            });
        }
    }

    private final Handler bg_keepalive_handler = new Handler();
    // ... (rest of bg_keepalive stuff)
    private int bg_burst_count = 0;
    private final Runnable bg_keepalive_runnable = new Runnable() {
        @Override
        public void run() {
            if (wv != null && webapp != null && webapp.isAllowMediaPlaybackInBackground()) {
                if (userWantsPlaying) {
                    wv.resumeTimers();
                    wv.evaluateJavascript("if (window._naWakeMedia) window._naWakeMedia(document);", null);
                }
                if (bg_burst_count > 0) { bg_burst_count--; bg_keepalive_handler.postDelayed(this, 200); }
                else bg_keepalive_handler.postDelayed(this, 30000);
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        if (webapp != null && !webapp.isAllowMediaPlaybackInBackground()) {
            if (wv != null) {
                wv.evaluateJavascript("document.querySelectorAll('audio, video').forEach(x => x.pause());", null);
                wv.onPause();
                wv.pauseTimers();
            }
        } else if (webapp != null && webapp.isAllowMediaPlaybackInBackground()) {
            startBackgroundPlayback();
        }
        if(mPopupMenu != null) mPopupMenu.dismiss();
        if (webapp != null && (webapp.isClearCache() || DataManager.getInstance().getSettings().isClearCache())) {
            if (wv != null) wv.clearCache(true);
        }
        if (reload_handler != null) reload_handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (webapp != null && webapp.isAllowMediaPlaybackInBackground() && wv != null) wv.resumeTimers();
    }

    @Override
    protected void onDestroy() {
        bg_keepalive_handler.removeCallbacks(bg_keepalive_runnable);
        MediaKeepAliveService.stop(this);
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); mediaSession = null; }
        if (mediaReceiver != null) { unregisterReceiver(mediaReceiver); mediaReceiver = null; }
        super.onDestroy();
    }

    private void reload() {
        if (reload_handler != null) reload_handler.postDelayed(() -> {
            currently_reloading = true;
            if (wv != null) wv.reload();
            reload();
        }, webapp.getTimeAutoreload() * 1000L);
    }

    // public WebView getWebView() { return wv; }

    private Map<String, String> initCustomHeaders(boolean save_data) {
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("DNT", "1");
        extraHeaders.put("X-REQUESTED-WITH", "");
        if (save_data) extraHeaders.put("Save-Data", "on");
        return Collections.unmodifiableMap(extraHeaders);
    }

    private void loadURL(final WebView view, final String url) {
        if (url == null || view == null) return;
        final WebApp webApp = DataManager.getInstance().getWebApp(webappID);
        if (url.contains("http://") && (webApp == null || !webApp.isAllowHttp())) {
            new MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog)
                .setTitle(R.string.no_https_dialog_title)
                .setMessage(R.string.no_https_dialog_msg)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.no_https_dialog_accept, (dialog, id) -> {
                    if (webApp != null) {
                        webApp.setAllowHttp(true);
                        webApp.setOverrideGlobalSettings(true);
                        DataManager.getInstance().saveWebAppData();
                    }
                    view.loadUrl(url, CUSTOM_HEADERS);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, id) -> finish())
                .show();
        } else view.loadUrl(url, CUSTOM_HEADERS);
    }
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if(controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void showSystemBars() {
        if(webapp != null && webapp.isShowFullscreen()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
    @FunctionalInterface interface PermissionGrantedCallback { void execute(); }

    private void enablePermissionBoolOnWebApp(PermissionGrantedCallback successCallback) {
        if (webapp == null) return;
        webapp.setOverrideGlobalSettings(true);
        successCallback.execute();
        DataManager.getInstance().replaceWebApp(webapp);
        if (wv != null) wv.reload();
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> list) {
        if (requestCode == Const.PERMISSION_RC_LOCATION) {
            enablePermissionBoolOnWebApp(() -> webapp.setAllowLocationAccess(true));
            handleGeoPermissionCallback(true);
        }
        if (requestCode == Const.PERMISSION_CAMERA) enablePermissionBoolOnWebApp(() -> webapp.setCameraPermission(true));
        if (requestCode == Const.PERMISSION_RC_STORAGE && dl_request != null) {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) { dm.enqueue(dl_request); NotificationUtils.showInfoSnackbar(this, getString(R.string.file_download), Snackbar.LENGTH_SHORT); }
            dl_request = null;
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> list) {
        if (requestCode == Const.PERMISSION_RC_LOCATION) handleGeoPermissionCallback(false);
    }

    private void handleGeoPermissionCallback(boolean allow) {
        if (mGeoPermissionRequestCallback != null) {
            mGeoPermissionRequestCallback.invoke(mGeoPermissionRequestOrigin, allow, false);
            mGeoPermissionRequestCallback = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == CODE_OPEN_FILE && filePathCallback != null) {
            filePathCallback.onReceiveValue(resultCode == RESULT_OK ? WebChromeClient.FileChooserParams.parseResult(resultCode, intent) : null);
            filePathCallback = null;
        }
    }

    private class CustomWebChromeClient extends android.webkit.WebChromeClient {
        private View mCustomView;
        private WebChromeClient.CustomViewCallback mCustomViewCallback;
        private int mOriginalOrientation, mOriginalSystemUiVisibility;

        private void handlePermissionRequest(String resId, boolean currentState, String[] androidPermissions, int requestCode, List<String> permissionsToGrant, String[] webkitPermission, PermissionGrantedCallback successCallback) {
            boolean androidPermissionsMissing = !EasyPermissions.hasPermissions(WebViewActivity.this, androidPermissions);
            if (currentState && androidPermissionsMissing) { ActivityCompat.requestPermissions(WebViewActivity.this, androidPermissions, requestCode); return; }
            if (currentState && !androidPermissionsMissing) { permissionsToGrant.addAll(Arrays.asList(webkitPermission)); handleGeoPermissionCallback(true); return; }
            new MaterialAlertDialogBuilder(WebViewActivity.this, R.style.AppTheme_AlertDialog).setTitle(getPermissionRequestStringResource("dialog_permission_", resId, "_title")).setMessage(getPermissionRequestStringResource("dialog_permission_", resId, "_txt")).setPositiveButton(android.R.string.yes, (dialog, id) -> {
                enablePermissionBoolOnWebApp(successCallback); handleGeoPermissionCallback(true); permissionsToGrant.addAll(Arrays.asList(webkitPermission));
                if (androidPermissionsMissing) ActivityCompat.requestPermissions(WebViewActivity.this, androidPermissions, requestCode);
            }).setNegativeButton(android.R.string.no, (dialog, id) -> handleGeoPermissionCallback(false)).create().show();
        }

        private String getPermissionRequestStringResource(String prefix, String variable, String suffix) {
            int id = getResources().getIdentifier(prefix + variable + suffix, "string", getPackageName());
            return id != 0 ? getString(id) : "";
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> pFilePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
            filePathCallback = pFilePathCallback;
            try { fileChooserLauncher.launch(fileChooserParams.createIntent()); }
            catch (Exception e) { NotificationUtils.showInfoSnackbar(WebViewActivity.this, getString(R.string.no_filemanager), Snackbar.LENGTH_LONG); }
            return true;
        }

        @Override
        public Bitmap getDefaultVideoPoster() {
            Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            new Canvas(bitmap).drawARGB(0, 0, 0, 0);
            return bitmap;
        }

        public void onHideCustomView() {
            ((FrameLayout) getWindow().getDecorView()).removeView(mCustomView);
            mCustomView = null;
            getWindow().getDecorView().setSystemUiVisibility(mOriginalSystemUiVisibility);
            setRequestedOrientation(mOriginalOrientation);
            if (mCustomViewCallback != null) mCustomViewCallback.onCustomViewHidden();
            mCustomViewCallback = null;
            showSystemBars();
        }

        public void onShowCustomView(View pView, WebChromeClient.CustomViewCallback pViewCallback) {
            if (mCustomView != null) { onHideCustomView(); return; }
            mCustomView = pView;
            mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            mOriginalOrientation = getRequestedOrientation();
            mCustomViewCallback = pViewCallback;
            ((FrameLayout) getWindow().getDecorView()).addView(mCustomView, new FrameLayout.LayoutParams(-1, -1));
            hideSystemBars();
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            List<String> permissionsToGrant = new ArrayList<>();
            List<String> resources = Arrays.asList(request.getResources());
            if (resources.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) handlePermissionRequest("drm", webapp.isDrmAllowed(), new String[]{}, -1, permissionsToGrant, new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID}, () -> webapp.setDrmAllowed(true));
            if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) handlePermissionRequest("camera", webapp.isCameraPermission(), new String[]{Manifest.permission.CAMERA}, Const.PERMISSION_CAMERA, permissionsToGrant, new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}, () -> webapp.setCameraPermission(true));
            if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) handlePermissionRequest("microphone", webapp.isMicrophonePermission(), new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS}, Const.PERMISSION_AUDIO, permissionsToGrant, new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE}, () -> webapp.setMicrophonePermission(true));
            request.grant(permissionsToGrant.toArray(new String[0]));
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            super.onReceivedIcon(view, icon);
            if (icon != null && webapp != null && ShortcutIconUtils.getIcon(WebViewActivity.this, webapp) == null) {
                ShortcutIconUtils.saveIcon(WebViewActivity.this, webapp.getID(), icon);
            }
        }

        public void onProgressChanged(WebView view, int progress) {
            if (DataManager.getInstance().getSettings().isShowProgressbar() || currently_reloading) {
                if (progressBar != null) {
                    if (progressBar.getVisibility() == ProgressBar.GONE && progress < 100) progressBar.setVisibility(ProgressBar.VISIBLE);
                    progressBar.setProgress(progress);
                    if (progress == 100) { progressBar.setVisibility(ProgressBar.GONE); currently_reloading = false; }
                }
            }
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            Log.d("NativeAlphaJS", consoleMessage.message() + " -- line " + consoleMessage.lineNumber());
            return true;
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            mGeoPermissionRequestCallback = callback;
            mGeoPermissionRequestOrigin = origin;
            handlePermissionRequest("location", webapp.isAllowLocationAccess(), new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, Const.PERMISSION_RC_LOCATION, new ArrayList<>(), new String[]{}, () -> webapp.setAllowLocationAccess(true));
        }
    }

    private void showHttpAuthDialog(final HttpAuthHandler handler, String host, String realm) {
        DialogHttpAuthBinding binding = DialogHttpAuthBinding.inflate(LayoutInflater.from(this));
        new MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog).setView(binding.getRoot()).setTitle(R.string.http_auth_title).setMessage(getString(R.string.enter_http_auth_credentials, realm, host)).setPositiveButton(R.string.ok, (dialog, whichButton) -> handler.proceed(binding.username.getText().toString(), binding.password.getText().toString())).setNegativeButton(R.string.cancel, (dialog, whichButton) -> handler.cancel()).show();
    }
    
    private void applySiteRules(WebView view, String url) {
        if (webapp == null) return;
        if (webapp.isAllowMediaPlaybackInBackground()) {
            String bgJs = """
                (function() {
                  var block = function(e) { e.stopImmediatePropagation(); };
                  try {
                    var getter = {get: function() { return 'visible'; }, configurable: true};
                    var hiddenGetter = {get: function() { return false; }, configurable: true};
                    Object.defineProperty(document, 'visibilityState', getter);
                    Object.defineProperty(document, 'webkitVisibilityState', getter);
                    Object.defineProperty(document, 'hidden', hiddenGetter);
                    Object.defineProperty(document, 'webkitHidden', hiddenGetter);
                    Object.defineProperty(document, 'hasFocus', {get: function() { return function() { return true; }; }, configurable: true});
                  } catch (e) { }
                  
                  window.addEventListener('visibilitychange', block, true);
                  window.addEventListener('webkitvisibilitychange', block, true);
                  window.addEventListener('blur', block, true);
                  window.addEventListener('focus', block, true);
                  window.addEventListener('pagehide', block, true);
                  
                  if (window.AudioContext) AudioContext.prototype.suspend = function() { return Promise.resolve(); };
                  if (window.webkitAudioContext) webkitAudioContext.prototype.suspend = function() { return Promise.resolve(); };
                  
                  window._naWakeMedia = function(root) {
                    root.querySelectorAll('video, audio').forEach(function(m) {
                      if (m.paused && m.duration > 0 && !m.ended && m.readyState > 0) m.play().catch(() => {});
                      if (m.tagName === 'VIDEO' && m.style.display === 'none') m.style.display = 'block';
                    });
                    var all = root.querySelectorAll('*');
                    for (var i = 0; i < all.length; i++) if (all[i].shadowRoot) window._naWakeMedia(all[i].shadowRoot);
                  };
                  
                  document.addEventListener('pause', function(e) {
                    if (window._naUserWantsPlaying && window._naRealVisibilityState === 'hidden') {
                      window._naWakeMedia(document);
                    }
                  }, true);
                  
                  window._naRealVisibilityState = 'visible';
                  window._naUserWantsPlaying = (function() { var m = document.querySelectorAll('video, audio'); for(var i=0; i<m.length; i++) if(!m[i].paused) return true; return false; })();
                  
                  function syncState() {
                    var isPlaying = false; var media = document.querySelectorAll('video, audio');
                    for (var i = 0; i < media.length; i++) { if (!media[i].paused) isPlaying = true; }
                    if (window._naRealVisibilityState === 'visible') {
                      window._naUserWantsPlaying = isPlaying;
                      if (window.NativeAlpha) window.NativeAlpha.setPlaybackState(isPlaying);
                    }
                    var title = document.title.replace(' - YouTube', ''); var artist = 'Native Alpha++';
                    if (location.hostname.includes('youtube.com')) {
                      artist = 'YouTube'; var channel = document.querySelector('#upload-info .ytd-channel-name a, .ytp-ce-channel-title, [itemprop="author"] [itemprop="name"]');
                      if (channel) artist = channel.innerText || channel.content;
                    }
                    if (window.NativeAlpha && window.NativeAlpha.updateMetadata) window.NativeAlpha.updateMetadata(title, artist);
                  }
                  window.addEventListener('play', syncState, true);
                  window.addEventListener('pause', syncState, true);
                })();""";
            view.evaluateJavascript(bgJs, null);
        }
        try {
            JSONObject site = SiteConfigManager.INSTANCE.getSiteConfig(this, url);
            if (site == null) return;
            StringBuilder cssToInject = new StringBuilder();
            JSONArray remove = site.optJSONArray("remove");
            if (remove != null && remove.length() > 0) {
                for (int j = 0; j < remove.length(); j++) {
                    if (j > 0) cssToInject.append(",");
                    cssToInject.append(remove.getString(j));
                }
                cssToInject.append(" { display: none !important; visibility: hidden !important; pointer-events: none !important; } ");
            }
            JSONArray css = site.optJSONArray("injectCss");
            if (css != null) for (int j = 0; j < css.length(); j++) cssToInject.append(css.getString(j));
            if (!cssToInject.toString().isEmpty()) {
                String js = "(function() { var style = document.getElementById('na-site-rules') || document.createElement('style'); style.id = 'na-site-rules'; style.textContent = " + JSONObject.quote(cssToInject.toString()) + "; var parent = document.head || document.documentElement; if (parent && !style.parentElement) parent.appendChild(style); })();";
                view.evaluateJavascript(js, null);
            }
            if (remove != null && remove.length() > 0 && !"onPageStarted".equals(new Throwable().getStackTrace()[1].getMethodName())) {
                StringBuilder removeJs = new StringBuilder("(function(){");
                for (int j = 0; j < remove.length(); j++) removeJs.append("document.querySelectorAll(").append(JSONObject.quote(remove.getString(j))).append(").forEach(function(e){e.remove();});");
                removeJs.append("})();");
                view.evaluateJavascript(removeJs.toString(), null);
            }
            JSONArray jsArray = site.optJSONArray("injectJs");
            if (jsArray != null) {
                for (int j = 0; j < jsArray.length(); j++) {
                    view.evaluateJavascript(jsArray.getString(j), null);
                }
            }
        } catch (Exception e) {
            Log.e("NativeAlpha", "applySiteRules error", e);
        }
    }

    private class CustomBrowser extends WebViewClient {
        @Override public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) { showHttpAuthDialog(handler, host, realm); }
        @Override public void onPageCommitVisible(WebView view, String url) {
            applySiteRules(view, url);
            JSONObject site = SiteConfigManager.INSTANCE.getSiteConfig(WebViewActivity.this, url);
            if (site != null) {
                JSONArray jsArray = site.optJSONArray("injectJs");
                if (jsArray != null) {
                    for (int j = 0; j < jsArray.length(); j++) {
                        try {
                            view.evaluateJavascript(jsArray.getString(j), null);
                        } catch (Exception ignored) {}
                    }
                }
            }
            super.onPageCommitVisible(view, url);
        }
        @Override public void onPageFinished(WebView view, String url) {
            applySiteRules(view, url);
            if ("about:blank".equals(url)) {
                String lang = LocaleUtils.getFileEnding();
                if (wv != null) wv.loadUrl("file:///android_asset/errorSite/error_" + lang + ".html");
            }
            super.onPageFinished(view, url);
        }
        @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
            try {
                JSONObject site = SiteConfigManager.INSTANCE.getSiteConfig(WebViewActivity.this, url);
                if (site != null) {
                    if (site.has("userAgent")) view.getSettings().setUserAgentString(site.getString("userAgent"));
                    if (site.optBoolean("allowBackgroundPlayback", false)) {
                        if (webapp != null) webapp.setAllowMediaPlaybackInBackground(true);
                    }
                    JSONArray jsArray = site.optJSONArray("injectJs");
                    if (jsArray != null) {
                        for (int j = 0; j < jsArray.length(); j++) {
                            view.evaluateJavascript(jsArray.getString(j), null);
                        }
                    }
                }
            } catch (Exception ignored) {}
            applySiteSystemBars(url);
            applySiteRules(view, url);
            super.onPageStarted(view, url, favicon);
        }
        @Nullable @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if(urlOnFirstPageload.isEmpty()) urlOnFirstPageload = request.getUrl().toString();
            if (webapp != null && webapp.isBlockThirdPartyRequests()) {
                Uri uri = request.getUrl();
                Uri webapp_uri = Uri.parse(webapp.getBaseUrl());
                String webappHost = webapp_uri.getHost();
                if(uri.getHost() != null && webappHost != null && !uri.getHost().endsWith(webappHost)) return new WebResourceResponse("text/plain", "utf-8", null);
            }
            return super.shouldInterceptRequest(view, request);
        }
        @SuppressLint("WebViewClientOnReceivedSslError")
        @Override public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
            if (webapp != null && webapp.isIgnoreSslErrors()) { handler.proceed(); return; }
            String msg = getString(R.string.ssl_error_msg_line1) + " ";
            switch (error.getPrimaryError()) {
                case SslError.SSL_UNTRUSTED: msg += getString(R.string.ssl_error_unknown_authority) + "\n"; break;
                case SslError.SSL_EXPIRED: msg += getString(R.string.ssl_error_expired) + "\n"; break;
                case SslError.SSL_IDMISMATCH: msg += getString(R.string.ssl_error_id_mismatch) + "\n"; break;
                case SslError.SSL_NOTYETVALID: msg += getString(R.string.ssl_error_notyetvalid) + "\n"; break;
            }
            msg += getString(R.string.ssl_error_msg_line2) + "\n";
            final AlertDialog dialog = new MaterialAlertDialogBuilder(WebViewActivity.this, R.style.AppTheme_AlertDialog).setTitle(R.string.ssl_error_title).setMessage(msg).setIcon(android.R.drawable.ic_dialog_alert).setPositiveButton(android.R.string.cancel, (d, id) -> handler.cancel()).setNegativeButton(R.string.load_anyway, (d, id) -> handler.proceed()).show();
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(WebViewActivity.this, android.R.color.holo_red_dark));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(WebViewActivity.this, android.R.color.holo_green_dark));
        }
        @Override public void onLoadResource(WebView view, String url) {
            super.onLoadResource(view, url);
            if (webapp != null && webapp.isRequestDesktop())
               view.evaluateJavascript("var needsWidth = document.documentElement.clientWidth < 1200; if(needsWidth) { var meta = document.querySelector('meta[name=\"viewport\"]'); if (meta) meta.setAttribute('content', 'width=1200px, initial-scale=' + (document.documentElement.clientWidth / 1200)); }", null);
            view.evaluateJavascript("document.addEventListener('visibilitychange', (e) => { e.stopImmediatePropagation(); }, true);", null);
        }
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            runOnUiThread(WebViewActivity.this::setDarkModeIfNeeded);
            String url = request.getUrl().toString();
            if (url.startsWith("tel:")) { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(url))); return true; }
            if (url.startsWith("mailto:")) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); return true; }
            if (webapp != null && webapp.isOpenUrlExternal()) {
                String host = Uri.parse(webapp.getBaseUrl()).getHost();
                if (host != null && !url.contains(host)) { view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); return true; }
            }
            loadURL(view, url);
            return true;
        }
    }

    public class WebAppInterface {
        @JavascriptInterface public void setPlaybackState(boolean playing) { 
            Log.d("NativeAlpha", "JS playback: " + playing); 
            userWantsPlaying = playing; 
            runOnUiThread(() -> {
                updatePlaybackState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
                if (playing && webapp != null && webapp.isAllowMediaPlaybackInBackground()) initMediaSession();
            }); 
        }
        @JavascriptInterface public void updateMetadata(String title, String artist) { runOnUiThread(() -> {
                if (mediaSession != null) {
                    mediaSession.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).putString(MediaMetadata.METADATA_KEY_ARTIST, artist).build());
                    if (webapp != null && userWantsPlaying) MediaKeepAliveService.start(WebViewActivity.this, webapp, mediaSession.getSessionToken());
                }
            });
        }
    }
}
