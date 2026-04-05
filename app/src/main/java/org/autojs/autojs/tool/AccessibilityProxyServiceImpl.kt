package org.autojs.autojs.tool

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.stardust.autojs.core.accessibility.AccessibilityProxyService
import com.stardust.autojs.core.accessibility.IAccessibilityProxyService

class AccessibilityProxyServiceImpl : Service() {

    companion object {
        private const val TAG = "AccessibilityProxyService"
    }

    private var proxyService: IAccessibilityProxyService? = null
    private var isProxyBound = false

    private val proxyConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            proxyService = IAccessibilityProxyService.Stub.asInterface(binder)
            isProxyBound = true
            Log.d(TAG, "绑定到 AccessibilityProxyService 成功")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            proxyService = null
            isProxyBound = false
            Log.d(TAG, "AccessibilityProxyService 连接断开")
        }
    }

    private fun bindToAccessibilityProxy() {
        try {
            val intent = Intent(this, AccessibilityProxyService::class.java)
            intent.setPackage(packageName)
            bindService(intent, proxyConnection, BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "绑定到 AccessibilityProxyService 失败", e)
        }
    }

    private val binder = object : IAccessibilityProxyService.Stub() {
        override fun isEnabled(): Boolean {
            return try {
                val enabled = proxyService?.isEnabled() ?: false
                Log.d(TAG, "isEnabled: $enabled")
                enabled
            } catch (e: RemoteException) {
                Log.e(TAG, "检查无障碍服务状态失败", e)
                false
            }
        }

        override fun ensureEnabled(): Boolean {
            return try {
                val result = proxyService?.ensureEnabled() ?: false
                Log.d(TAG, "enable: $result")
                result
            } catch (e: RemoteException) {
                Log.e(TAG, "启用无障碍服务失败", e)
                false
            }
        }

        override fun disable(): Boolean {
            return try {
                val result = proxyService?.disable() ?: false
                Log.d(TAG, "disable: $result")
                result
            } catch (e: RemoteException) {
                Log.e(TAG, "禁用无障碍服务失败", e)
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AccessibilityProxyServiceImpl onCreate")
        bindToAccessibilityProxy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "收到启动命令")
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "收到绑定请求，返回 IAccessibilityProxyService 接口")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "服务解绑")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isProxyBound) {
            unbindService(proxyConnection)
        }
        Log.d(TAG, "AccessibilityProxyServiceImpl 销毁")
    }
}