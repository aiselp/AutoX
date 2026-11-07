package com.stardust.autojs.core.console

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * 控制台日志的磁盘持久化存储，用于在应用或脚本进程被杀死后仍能找回日志。
 *
 * - 写入：[append] 只把日志放入内存队列，由后台线程批量追加到文件，不会阻塞调用方，也不会抛异常。
 * - 存储格式：每条日志一行 `level<TAB>content`，content 中的 `\`、换行、回车会被转义，因此一行就是一条日志。
 * - 轮转：活动文件超过 [maxFileBytes] 后改名为 `console.1.log`，更早的依次后移，总共最多保留 [maxFiles] 个文件。
 *   [maxFileBytes] 是软上限：写入按块进行，每块写入前才检查大小，所以文件可能略微超出一个块。
 * - 跨进程：写入进程以追加模式打开文件，其他进程可以随时读取（[readRecent]）或清空（[clear]）。
 *   清空时对活动文件只截断、不删除，这样写入进程手里的文件句柄依然有效。
 * - 容错：日志存储只是辅助功能，读写失败只通过 [onError] 上报，不能影响应用启动和脚本运行
 *   （读取发生在应用启动阶段，后台写线程里未捕获的异常还会让整个进程崩溃），所以这里有意捕获 Throwable。
 */
@Suppress("TooGenericExceptionCaught")
class ConsoleLogStore @JvmOverloads constructor(
    private val dir: File,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    maxFiles: Int = DEFAULT_MAX_FILES,
    private val onError: ((Throwable) -> Unit)? = null,
) {
    private class Record(val generation: Int, val level: Int, val content: String)

    private val maxFiles = maxFiles.coerceAtLeast(2)
    private val queue = LinkedBlockingQueue<Record>(MAX_PENDING_RECORDS)

    /** 已入队但尚未处理完的记录数，用于 [flush] 判断后台线程是否已空闲。 */
    private val pending = AtomicInteger(0)

    /** 因队列已满而被丢弃的记录数，下次写入时会补一条提示。 */
    private val dropped = AtomicInteger(0)

    /** 保护文件句柄、轮转、读取和清空。 */
    private val ioLock = Any()
    private val workerLock = Any()

    /** [clear] 时递增，使清空前已入队但尚未落盘的记录被丢弃。 */
    @Volatile
    private var generation = 0

    @Volatile
    private var worker: Thread? = null
    private var stream: FileOutputStream? = null

    /**
     * 追加一条日志。可以在任意线程调用，不会阻塞，也不会抛出异常。
     */
    fun append(level: Int, content: String) {
        ensureWorker()
        pending.incrementAndGet()
        if (!queue.offer(Record(generation, level, content))) {
            pending.decrementAndGet()
            dropped.incrementAndGet()
        }
    }

    /**
     * 等待已经 [append] 的日志全部写入文件，最多等待 [timeoutMs] 毫秒。
     */
    fun flush(timeoutMs: Long = FLUSH_TIMEOUT_MS) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (pending.get() > 0 && System.nanoTime() < deadline) {
            try {
                Thread.sleep(2)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    /**
     * 读取最近的至多 [maxEntries] 条日志，按时间从旧到新排列。
     * 返回的 [LogEntry] 没有有效的 id，由接收方（[ConsoleImpl.restoreHistory]）重新编号。
     */
    fun readRecent(maxEntries: Int): List<LogEntry> {
        if (maxEntries <= 0) return emptyList()
        flush()
        return try {
            synchronized(ioLock) { readRecentLocked(maxEntries) }
        } catch (t: Throwable) {
            reportError(t)
            emptyList()
        }
    }

    /**
     * 清空所有已保存的日志，并丢弃尚未写入的日志。
     */
    fun clear() {
        synchronized(ioLock) {
            generation++
            dropped.set(0)
            try {
                for (i in 1 until maxFiles) {
                    fileAt(i).delete()
                }
                val active = fileAt(0)
                if (active.isFile) {
                    RandomAccessFile(active, "rw").use { it.setLength(0) }
                }
            } catch (t: Throwable) {
                reportError(t)
            }
        }
    }

    private fun ensureWorker() {
        if (worker != null) return
        synchronized(workerLock) {
            if (worker == null) {
                worker = Thread(::writeLoop, "ConsoleLogStore").apply {
                    isDaemon = true
                    start()
                }
            }
        }
    }

    private fun writeLoop() {
        val batch = ArrayList<Record>(BATCH_SIZE)
        try {
            while (true) {
                batch.add(queue.take())
                queue.drainTo(batch, BATCH_SIZE - 1)
                try {
                    writeBatch(batch)
                } catch (t: Throwable) {
                    reportError(t)
                } finally {
                    pending.addAndGet(-batch.size)
                    batch.clear()
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            synchronized(workerLock) {
                if (worker === Thread.currentThread()) worker = null
            }
        }
    }

    private fun writeBatch(batch: List<Record>) {
        synchronized(ioLock) {
            val lost = dropped.getAndSet(0)
            val text = StringBuilder()
            val currentGeneration = generation
            for (record in batch) {
                if (record.generation == currentGeneration) {
                    encode(record.level, record.content, text)
                    // 分块写入：每写一块就检查一次是否需要轮转，
                    // 否则一批很大的日志会全部写进同一个文件，文件大小远超 maxFileBytes
                    if (text.length >= WRITE_CHUNK_CHARS) {
                        writeChunk(text)
                    }
                }
            }
            if (lost > 0) {
                encode(Log.WARN, "[ConsoleLogStore] $lost log entries were dropped (writer too slow)", text)
            }
            writeChunk(text)
        }
    }

    /** 把 [text] 追加到活动文件（必要时先轮转），然后清空 [text]。 */
    private fun writeChunk(text: StringBuilder) {
        if (text.isEmpty()) return
        var out = openStream()
        if (out.channel.size() >= maxFileBytes) {
            rotate()
            out = openStream()
        }
        out.write(text.toString().toByteArray(Charsets.UTF_8))
        text.setLength(0)
    }

    /** 打开活动文件；如果文件在外部被删除了，则重新创建，避免一直写入已被删除的文件。 */
    private fun openStream(): FileOutputStream {
        val current = stream
        if (current != null) {
            if (fileAt(0).exists()) return current
            closeStream()
        }
        dir.mkdirs()
        return FileOutputStream(fileAt(0), true).also { stream = it }
    }

    private fun closeStream() {
        try {
            stream?.close()
        } catch (ignored: Throwable) {
        }
        stream = null
    }

    private fun rotate() {
        closeStream()
        fileAt(maxFiles - 1).delete()
        for (i in maxFiles - 2 downTo 0) {
            val file = fileAt(i)
            if (file.exists()) {
                file.renameTo(fileAt(i + 1))
            }
        }
    }

    private fun readRecentLocked(maxEntries: Int): List<LogEntry> {
        // 从最新的文件开始读，攒够条数就停
        val chunks = ArrayList<List<LogEntry>>()
        var total = 0
        for (i in 0 until maxFiles) {
            val file = fileAt(i)
            if (!file.isFile) continue
            val entries = parseFile(file, maxEntries - total)
            if (entries.isEmpty()) continue
            chunks.add(entries)
            total += entries.size
            if (total >= maxEntries) break
        }
        val result = ArrayList<LogEntry>(minOf(total, maxEntries))
        for (i in chunks.indices.reversed()) {
            result.addAll(chunks[i])
        }
        return if (result.size > maxEntries) {
            ArrayList(result.subList(result.size - maxEntries, result.size))
        } else {
            result
        }
    }

    /**
     * 解析文件中最新的至多 [maxEntries] 行。
     * 恢复历史发生在应用启动的主线程上，而一个写满的文件可能有几十万行很短的日志，
     * 所以只为用得上的最新几行创建对象，解析量只和 [maxEntries] 有关，与文件里有多少行无关。
     */
    private fun parseFile(file: File, maxEntries: Int): List<LogEntry> {
        if (maxEntries <= 0) return emptyList()
        val text = String(file.readBytes(), Charsets.UTF_8)
        // 末尾没有换行符的内容可能还在写入过程中，忽略
        val end = text.lastIndexOf('\n') + 1
        if (end == 0) return emptyList()

        // 从末尾逐行往前找到第 maxEntries 行的行首。
        // lastIndexOf 向前找到的是紧邻的上一个换行符，最多跨越一行，不会扫描整个文件。
        var start = end
        var terminator = end - 1
        var remaining = maxEntries
        while (remaining > 0) {
            val previous = if (terminator > 0) text.lastIndexOf('\n', terminator - 1) else -1
            start = previous + 1
            remaining--
            if (previous < 0) break
            terminator = previous
        }

        val result = ArrayList<LogEntry>()
        var lineStart = start
        while (lineStart < end) {
            val lineEnd = text.indexOf('\n', lineStart)
            decode(text, lineStart, lineEnd)?.let { result.add(it) }
            lineStart = lineEnd + 1
        }
        return result
    }

    private fun fileAt(index: Int): File {
        return if (index == 0) File(dir, ACTIVE_FILE_NAME) else File(dir, "console.$index.log")
    }

    private fun reportError(t: Throwable) {
        try {
            onError?.invoke(t)
        } catch (ignored: Throwable) {
        }
    }

    companion object {
        private const val TAG = "ConsoleLogStore"
        private const val DIR_NAME = "console_logs"
        private const val ACTIVE_FILE_NAME = "console.log"

        const val DEFAULT_MAX_FILE_BYTES = 2L * 1024 * 1024
        const val DEFAULT_MAX_FILES = 3

        /** 待写入队列的容量；写入跟不上时丢弃新日志，而不是无限占用内存。 */
        private const val MAX_PENDING_RECORDS = 20_000
        private const val BATCH_SIZE = 512

        /** 一次写入文件的字符数达到这个值就先写一块，这样文件大小只会略微超出 [DEFAULT_MAX_FILE_BYTES]。 */
        private const val WRITE_CHUNK_CHARS = 64 * 1024

        /** 单条日志最多保存的字符数，防止异常的超长日志撑爆文件。 */
        private const val MAX_ENTRY_CHARS = 64 * 1024
        private const val FLUSH_TIMEOUT_MS = 3000L

        private val instances = ConcurrentHashMap<String, ConsoleLogStore>()

        /**
         * 获取当前应用的日志存储。同一进程内共享同一个实例，
         * 不同进程的实例指向同一个目录，因此可以互相读取对方写入的日志。
         */
        @JvmStatic
        fun forContext(context: Context): ConsoleLogStore {
            val dir = File((context.applicationContext ?: context).filesDir, DIR_NAME)
            return instances.getOrPut(dir.absolutePath) {
                ConsoleLogStore(dir, onError = { Log.w(TAG, "console log store error", it) })
            }
        }

        private fun encode(level: Int, content: String, out: StringBuilder) {
            out.append(level).append('\t')
            val limit = minOf(content.length, MAX_ENTRY_CHARS)
            for (i in 0 until limit) {
                when (val c = content[i]) {
                    '\\' -> out.append("\\\\")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    else -> out.append(c)
                }
            }
            if (content.length > limit) {
                out.append("...(truncated ").append(content.length - limit).append(" chars)")
            }
            out.append('\n')
        }

        /**
         * 只在 [start, end) 内查找字符。
         * 不能用 [String.indexOf] 的 startIndex 重载：它会越过当前行一路扫描到整个文件末尾，
         * 对 2MB 的日志文件，每解析一行都要扫一遍剩余内容，整体变成 O(行数 × 文件大小)，主线程会卡死。
         */
        private fun indexOfInRange(text: String, ch: Char, start: Int, end: Int): Int {
            for (i in start until end) {
                if (text[i] == ch) return i
            }
            return -1
        }

        private fun decode(text: String, start: Int, end: Int): LogEntry? {
            val tab = indexOfInRange(text, '\t', start, end)
            if (tab < 0) return null
            val level = text.substring(start, tab).toIntOrNull() ?: return null
            return LogEntry(level = level, content = unescape(text, tab + 1, end), newLine = true)
        }

        private fun unescape(text: String, start: Int, end: Int): String {
            val firstEscape = indexOfInRange(text, '\\', start, end)
            if (firstEscape < 0) return text.substring(start, end)
            val out = StringBuilder(end - start)
            var i = start
            while (i < end) {
                val c = text[i]
                if (c == '\\' && i + 1 < end) {
                    when (val next = text[i + 1]) {
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        '\\' -> out.append('\\')
                        else -> out.append(c).append(next)
                    }
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}
