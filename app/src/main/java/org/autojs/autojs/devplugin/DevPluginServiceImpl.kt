package org.autojs.autojs.devplugin

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.autojs.autojs.Pref

class DevPluginServiceImpl : Service() {

    companion object {
        private const val TAG = "DevPluginService"
    }

    private fun getUrl(input: String): String {
        var url1 = input
        if (!url1.matches(Regex("^(ws|wss)://.*"))) {
            url1 = "ws://${url1}"
        }
        if (!url1.matches(Regex("^.+://.+?:.+$"))) {
            url1 += ":${DevPlugin.SERVER_PORT}"
        }
        return url1
    }

    private val binder = object : IDevPluginService.Stub() {

        @Throws(RemoteException::class)
        override fun connectToComputer(url: String) {
            val fullUrl = getUrl(url)
            Log.d(TAG, "AIDL调用: connectToComputer($fullUrl)")
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    DevPlugin.connect(fullUrl)
                    Log.d(TAG, "连接成功: $fullUrl")
                } catch (e: Exception) {
                    Log.e(TAG, "连接失败: ${e.message}", e)
                }
            }
        }

        @Throws(RemoteException::class)
        override fun disconnectFromComputer() {
            Log.d(TAG, "AIDL调用: disconnectFromComputer()")
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    DevPlugin.close()
                    Log.d(TAG, "断开连接成功")
                } catch (e: Exception) {
                    Log.e(TAG, "断开连接失败: ${e.message}", e)
                }
            }
        }

        @Throws(RemoteException::class)
        override fun isComputerConnected(): Boolean {
            return try {
                val connected = DevPlugin.isActive
                Log.d(TAG, "电脑连接状态: $connected")
                connected
            } catch (e: Exception) {
                Log.e(TAG, "获取连接状态失败: ${e.message}", e)
                false
            }
        }

        @Throws(RemoteException::class)
        override fun getSavedServerAddress(): String {
            Log.d(TAG, "AIDL调用: getSavedServerAddress()")
            return try {
                val address = Pref.getServerAddressOrDefault("")
                Log.d(TAG, "保存的地址: $address")
                address
            } catch (e: Exception) {
                Log.e(TAG, "获取保存地址失败: ${e.message}", e)
                ""
            }
        }

        @Throws(RemoteException::class)
        override fun startUSBDebug() {
            Log.d(TAG, "AIDL调用: startUSBDebug()")
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    DevPlugin.startUSBDebug()
                    Log.d(TAG, "启动USB调试成功")
                } catch (e: Exception) {
                    Log.e(TAG, "启动USB调试失败: ${e.message}", e)
                }
            }
        }

        @Throws(RemoteException::class)
        override fun stopUSBDebug() {
            Log.d(TAG, "AIDL调用: stopUSBDebug()")
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    DevPlugin.stopUSBDebug()
                    Log.d(TAG, "停止USB调试成功")
                } catch (e: Exception) {
                    Log.e(TAG, "停止USB调试失败: ${e.message}", e)
                }
            }
        }

        @Throws(RemoteException::class)
        override fun isUSBDebugActive(): Boolean {
            Log.d(TAG, "AIDL调用: isUSBDebugActive()")
            return try {
                val active = DevPlugin.isUSBDebugServiceActive
                Log.d(TAG, "USB调试状态: $active")
                active
            } catch (e: Exception) {
                Log.e(TAG, "获取USB调试状态失败: ${e.message}", e)
                false
            }
        }

    }

    // 在 Service 的 onCreate 中
    override fun onCreate() {
        super.onCreate()
        Log.d("DevPluginService", "DevPluginServiceImpl onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "收到启动命令")
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "收到绑定请求，返回AIDL接口")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "服务解绑")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DevPluginServiceImpl 销毁")
    }
}