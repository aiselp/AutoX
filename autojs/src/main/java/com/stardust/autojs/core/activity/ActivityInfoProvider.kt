package com.stardust.autojs.core.activity

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.stardust.app.isOpPermissionGranted
import com.stardust.view.accessibility.AccessibilityDelegate
import java.util.regex.Pattern

/**
 * Created by Stardust on 2017/3/9.
 */

class ActivityInfoProvider(private val context: Context) : AccessibilityDelegate {

    private val mPackageManager: PackageManager = context.packageManager

    @Volatile
    private var mLatestPackage: String = ""

    @Volatile
    private var mLatestActivity: String = ""

    val latestPackage: String
        get() {
            if (useUsageStats) {
                mLatestPackage = getLatestPackageByUsageStats()
            }
            return mLatestPackage
        }

    val latestActivity: String
        get() {
            return mLatestActivity
        }

    var useUsageStats: Boolean = false

    @Deprecated("shell相关命令不存在")
    var useShell: Boolean = false

    override val eventTypes: Set<Int>? = AccessibilityDelegate.ALL_EVENT_TYPES

    override fun onAccessibilityEvent(
        service: AccessibilityService, event: AccessibilityEvent
    ): Boolean {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.WINDOWS_CHANGE_ACTIVE) {
            val window = service.getWindow(event.windowId)
            if (window?.isFocused != false) {
                setLatestComponent(event.packageName, event.className)
                return false
            }
        }
        return false
    }

    fun getLatestPackageByUsageStatsIfGranted(): String {
        if (context.isOpPermissionGranted(AppOpsManager.OPSTR_GET_USAGE_STATS)) {
            return getLatestPackageByUsageStats()
        }
        return mLatestPackage
    }

    fun getLatestPackageByUsageStats(): String {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val current = System.currentTimeMillis()
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            current - 60 * 60 * 1000,
            current
        )
        return if (usageStats.isEmpty()) {
            mLatestPackage
        } else {
            usageStats.sortBy {
                it.lastTimeStamp
            }
            usageStats.last().packageName
        }

    }

    private fun setLatestComponent(latestPackage: CharSequence?, latestClass: CharSequence?) {
        if (latestPackage == null)
            return
        val latestPackageStr = latestPackage.toString()
        val latestClassStr = (latestClass ?: "").toString()
        if (isPackageExists(latestPackageStr)) {
            mLatestPackage = latestPackage.toString()
            mLatestActivity = latestClassStr
        }
        Log.d(
            LOG_TAG,
            "setLatestComponent: $latestPackage/$latestClassStr $mLatestPackage/$mLatestActivity"
        )
    }

    private fun isPackageExists(packageName: String): Boolean {
        return try {
            mPackageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    companion object {
        private val WINDOW_PATTERN =
            Pattern.compile("Window\\{\\S+\\s\\S+\\s([^\\/]+)\\/?([^}]+)?\\}")

        private const val LOG_TAG = "ActivityInfoProvider"
    }
}

private fun AccessibilityService.getWindow(windowId: Int): AccessibilityWindowInfo? {
    windows.forEach {
        if (it.id == windowId) {
            return it
        }
    }
    return null
}
