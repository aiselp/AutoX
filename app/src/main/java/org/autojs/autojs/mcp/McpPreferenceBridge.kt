package org.autojs.autojs.mcp

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.autojs.autoxjs.mcp.McpPrefKeys
import org.autojs.autoxjs.mcp.McpServerService

class McpPreferenceBridge(private val context: Context) :
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    fun start() {
        prefs.registerOnSharedPreferenceChangeListener(this)
        applyState()
    }

    fun stop() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        McpServerService.stop(context)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == McpPrefKeys.KEY_ENABLED) {
            applyState()
        }
    }

    private fun applyState() {
        val enabled = prefs.getBoolean(McpPrefKeys.KEY_ENABLED, false)
        if (enabled) {
            McpServerService.start(context)
        } else {
            McpServerService.stop(context)
        }
    }
}
