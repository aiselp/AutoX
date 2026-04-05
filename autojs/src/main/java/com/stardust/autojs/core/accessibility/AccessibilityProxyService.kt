package com.stardust.autojs.core.accessibility

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.stardust.view.accessibility.AccessibilityService


class AccessibilityProxyService : Service() {

    private val binder = object : IAccessibilityProxyService.Stub() {
        override fun isEnabled(): Boolean {
            return AccessibilityService.instance != null
        }

        override fun ensureEnabled(): Boolean {
            if (AccessibilityService.instance != null) return true

            val result = AccessibilityServiceTool.enableAccessibilityServiceByRootAndWaitFor(2000)
            Log.d("AccessibilityService", "代理service, result: $result, instance: ${AccessibilityService.instance}");
            return AccessibilityService.instance != null
        }

        override fun disable(): Boolean {
            return AccessibilityService.disable()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}