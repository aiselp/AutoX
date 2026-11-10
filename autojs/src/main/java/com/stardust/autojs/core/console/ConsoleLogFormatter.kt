package com.stardust.autojs.core.console

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把日志拼接成用于复制、保存、分享的文本。
 */
object ConsoleLogFormatter {

    /**
     * 复制日志时取到的文本，以及其中包含的日志条数。
     */
    class Text(val text: String, val count: Int)

    /**
     * 把若干条日志拼成多行文本，一条日志一行（日志内容自带的换行原样保留）。
     */
    @JvmStatic
    fun join(entries: List<LogEntry>): String {
        val sb = StringBuilder()
        for (i in entries.indices) {
            if (i > 0) sb.append('\n')
            sb.append(entries[i].content)
        }
        return sb.toString()
    }

    /**
     * 从最新的日志往前取，直到文本长度将要超过 [maxChars]，返回这部分日志拼成的文本。
     * 至少会返回最新的一条（它自己超长时只保留结尾的 [maxChars] 个字符）。
     */
    @JvmStatic
    fun joinLatest(entries: List<LogEntry>, maxChars: Int): Text {
        if (entries.isEmpty()) return Text("", 0)
        var start = entries.size
        var length = 0
        while (start > 0) {
            val next = entries[start - 1].content.length + 1
            if (length + next > maxChars) break
            length += next
            start--
        }
        if (start == entries.size) {
            return Text(entries.last().content.takeLast(maxChars), 1)
        }
        return Text(join(entries.subList(start, entries.size)), entries.size - start)
    }

    /**
     * 生成导出文件的内容：头部说明（应用与设备信息）+ 日志正文。
     */
    @JvmStatic
    fun buildExport(header: String, entries: List<LogEntry>): String {
        val sb = StringBuilder()
        sb.append(header.trimEnd()).append('\n')
        sb.append("Log entries: ").append(entries.size).append('\n')
        sb.append("=".repeat(40)).append('\n')
        sb.append(join(entries)).append('\n')
        return sb.toString()
    }

    /**
     * 导出文件的文件名，例如 `autox-log-20261107-063000.txt`。
     */
    @JvmStatic
    fun exportFileName(timeMillis: Long): String {
        return "autox-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timeMillis)) + ".txt"
    }
}
