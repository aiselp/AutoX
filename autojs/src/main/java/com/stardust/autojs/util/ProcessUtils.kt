package com.stardust.autojs.util

import android.app.ActivityManager
import android.content.Context
import com.stardust.autojs.R


object ProcessUtils {
    private const val LOG_TAG = "ProcessUtils"

    @JvmStatic
    fun getProcessPid(process: Process): Int {
        return try {
            val pid = process.javaClass.getDeclaredField("pid")
            pid.isAccessible = true
            pid[process] as Int
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    @JvmStatic
    fun isScriptProcess(context: Context): Boolean {
        val pid = android.os.Process.myPid()
        val processName = context.packageName + context.getString(R.string.text_script_process_name)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (processInfo in activityManager.runningAppProcesses) {
            if (processInfo.pid == pid && processInfo.processName == processName) {
                return true
            }
        }
        return false
    }

    fun isMainProcess(context: Context): Boolean {
        val currentProcessName = getProcessName(context, android.os.Process.myPid())
        val packageName = context.packageName
        return currentProcessName != null && currentProcessName == packageName
    }

    private fun getProcessName(context: Context, pid: Int): String? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (processInfo in am.runningAppProcesses) {
            if (processInfo.pid == pid) {
                return processInfo.processName
            }
        }
        return null
    }
}
