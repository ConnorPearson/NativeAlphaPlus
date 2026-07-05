package com.cylonid.nativealpha.util

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import android.support.v4.media.session.MediaSessionCompat
import com.cylonid.nativealpha.R
import android.util.Log

class MediaKeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "media_keep_alive"
        private const val NOTIFICATION_ID = 99

        @JvmStatic
        fun start(context: Context, webApp: com.cylonid.nativealpha.model.WebApp, token: android.media.session.MediaSession.Token? = null) {
            val intent = Intent(context, MediaKeepAliveService::class.java).apply {
                putExtra("title", webApp.title)
                putExtra("webapp_id", webApp.ID)
                putExtra("webapp_url", webApp.baseUrl)
                putExtra("activity_class", context.javaClass.name)
                if (token != null) {
                    putExtra("session_token", token)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        @Deprecated("Use start with WebApp object")
        fun start(context: Context, webAppTitle: String, token: android.media.session.MediaSession.Token? = null) {
            val intent = Intent(context, MediaKeepAliveService::class.java).apply {
                putExtra("title", webAppTitle)
                putExtra("activity_class", context.javaClass.name)
                if (token != null) {
                    putExtra("session_token", token)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            context.stopService(Intent(context, MediaKeepAliveService::class.java))
        }
    }

    private var audioTrack: android.media.AudioTrack? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title") ?: "Web App"
        val webAppId = intent?.getIntExtra("webapp_id", -1) ?: -1
        val webAppUrl = intent?.getStringExtra("webapp_url") ?: ""
        val sessionToken = intent?.getParcelableExtra<android.media.session.MediaSession.Token>("session_token")
        val activityClassName = intent?.getStringExtra("activity_class")
        
        Log.d("NativeAlpha", "MediaKeepAliveService onStartCommand for $title, token exists: ${sessionToken != null}")
        createNotificationChannel()

        // 1. Create basic notification and call startForeground IMMEDIATELY to satisfy OS
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Background playback active")
            .setSmallIcon(R.drawable.native_alpha_white_foreground)
            .setOngoing(true)
            .setSilent(true) // Don't beep every time metadata updates
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Try to load the WebApp icon for the notification
        if (webAppId != -1 && webAppUrl.isNotEmpty()) {
            val icon = ShortcutIconUtils.getIcon(this, webAppId, webAppUrl)
            if (icon != null) {
                notificationBuilder.setLargeIcon(icon)
            }
        }

        // Set content intent to open the specific activity
        if (activityClassName != null) {
            try {
                val activityClass = Class.forName(activityClassName)
                val contentIntent = Intent(this, activityClass).apply {
                    action = Intent.ACTION_VIEW
                    data = android.net.Uri.parse(webAppUrl + webAppId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    putExtra(Const.INTENT_WEBAPPID, webAppId)
                }
                val contentPendingIntent = PendingIntent.getActivity(
                    this, webAppId, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                notificationBuilder.setContentIntent(contentPendingIntent)
            } catch (e: Exception) {
                Log.e("NativeAlpha", "Failed to create content intent for $activityClassName", e)
            }
        }

        if (sessionToken != null) {
            val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(MediaSessionCompat.Token.fromToken(sessionToken))
                .setShowActionsInCompactView(0, 1)
            
            notificationBuilder.setStyle(mediaStyle)
            
            val playIntent = Intent(this, MediaKeepAliveService::class.java).apply { action = "PLAY" }
            val pauseIntent = Intent(this, MediaKeepAliveService::class.java).apply { action = "PAUSE" }
            
            val playPendingIntent = PendingIntent.getService(this, 10, playIntent, PendingIntent.FLAG_IMMUTABLE)
            val pausePendingIntent = PendingIntent.getService(this, 11, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
            
            notificationBuilder.addAction(android.R.drawable.ic_media_play, "Play", playPendingIntent)
            notificationBuilder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
        }

        val notification = notificationBuilder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 2. Now handle other logic
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NativeAlpha::KeepAlive")
            wakeLock?.acquire(24 * 60 * 60 * 1000L /* 24 hours */)
        }

        startSilentAudio()

        // Handle button clicks if this onStartCommand was triggered by an action
        when (intent?.action) {
            "PLAY" -> {
                Log.d("NativeAlpha", "Notification PLAY clicked")
                sendBroadcast(Intent("com.cylonid.nativealpha.ACTION_PLAY"))
            }
            "PAUSE" -> {
                Log.d("NativeAlpha", "Notification PAUSE clicked")
                sendBroadcast(Intent("com.cylonid.nativealpha.ACTION_PAUSE"))
            }
        }
        
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Background Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startSilentAudio() {
        if (audioTrack != null) return
        
        try {
            val sampleRate = 44100
            val minBufferSize = android.media.AudioTrack.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = android.media.AudioTrack(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                android.media.AudioFormat.Builder()
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .build(),
                minBufferSize,
                android.media.AudioTrack.MODE_STREAM,
                0
            )

            val silentData = ShortArray(minBufferSize)
            audioTrack?.play()
            
            // Continuous silent loop in a thread
            Thread {
                try {
                    while (true) {
                        val track = audioTrack ?: break
                        if (track.playState != android.media.AudioTrack.PLAYSTATE_PLAYING) break
                        val result = track.write(silentData, 0, silentData.size)
                        if (result < 0) break
                        Thread.sleep(100)
                    }
                } catch (e: Exception) {
                    Log.e("NativeAlpha", "Silent audio loop stopped", e)
                }
            }.start()
            
            Log.d("NativeAlpha", "Started silent audio track loop")
        } catch (e: Exception) {
            Log.e("NativeAlpha", "Error starting silent audio", e)
        }
    }

    private fun stopSilentAudio() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            Log.d("NativeAlpha", "Stopped silent audio track")
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        Log.d("NativeAlpha", "MediaKeepAliveService onDestroy")
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        stopSilentAudio()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
