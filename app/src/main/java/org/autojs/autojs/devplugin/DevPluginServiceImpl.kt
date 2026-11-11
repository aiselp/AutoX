package org.autojs.autojs.devplugin

import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.stardust.app.service.AbstractAutoService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.autojs.autojs.Pref

class DevPluginServiceImpl : AbstractAutoService() {

    companion object {
        private const val TAG = "DevPluginService"
    }

    // 给 Service 绑定一个 Job，方便在 onDestroy 时取消所有协程
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private fun getUrl(input: String): String {
        var url1 = input.trim() // 加上 trim 去除首尾空格
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
            serviceScope.launch { // 使用 IO 调度器
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
            serviceScope.launch {
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
                DevPlugin.isActive
            } catch (e: Exception) {
                Log.e(TAG, "获取连接状态失败: ${e.message}", e)
                false
            }
        }

        @Throws(RemoteException::class)
        override fun getSavedServerAddress(): String {
            return try {
                Pref.getServerAddressOrDefault("")
            } catch (e: Exception) {
                ""
            }
        }

        @Throws(RemoteException::class)
        override fun startUSBDebug() {
            Log.d(TAG, "AIDL调用: startUSBDebug()")
            serviceScope.launch { // 放后台线程跑，防止阻塞主线程导致 ANR
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
            serviceScope.launch {
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
            return try {
                DevPlugin.isUSBDebugServiceActive
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DevPluginServiceImpl onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // 销毁时取消所有协程
        Log.d(TAG, "DevPluginServiceImpl 销毁")
    }
}