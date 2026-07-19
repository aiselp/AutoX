package com.stardust.autojs.core.shizuku

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.BuildConfig
import com.stardust.autojs.core.console.ConsoleImpl
import com.stardust.autojs.servicecomponents.ScriptServiceConnection
import com.stardust.autojs.util.ProcessUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs


class ShizukuClient private constructor() : Shizuku.OnRequestPermissionResultListener,
    Shizuku.OnBinderDeadListener, Shizuku.OnBinderReceivedListener {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var packageName: String? = null

    var available: Boolean by mutableStateOf(false)
    var userPermission: Boolean by mutableStateOf(false)
    val shizukuConnection: ShizukuConnection = object : ShizukuConnection() {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            super.onServiceConnected(name, service)
            scope.launch { available = true }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            super.onServiceDisconnected(name)
            scope.launch { available = false }
        }

    }

    init {
        scope.launch {
            available = Shizuku.pingBinder()
            userPermission = checkPermission()
        }
    }

    fun checkPermission(): Boolean {
        if (Shizuku.isPreV11()) {
            return false
        }
        if (!Shizuku.pingBinder()) return false

        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Granted
            true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Users choose "Deny and don't ask again"
            false
        } else {
            false
        }
    }

    fun setupService(packageName: String, console: ConsoleImpl? = null) = scope.launch {
        if (console != null) {
            val subscribe = shizukuConnection.binderConsoleListener.logPublish.subscribe {
                console.print(it.level, it.content)
            }
        }
        if (available) {
            bindService(packageName)
        }
        this@ShizukuClient.packageName = packageName
    }

    suspend fun ensureShizukuService(): IShizukuUserService {
        if (shizukuConnection.service == null && available && checkPermission()) {
            bindService(packageName!!)
        }
        return withTimeout(2000) {
            var exception: Throwable? = null
            shizukuConnection.binder.invokeOnCompletion {
                exception = it
            }
            shizukuConnection.binder.join()
            if (exception != null) throw exception as Throwable
            shizukuConnection.service!!
        }
    }


    fun bindService(packageName: String = this.packageName!!) {
        Log.d(TAG, "bindService")
        if (!checkPermission()) {
            return
        }
        val userServiceArgs = UserServiceArgs(
            ComponentName(packageName, ShizukuUserService::class.java.getName())
        )
            .daemon(true)
            .processNameSuffix("service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
        shizukuConnection.binder = Job()
        Shizuku.bindUserService(userServiceArgs, shizukuConnection)
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode == REQUEST_CODE) {
            scope.launch {
                userPermission = grantResult == PackageManager.PERMISSION_GRANTED
                if (ProcessUtils.isMainProcess(GlobalAppContext.get()))
                    ScriptServiceConnection.GlobalConnection.bindShizukuUserService()
            }
        }
    }

    override fun onBinderDead() {
        Log.d(TAG, "onBinderDead")
        scope.launch {
            available = false
        }
    }

    override fun onBinderReceived() {
        Log.d(TAG, "onBinderReceived")
        scope.launch {
            available = true
            userPermission = checkPermission()
            if (packageName != null) {
                bindService(packageName!!)
            }
        }
    }

    companion object {
        private const val TAG = "ShizukuClient"
        private const val REQUEST_CODE = 1564
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"

        fun requestPermission() {
            Shizuku.requestPermission(REQUEST_CODE)
        }

        val instance by lazy {
            val client = ShizukuClient()
            Shizuku.addRequestPermissionResultListener(client)
            Shizuku.addBinderDeadListener(client)
            Shizuku.addBinderReceivedListener(client)
            client
        }

        suspend fun setAppOpsPermission(op: String, allow: Boolean): Boolean {
            val packageName = GlobalAppContext.get().packageName
            if (instance.packageName == null) {
                instance.setupService(packageName).join()
            }
            val mode = if (allow) "allow" else "ask"

            return runCatching {
                if (instance.available && instance.userPermission) {
                    val service = instance.ensureShizukuService()
                    val result = service.runShellCommand(0, "appops set $packageName $op $mode")
                    result.contains("\"code\":0")
                } else null
            }.getOrNull() ?: runCatching {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "appops set $packageName $op $mode")).waitFor() == 0
            }.getOrDefault(false)
        }

        suspend fun checkAppOpsPermission(op: String): Boolean {
            val packageName = GlobalAppContext.get().packageName
            if (instance.packageName == null) {
                instance.setupService(packageName).join()
            }

            return runCatching {
                if (instance.available && instance.userPermission) {
                    val service = instance.ensureShizukuService()
                    val result = service.runShellCommand(0, "appops get $packageName $op")
                    result.contains("allow")
                } else null
            }.getOrNull() ?: runCatching {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "appops get $packageName $op"))
                val result = process.inputStream.bufferedReader().readText()
                process.waitFor()
                process.destroy()
                result.contains("allow")
            }.getOrDefault(false)
        }
    }


}