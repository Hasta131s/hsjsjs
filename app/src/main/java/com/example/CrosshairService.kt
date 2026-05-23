package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class CrosshairService : Service() {

    companion object {
        const val CHANNEL_ID = "tvnah_cross_channel"
        const val NOTIFICATION_ID = 1907
        const val ACTION_STOP_SERVICE = "com.example.ACTION_STOP_SERVICE"
        
        @Volatile
        var isServiceRunning = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var crosshairView: CrosshairView
    private lateinit var sharedPrefs: SharedPreferences

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (::crosshairView.isInitialized) {
            updateViewSettings()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sharedPrefs = getSharedPreferences("tvnah_cross_prefs", Context.MODE_PRIVATE)
        sharedPrefs.registerOnSharedPreferenceChangeListener(preferenceListener)

        createNotificationChannel()
        startForegroundServiceWithNotification()

        setupOverlay()
    }

    private fun setupOverlay() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        crosshairView = CrosshairView(this)
        updateViewSettings()

        try {
            windowManager.addView(crosshairView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun updateViewSettings() {
        crosshairView.shape = sharedPrefs.getString("shape", "cross") ?: "cross"
        crosshairView.crosshairSize = sharedPrefs.getInt("size", 40)
        crosshairView.crosshairColor = sharedPrefs.getInt("color", 0xFF00FF00.toInt())
        crosshairView.thickness = sharedPrefs.getFloat("thickness", 3f)
        crosshairView.opacity = sharedPrefs.getFloat("opacity", 1.0f)
        crosshairView.gap = sharedPrefs.getInt("gap", 4)
        crosshairView.outline = sharedPrefs.getBoolean("outline", true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TVNAH CROSS Arka Plan Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Nişangah ekranının üstünde kalmasını sağlar."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceWithNotification() {
        val stopIntent = Intent(this, CrosshairService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Using a built-in compass drawable as standard icon
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TVNAH CROSS Aktif")
            .setContentText("Nişangahınız ekranın tam ortasında gösteriliyor.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Kapat", // Close button requested by user
                stopPendingIntent
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isServiceRunning = false
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        
        if (::crosshairView.isInitialized) {
            try {
                windowManager.removeView(crosshairView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
