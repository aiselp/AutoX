package com.stardust.autojs.core.image.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.stardust.app.service.AbstractAutoService
import com.stardust.autojs.R
import java.lang.ref.WeakReference

/**
 * Created by TonyJiangWJ(https://github.com/TonyJiangWJ).
 * From [TonyJiangWJ/Auto.js](https://github.com/TonyJiangWJ/Auto.js)
 */
class CaptureForegroundService : AbstractAutoService() {

    private class ServiceCallback(service: CaptureForegroundService) : MediaProjection.Callback() {
        private val serviceRef = WeakReference(service)

        override fun onStop() {
            serviceRef.get()?.stopServiceInternal()
        }
    }

    private val callback = ServiceCallback(this)

    private lateinit var weakService: WeakReference<CaptureForegroundService>

    override fun onCreate() {
        super.onCreate()
        weakService = WeakReference(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else 0
        )
    }

    override fun onBind(intent: Intent?): IBinder {
        return object : Binder() {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            STOP -> stopServiceInternal()
            REGISTER -> {
                getMediaProjection()?.unregisterCallback(callback)
                // 使用弱引用避免 MediaProjection 持有 Service
                getMediaProjection()?.registerCallback(callback, Handler(mainLooper))
            }
            UNREGISTER -> {
                getMediaProjection()?.unregisterCallback(callback)
            }
        }
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        createNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle(NOTIFICATION_TITLE)
            .setSmallIcon(R.drawable.autojs_logo).setWhen(System.currentTimeMillis())
            .addAction(createExitAction()).setChannelId(CHANNEL_ID)
            .setVibrate(LongArray(0)).setOngoing(false).build()
    }

    private fun createNotificationChannel() {
        val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        val channel = NotificationChannel(
            CHANNEL_ID, NOTIFICATION_TITLE, NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = NOTIFICATION_TITLE
        channel.enableLights(false)
        manager.createNotificationChannel(channel)
    }

    private fun createExitAction(): NotificationCompat.Action {
        val pendingIntent = PendingIntent.getService(
            this, 12, Intent(this, CaptureForegroundService::class.java).apply {
                action = STOP
            }, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            null, "停止截图", pendingIntent
        ).build()
    }

    private fun removeNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消注册回调
        getMediaProjection()?.unregisterCallback(callback)
        // 停止并清除 MediaProjection
        clearMediaProjection()
        removeNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private var mediaProjectionRef: WeakReference<MediaProjection>? = null

        private fun getMediaProjection(): MediaProjection? {
            return mediaProjectionRef?.get()
        }

        private fun clearMediaProjection() {
            mediaProjectionRef?.get()?.stop()
            mediaProjectionRef = null
        }

        fun setMediaProjection(context: Context, media: MediaProjection) {
            // 清理旧的引用
            clearMediaProjection()
            // 使用弱引用存储
            mediaProjectionRef = WeakReference(media)
            val intent = Intent(context, CaptureForegroundService::class.java).apply {
                action = REGISTER
            }
            context.startForegroundService(intent)
        }

        private const val STOP = "STOP_SERVICE"
        private const val REGISTER = "REGISTER_CALLBACK"
        private const val UNREGISTER = "UNREGISTER_CALLBACK"
        private const val NOTIFICATION_ID = 26
        private val CHANNEL_ID = CaptureForegroundService::class.java.name + ".foreground"
        private const val NOTIFICATION_TITLE = "截图服务运行中"
    }
}