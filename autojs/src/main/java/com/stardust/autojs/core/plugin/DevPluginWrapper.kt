package com.stardust.autojs.core.plugin

import android.content.Context
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.annotation.ScriptInterface

/**
 * DevPlugin 的包装类，用于在 autojs 模块中访问 app 模块的 DevPluginAccessor
 */
class DevPluginWrapper {

    // 直接获取 Kotlin 版本的 DevPluginAccessor 单例
    private val devPluginAccessor = DevPluginAccessor.Companion.getInstance()

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
            pref.edit().putString("server_address", address).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 清除保存的服务器地址
     */
    @ScriptInterface
    fun clearServerAddress() {
        try {
            val pref = GlobalAppContext.get()
                .getSharedPreferences("pref", Context.MODE_PRIVATE)
            pref.edit().remove("server_address").apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}