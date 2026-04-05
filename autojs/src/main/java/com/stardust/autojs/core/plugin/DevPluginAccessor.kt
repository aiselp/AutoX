package com.stardust.autojs.core.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.stardust.app.GlobalAppContext
import org.autojs.autojs.devplugin.IDevPluginService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DevPluginAccessor private constructor() {

    companion object {
        private const val TAG = "DevPluginAccessor"

        @Volatile
        private var instance: DevPluginAccessor? = null
        @JvmStatic
        fun getInstance(): DevPluginAccessor {
            return instance ?: synchronized(this) {
                instance ?: DevPluginAccessor().also { instance = it }
            }
        }
    }
    private var service: IDevPluginService? = null
    private var isBound = false
    private var bindingInProgress = false
    private var serviceReady = CompletableFuture<Boolean?>()
    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IDevPluginService.Stub.asInterface(binder)
            isBound = true
            // 标记 Service 就绪
            serviceReady.complete(true)
            Log.d(TAG, "AIDL服务连接成功")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
            serviceReady = CompletableFuture<Boolean?>()
            Log.d(TAG, "AIDL服务断开")
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.e(TAG, "!!! 绑定失败: " + name)
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.e(TAG, "!!! Service返回null: " + name)
        }
    }

    private fun ensureBound() {
        if (!isBound && !bindingInProgress) {
            bindingInProgress = true
            try {
                bindService()
            } catch (e: Exception) {
                Log.e(TAG, "绑定失败", e)
            } finally {
                bindingInProgress = false
            }
        }
    }

    private fun waitForServiceReady(timeout: Long = 3): Boolean {
        ensureBound()

        return try {
            if (!serviceReady.isDone) {
                serviceReady.get(timeout, TimeUnit.SECONDS)
            }
            service != null && isBound
        } catch (e: Exception) {
            false
        }
    }

    private fun bindService() {
        try {
            val context = GlobalAppContext.get()

            val intent = Intent()
            intent.setComponent(
                ComponentName(
                    context,
                    "org.autojs.autojs.devplugin.DevPluginServiceImpl"
                )
            )
            context.startService(intent)

            var bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            Log.d("DevPluginAccessor", "Service绑定结果: " + bound)

            // 如果失败，启动前台Service
            if (!bound) {
                context.startForegroundService(intent)
                bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                Log.d("DevPluginAccessor", "失败后尝试前台Service绑定: " + bound)
            }
        } catch (e: Exception) {
            Log.e(TAG, "绑定服务失败", e)
        }
    }

    fun connectToComputer(url: String?) {
        try {
            if (!waitForServiceReady()) return
            service!!.connectToComputer(url)
        } catch (e: RemoteException) {
            Log.e(TAG, "AIDL调用失败", e)
        }
    }

    fun connectToSavedAddress() {
        try {
            if (!waitForServiceReady()) return

            val savedAddress = service!!.getSavedServerAddress()

            if (savedAddress == null || savedAddress.trim { it <= ' ' }.isEmpty()) {
                Log.w(TAG, "没有保存的地址")
            }
            service!!.connectToComputer(savedAddress)
        } catch (e: RemoteException) {
            Log.e(TAG, "connectToSavedAddress AIDL调用失败", e)
        }
    }

    fun disconnectFromComputer() {
        try {
            if (!waitForServiceReady()) return
            service!!.disconnectFromComputer()
        } catch (e: RemoteException) {
            Log.e(TAG, "AIDL调用失败", e)
        }
    }

    val isComputerConnected: Boolean
        get() {
            try {
                if (!waitForServiceReady()) return false
                val connected = service!!.isComputerConnected()
                return connected
            } catch (e: RemoteException) {
                Log.e(TAG, "获取状态失败", e)
            }
            return false
        }

    val savedServerAddress: String?
        get() {
            try {
                if (!waitForServiceReady()) return ""
                val address = service!!.getSavedServerAddress()
                return address
            } catch (e: RemoteException) {
                Log.e(TAG, "获取地址失败", e)
            }
            return ""
        }

    fun startUSBDebug() {
        try {
            if (!waitForServiceReady()) return
            service!!.startUSBDebug()
        } catch (e: RemoteException) {
            Log.e(TAG, "AIDL调用失败", e)
        }
    }

    fun stopUSBDebug() {
        try {
            if (!waitForServiceReady()) return
            service!!.stopUSBDebug()
        } catch (e: RemoteException) {
            Log.e(TAG, "AIDL调用失败", e)
        }
    }

    val isUSBDebugActive: Boolean
        get() {
            try {
                if (!waitForServiceReady()) return false
                val active = service!!.isUSBDebugActive()
                return active
            } catch (e: RemoteException) {
                Log.e(TAG, "获取状态失败", e)
            }
            return false
        }

    fun destroy() {
        if (isBound) {
            try {
                GlobalAppContext.get().unbindService(connection)
                isBound = false
                Log.d(TAG, "服务解绑")
            } catch (e: Exception) {
                Log.e(TAG, "解绑失败", e)
            }
        }
    }

}