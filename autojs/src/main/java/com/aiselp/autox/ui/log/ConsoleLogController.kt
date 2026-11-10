package com.aiselp.autox.ui.log

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.aiselp.autox.utils.DeviceInfo
import com.stardust.autojs.R
import com.stardust.autojs.core.console.ConsoleImpl
import com.stardust.autojs.core.console.ConsoleLogFormatter
import com.stardust.autojs.core.console.ConsoleView
import com.stardust.autojs.core.console.LogEntry
import com.stardust.toast
import com.stardust.util.ClipboardUtil
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志页面上的复制、多选、保存为文件、分享等操作。
 *
 * 需要在 Activity 的 `onCreate` 中创建（内部会注册 Activity Result 回调）。
 * 复制、保存、分享的内容都取自 [console] 当前持有的日志，也就是界面上看到的日志。
 */
class ConsoleLogController(
    private val activity: ComponentActivity,
    private val console: ConsoleImpl,
    private val consoleView: ConsoleView,
) {
    /** 是否处于多选状态，供界面据此切换顶栏。 */
    var selectionMode by mutableStateOf(false)
        private set

    /** 多选状态下已选中的行数。 */
    var selectedCount by mutableIntStateOf(0)
        private set

    private var pendingSaveText: String? = null
    private val saveFileLauncher =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            onSaveTargetChosen(uri)
        }

    init {
        consoleView.setSelectionListener { mode, count ->
            selectionMode = mode
            selectedCount = count
        }
    }

    /** 一键复制全部日志。 */
    fun copyAll() {
        copyEntries(console.snapshotLogs())
    }

    /** 复制多选状态下选中的日志，复制成功后退出多选。 */
    fun copySelected() {
        val entries = consoleView.getSelectedEntries()
        if (entries.isEmpty()) return
        if (copyEntries(entries)) {
            consoleView.exitSelectionMode()
        }
    }

    fun enterSelectionMode() {
        if (isLogEmpty()) {
            toast(activity, R.string.text_log_empty)
            return
        }
        consoleView.enterSelectionMode()
    }

    fun exitSelectionMode() {
        consoleView.exitSelectionMode()
    }

    fun selectAll() {
        consoleView.selectAll()
    }

    /** 让用户选择位置，把全部日志保存成文本文件。 */
    fun saveToFile() {
        val entries = console.snapshotLogs()
        if (entries.isEmpty()) {
            toast(activity, R.string.text_log_empty)
            return
        }
        pendingSaveText = buildExportText(entries)
        try {
            saveFileLauncher.launch(ConsoleLogFormatter.exportFileName(System.currentTimeMillis()))
        } catch (e: ActivityNotFoundException) {
            pendingSaveText = null
            toast(activity, activity.getString(R.string.text_log_save_failed, e.message))
        }
    }

    /** 把全部日志导出成文本文件，通过系统分享面板发送出去。 */
    fun share() {
        val entries = console.snapshotLogs()
        if (entries.isEmpty()) {
            toast(activity, R.string.text_log_empty)
            return
        }
        val text = buildExportText(entries)
        Thread {
            val result = runCatching {
                val dir = File(activity.externalCacheDir ?: activity.cacheDir, SHARE_DIR_NAME)
                dir.mkdirs()
                // 只保留最近一次导出的文件，避免缓存目录越积越多
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, ConsoleLogFormatter.exportFileName(System.currentTimeMillis()))
                file.writeText(text, Charsets.UTF_8)
                FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            }
            activity.runOnUiThread {
                result.onSuccess { uri -> startShare(uri) }
                    .onFailure { toast(activity, activity.getString(R.string.text_log_share_failed, it.message)) }
            }
        }.start()
    }

    private fun startShare(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.text_log_share)))
        } catch (e: ActivityNotFoundException) {
            toast(activity, activity.getString(R.string.text_log_share_failed, e.message))
        }
    }

    private fun onSaveTargetChosen(uri: Uri?) {
        val pending = pendingSaveText
        pendingSaveText = null
        if (uri == null) return
        // 用户选择保存位置期间 Activity 被重建（例如旋转屏幕）时，待保存的文本已经丢失，按当前日志重新生成
        val text = pending ?: buildExportText(console.snapshotLogs())
        Thread {
            val result = runCatching {
                val output = activity.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("openOutputStream returned null")
                output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }
            activity.runOnUiThread {
                result.onSuccess { toast(activity, R.string.text_log_saved) }
                    .onFailure { toast(activity, activity.getString(R.string.text_log_save_failed, it.message)) }
            }
        }.start()
    }

    /**
     * 把日志复制到剪贴板。日志太多、超出系统剪贴板的容量时，只复制最新的一部分并提示用户。
     *
     * @return 是否复制成功（包括只复制了一部分的情况）
     */
    private fun copyEntries(entries: List<LogEntry>): Boolean {
        if (entries.isEmpty()) {
            toast(activity, R.string.text_log_empty)
            return false
        }
        if (setClipboard(ConsoleLogFormatter.join(entries))) {
            // Android 13 起系统自己会提示已复制，不再重复提示
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                toast(activity, activity.getString(R.string.text_log_copied_lines, entries.size))
            }
            return true
        }
        val latest = ConsoleLogFormatter.joinLatest(entries, CLIPBOARD_FALLBACK_CHARS)
        if (setClipboard(latest.text)) {
            toast(
                activity,
                activity.getString(R.string.text_log_copied_partial, latest.count, entries.size),
                true
            )
            return true
        }
        toast(activity, R.string.text_log_copy_failed)
        return false
    }

    /**
     * 写入系统剪贴板，返回是否成功。
     *
     * 内容超过 Binder 事务的大小上限时，系统服务会抛出 TransactionTooLargeException，
     * 但 [android.content.ClipboardManager] 会把它（以及其他 RemoteException）统一包装成 RuntimeException
     * （rethrowFromSystemServer），没有更具体的类型可以捕获。
     */
    @Suppress("TooGenericExceptionCaught")
    private fun setClipboard(text: String): Boolean {
        return try {
            ClipboardUtil.setClip(activity, text)
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    private fun isLogEmpty(): Boolean {
        synchronized(console.allLogs) {
            return console.allLogs.isEmpty()
        }
    }

    private fun buildExportText(entries: List<LogEntry>): String {
        val appLabel = activity.applicationInfo.loadLabel(activity.packageManager)
        val header = buildString {
            append(appLabel).append(" (").append(activity.packageName).append(") console log\n")
            append("Exported at: ")
                .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                .append('\n')
            append(DeviceInfo(activity).toString())
        }
        return ConsoleLogFormatter.buildExport(header, entries)
    }

    companion object {
        private const val SHARE_DIR_NAME = "console_log_share"

        /** 系统剪贴板放不下全部日志时，退而求其次复制的最大字符数。 */
        private const val CLIPBOARD_FALLBACK_CHARS = 100_000
    }
}
