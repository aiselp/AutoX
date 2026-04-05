package com.stardust.autojs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.github.aiselp.autox.api.TermuxApi
import com.stardust.app.service.AbstractAutoService
import com.stardust.autojs.core.pref.Pref
import com.stardust.autojs.servicecomponents.ScriptBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

class IndependentScriptService : AbstractAutoService() {
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        Log.i(TAG, "Pid: ${Process.myPid()}")
        if (Pref.isForegroundServiceEnabled) {
            startForeground()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
    }

    private fun startForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )
        isForegroundRunning = true
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val name: CharSequence = "AutoJS Service"
        val description = "script foreground service"
        val channel = NotificationChannel(
            CHANEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = description
        channel.enableLights(false)
        manager.createNotificationChannel(channel)

        // 设置启动意图
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANEL_ID)
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText(getString(R.string.foreground_notification_text))
            .setSmallIcon(R.drawable.autojs_logo)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(contentIntent)
            .setChannelId(CHANEL_ID)
            .setVibrate(LongArray(0))
            .setOngoing(true)

        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            TermuxApi.onHandleIntent(intent)
        }
        val action = intent?.action
        when (action) {
            ACTION_START_FOREGROUND -> startForeground()
            ACTION_STOP_FOREGROUND -> {
                isForegroundRunning = false
                stopServiceInternal()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        isForegroundRunning = false
        scope.cancel()
        super.onDestroy()
        Log.i(TAG, "IndependentScriptService Service destroyed")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
    }

    override fun onBind(intent: Intent?): IBinder {
        return ScriptBinder(this, scope)
    }

    companion object {
        private const val TAG = "ScriptService"
        private const val NOTIFICATION_ID = 25

        private val CHANEL_ID = IndependentScriptService::class.java.name + "_foreground"
        const val ACTION_START_FOREGROUND = "action_start_foreground"
        const val ACTION_STOP_FOREGROUND = "action_stop_foreground"
        @Volatile
        var isForegroundRunning = false
            private set

        fun startForeground(context: Context) {
            val intent = Intent(context, IndependentScriptService::class.java).apply {
                action = ACTION_START_FOREGROUND
            }
            context.startForegroundService(intent)
        }

        fun stopForeground(context: Context) {
            val intent = Intent(context, IndependentScriptService::class.java).apply {
                action = ACTION_STOP_FOREGROUND
            }
            context.startService(intent)
        }
    }
}