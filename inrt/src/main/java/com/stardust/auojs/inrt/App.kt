package com.stardust.auojs.inrt

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aiselp.autox.ui.material3.activity.ErrorReportActivity
import com.linsh.utilseverywhere.Utils
import com.stardust.app.GlobalAppContext
import com.stardust.auojs.inrt.autojs.AutoJs
import com.stardust.auojs.inrt.autojs.GlobalKeyObserver
import org.autojs.autoxjs.inrt.BuildConfig
import org.autojs.autoxjs.inrt.R


/**
 * Created by Stardust on 2017/7/1.
 */

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        GlobalAppContext.set(
            this, com.stardust.app.BuildConfig.generate(BuildConfig::class.java)
        )
        Utils.init(this);
        AutoJs.initInstance(this)
        GlobalKeyObserver.init()

        ErrorReportActivity.install(this, SplashActivity::class.java)
        if (BuildConfig.isMarket) {
            showNotification(this);
        }
    }

    private fun showNotification(context: Context) {
        val manager: NotificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val builder: Notification.Builder = Notification.Builder(context)
        builder.setWhen(System.currentTimeMillis())
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(GlobalAppContext.appName + "保持运行中")
            .setContentText("点击打开【" + GlobalAppContext.appName + "】")
            .setDefaults(NotificationCompat.FLAG_ONGOING_EVENT)
            .setPriority(Notification.PRIORITY_MAX)
        //SDK版本>=21才能设置悬挂式通知栏
        builder.setCategory(Notification.FLAG_ONGOING_EVENT.toString())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        val intent = Intent(context, SplashActivity::class.java)
        val pi =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(pi)
        manager.notify(null, 0, builder.build())
    }

    companion object {
        private const val TAG = "inrt.application";
    }
}
