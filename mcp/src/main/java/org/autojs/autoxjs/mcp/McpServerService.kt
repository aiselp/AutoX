package org.autojs.autoxjs.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager

class McpServerService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: SharedPreferences
    private lateinit var mcpService: McpService
    @Volatile
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        startForegroundIfNeeded()
        @Suppress("DEPRECATION")
        prefs = getSharedPreferences(
            packageName + "_preferences",
            Context.MODE_MULTI_PROCESS
        )
        prefs.registerOnSharedPreferenceChangeListener(this)
        mcpService = McpService(this)
        applyConfig()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundIfNeeded()
        applyConfig()
        return START_STICKY
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        mcpService.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key in MCP_KEYS) {
            applyConfig()
        }
    }

    private fun applyConfig() {
        val config = McpPrefs.load(this)
        if (config.enabled) {
            startForegroundIfNeeded()
            mcpService.start(config)
        } else {
            mcpService.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
            stopSelf()
        }
    }

    private fun startForegroundIfNeeded() {
        if (foregroundStarted) {
            return
        }
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        foregroundStarted = true
    }

    private fun buildNotification(): Notification {
        val channelId = ensureChannel()
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.mcp_notification_title))
            .setContentText(getString(R.string.mcp_notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel(): String {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                channelId,
                getString(R.string.mcp_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = getString(R.string.mcp_notification_channel_desc)
            manager.createNotificationChannel(channel)
        }
        return channelId
    }

    companion object {
        private const val CHANNEL_ID = "mcp_server"
        private const val NOTIFICATION_ID = 27190

        private val MCP_KEYS = setOf(
            McpPrefKeys.KEY_ENABLED,
            McpPrefKeys.KEY_HOST,
            McpPrefKeys.KEY_PORT,
            McpPrefKeys.KEY_TOKEN,
            McpPrefKeys.KEY_ALLOW_BASE64,
            McpPrefKeys.KEY_ALLOW_NETWORK
        )

        fun start(context: Context) {
            val intent = Intent(context, McpServerService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, McpServerService::class.java)
            context.stopService(intent)
        }
    }
}
