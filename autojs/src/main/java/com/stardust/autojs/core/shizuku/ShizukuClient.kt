package com.stardust.autojs.core.shizuku

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonParser
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

        private const val APP_OPS_ALLOW = "allow"
        private const val APP_OPS_DEFAULT = "default"

        /**
         * 解析 ShizukuUserService.runShellCommand 返回的 JSON（AbstractShell.Result）：
         * `{ "code": 0, "error": "...", "result": "<stdout>" }`。
         */
        private fun parseShellResult(json: String): Pair<Int, String> = runCatching {
            val obj = JsonParser.parseString(json).asJsonObject
            val code = obj.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1
            val stdout = obj.get("result")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            code to stdout
        }.getOrDefault(-1 to "")

        /**
         * `appops get` 的 stdout 形如 `PROJECT_MEDIA: allow; time=...`。
         * 只有 mode 恰好是 allow 才算「静默允许」；default / foreground / ignore /
         * deny 以及部分 ROM 扩展的 ask(5) 都应显示为关闭。不能用
         * contains("allow")，否则包名、JSON 字段或别处的 allow 会误判。
         */
        private fun isAppOpsAllowed(stdout: String): Boolean =
            stdout.lineSequence().any { line ->
                val mode = line.substringAfter(':', "").trim()
                mode == APP_OPS_ALLOW ||
                    mode.startsWith("$APP_OPS_ALLOW;") ||
                    mode.startsWith("$APP_OPS_ALLOW ")
            }

        suspend fun setAppOpsPermission(op: String, allow: Boolean): Boolean {
            val packageName = GlobalAppContext.get().packageName
            if (instance.packageName == null) {
                instance.setupService(packageName).join()
            }
            // 关闭必须写 default：AOSP 的 MODE_DEFAULT(3) 是「不留显式覆盖，
            // 回落系统默认策略」，对应设置里的「每次都询问」。
            // 原先写 ask，部分 ROM 把它扩展成数值 5，标准工具显示「未知(5)」，
            // 而且它和 allow / foreground 一样会留下覆盖，永远回不到默认。
            val mode = if (allow) APP_OPS_ALLOW else APP_OPS_DEFAULT
            val command = "appops set $packageName $op $mode"

            return runCatching {
                if (instance.available && instance.userPermission) {
                    val service = instance.ensureShizukuService()
                    val (code, _) = parseShellResult(service.runShellCommand(0, command))
                    if (code == 0) true else null
                } else null
            }.getOrNull() ?: runCatching {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val code = process.waitFor()
                process.destroy()
                code == 0
            }.getOrDefault(false)
        }

        suspend fun checkAppOpsPermission(op: String): Boolean {
            val packageName = GlobalAppContext.get().packageName
            if (instance.packageName == null) {
                instance.setupService(packageName).join()
            }
            val command = "appops get $packageName $op"

            return runCatching {
                if (instance.available && instance.userPermission) {
                    val service = instance.ensureShizukuService()
                    val (code, stdout) = parseShellResult(service.runShellCommand(0, command))
                    if (code == 0) isAppOpsAllowed(stdout) else null
                } else null
            }.getOrNull() ?: runCatching {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val stdout = process.inputStream.bufferedReader().readText()
                val code = process.waitFor()
                process.destroy()
                if (code == 0) isAppOpsAllowed(stdout) else false
            }.getOrDefault(false)
        }
    }


}