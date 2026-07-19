package com.stardust.autojs.core.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.stardust.app.GlobalAppContext.get
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class AccessibilityProxyAccessor private constructor() {

    companion object {
        private const val TAG = "AccessibilityProxyAccessor"

        @Volatile
        private var instance: AccessibilityProxyAccessor? = null

        @JvmStatic
        fun getInstance(): AccessibilityProxyAccessor {
            return instance ?: synchronized(this) {
                instance ?: AccessibilityProxyAccessor().also { instance = it }
            }
        }
    }

    private var service: IAccessibilityProxyService? = null
    private var isBound = false
    private var bindingInProgress = false
    private var serviceReady = CompletableFuture<Boolean>()

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IAccessibilityProxyService.Stub.asInterface(binder)
            isBound = true
            serviceReady.complete(true)
            Log.d(TAG, "无障碍代理AIDL服务连接成功")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
            serviceReady = CompletableFuture()
            Log.w(TAG, "无障碍代理AIDL服务断开")
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.e(TAG, "无障碍代理绑定失败: $name")
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.e(TAG, "无障碍代理返回null绑定: $name")
        }
    }

    private fun ensureBound() {
        if (!isBound && !bindingInProgress) {
            bindingInProgress = true
            try {
                bindService()
            } catch (e: Exception) {
                Log.e(TAG, "绑定无障碍代理服务失败", e)
            } finally {
                bindingInProgress = false
            }
        }
    }

    private fun waitForServiceReady(timeoutSeconds: Long = 3): Boolean {
        ensureBound()

        return try {
            if (!serviceReady.isDone) {
                serviceReady.get(timeoutSeconds, TimeUnit.SECONDS)
            }
            service != null && isBound
        } catch (e: Exception) {
            Log.e(TAG, "等待无障碍代理服务就绪超时", e)
            false
        }
    }

    private fun bindService() {
        try {
            val context = get()

            val intent = Intent()
            intent.setComponent(
                ComponentName(
                    context,
                    "org.autojs.autojs.tool.AccessibilityProxyServiceImpl"
                )
            )

            context.startService(intent)

            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "无障碍代理服务绑定结果: $bound")

            if (!bound) {
                context.startForegroundService(intent)
                val retryBound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                Log.d(TAG, "重试绑定无障碍代理服务结果: $retryBound")
            }
        } catch (e: Exception) {
            Log.e(TAG, "绑定无障碍代理服务异常", e)
        }
    }

    val isEnabled: Boolean
        get() {
            if (!waitForServiceReady()) return false
            return try {
                service?.isEnabled() ?: false
            } catch (e: RemoteException) {
                Log.e(TAG, "检查无障碍服务状态失败", e)
                false
            }
        }

    fun ensureEnabled(): Boolean {
        if (!waitForServiceReady()) return false
        return try {
            service?.ensureEnabled() ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "启用无障碍服务失败", e)
            false
        }
    }

    fun disable(): Boolean {
        if (!waitForServiceReady()) return false
        return try {
            service?.disable() ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "禁用无障碍服务失败", e)
            false
        }
    }

    fun destroy() {
        if (isBound) {
            try {
                get().unbindService(connection)
                isBound = false
                Log.d(TAG, "无障碍代理服务解绑")
            } catch (e: Exception) {
                Log.e(TAG, "解绑无障碍代理服务失败", e)
            }
        }
    }
}