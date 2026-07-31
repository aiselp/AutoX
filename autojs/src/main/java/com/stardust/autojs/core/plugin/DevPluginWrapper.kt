package com.stardust.autojs.core.plugin

import android.content.Context
import android.util.Log
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.annotation.ScriptInterface
import androidx.core.content.edit

/**
 * DevPlugin 的包装类，用于在 autojs 模块中访问 app 模块的 DevPluginAccessor
 */
class DevPluginWrapper {

    companion object {
        private const val TAG = "DevPluginWrapper"
    }
    // 直接获取 Kotlin 版本的 DevPluginAccessor 单例
    private val devPluginAccessor = DevPluginAccessor.getInstance()

    @ScriptInterface
    fun connectToComputer(url: String) {
        devPluginAccessor.connectToComputer(url)
    }

    @ScriptInterface
    fun disconnectFromComputer() {
        devPluginAccessor.disconnectFromComputer()
    }

    @ScriptInterface
    fun startUSBDebug() {
        devPluginAccessor.startUSBDebug()
    }

    @ScriptInterface
    fun stopUSBDebug() {
        devPluginAccessor.stopUSBDebug()
    }

    @ScriptInterface
    fun isComputerConnected(): Boolean {
        return devPluginAccessor.isComputerConnected
    }

    @ScriptInterface
    fun isUSBDebugActive(): Boolean {
        return devPluginAccessor.isUSBDebugActive
    }

    @ScriptInterface
    fun connectToSavedAddress() {
        return devPluginAccessor.connectToSavedAddress()
    }

    @ScriptInterface
    fun getSavedServerAddress(): String {
        return devPluginAccessor.savedServerAddress ?: ""
    }

    /**
     * 直接设置服务器地址到首选项
     */
    @ScriptInterface
    fun setServerAddress(address: String) {
        try {
            val pref = GlobalAppContext.get()
                .getSharedPreferences("pref", Context.MODE_PRIVATE)
            pref.edit { putString("server_address", address) }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "GlobalAppContext 未初始化", e)
        } catch (e: NullPointerException) {
            Log.e(TAG, "SharedPreferences 为 null", e)
        } catch (e: Exception) {
            Log.e(TAG, "保存服务器地址失败", e)
        }
    }

    @ScriptInterface
    fun clearServerAddress() {
        try {
            val pref = GlobalAppContext.get()
                .getSharedPreferences("pref", Context.MODE_PRIVATE)
            pref.edit { remove("server_address") }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "GlobalAppContext 未初始化", e)
        } catch (e: NullPointerException) {
            Log.e(TAG, "SharedPreferences 为 null", e)
        } catch (e: Exception) {
            Log.e(TAG, "清除服务器地址失败", e)
        }
    }
}