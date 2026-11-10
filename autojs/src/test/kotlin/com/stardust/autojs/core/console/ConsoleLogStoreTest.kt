package com.stardust.autojs.core.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConsoleLogStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun newStore(
        dir: File = File(folder.root, "logs"),
        maxFileBytes: Long = ConsoleLogStore.DEFAULT_MAX_FILE_BYTES,
        maxFiles: Int = ConsoleLogStore.DEFAULT_MAX_FILES,
    ) = ConsoleLogStore(dir, maxFileBytes, maxFiles)

    private fun ConsoleLogStore.appendAndFlush(level: Int, content: String) {
        append(level, content)
        flush()
    }

    private fun ConsoleLogStore.contents(maxEntries: Int = 10_000) = readRecent(maxEntries).map { it.content }

    @Test
    fun appendedEntriesAreReadBackInOrderWithLevels() {
        val store = newStore()
        store.append(4, "first")
        store.append(6, "second")

        val entries = store.readRecent(100)

        assertEquals(listOf("first", "second"), entries.map { it.content })
        assertEquals(listOf(4, 6), entries.map { it.level })
    }

    @Test
    fun multiLineContentAndSpecialCharactersSurviveRoundTrip() {
        val store = newStore()
        val samples = listOf(
            "line1\nline2\r\nline3",
            "back\\slash and a literal \\n",
            "tab\tinside",
            "中文 and emoji 😀",
            "ends with backslash\\",
            "",
        )
        samples.forEach { store.append(3, it) }

        assertEquals(samples, store.contents())
    }

    @Test
    fun readRecentReturnsOnlyTheNewestEntries() {
        val store = newStore()
        repeat(10) { store.append(4, "log $it") }

        assertEquals(listOf("log 7", "log 8", "log 9"), store.contents(3))
    }

    @Test
    fun readRecentOnEmptyStoreReturnsNothing() {
        assertTrue(newStore().readRecent(10).isEmpty())
    }

    @Test
    fun readRecentHandlesEveryLimitAroundTheNumberOfLines() {
        val dir = File(folder.root, "limits")
        dir.mkdirs()
        File(dir, "console.log").writeText((1..5).joinToString("") { "4\tl$it\n" })
        val store = newStore(dir)

        assertEquals(listOf("l5"), store.contents(1))
        assertEquals(listOf("l4", "l5"), store.contents(2))
        assertEquals(listOf("l2", "l3", "l4", "l5"), store.contents(4))
        assertEquals(listOf("l1", "l2", "l3", "l4", "l5"), store.contents(5))
        assertEquals(listOf("l1", "l2", "l3", "l4", "l5"), store.contents(6))
        assertEquals(listOf("l1", "l2", "l3", "l4", "l5"), store.contents(1000))
    }

    @Test
    fun readRecentLimitIgnoresTheIncompleteTrailingLine() {
        val dir = File(folder.root, "limits-partial")
        dir.mkdirs()
        File(dir, "console.log").writeText("4\ta\n4\tb\n4\tc\n4\tstill being writ")
        val store = newStore(dir)

        assertEquals(listOf("c"), store.contents(1))
        assertEquals(listOf("b", "c"), store.contents(2))
        assertEquals(listOf("a", "b", "c"), store.contents(10))
    }

    @Test
    fun readRecentLimitKeepsEmptyEntriesAndMultiLineEntries() {
        val store = newStore()
        store.append(4, "first")
        store.append(4, "")
        store.append(4, "multi\nline")
        store.append(4, "last")

        assertEquals(listOf("multi\nline", "last"), store.contents(2))
        assertEquals(listOf("", "multi\nline", "last"), store.contents(3))
    }

    @Test
    fun readRecentTakesTheNewestAcrossRotatedFiles() {
        val dir = File(folder.root, "rotated-limit")
        val store = newStore(dir, maxFileBytes = 200, maxFiles = 3)
        repeat(60) { store.appendAndFlush(4, "entry-%03d".format(it)) }
        val all = store.contents()
        assertTrue("test needs entries in more than one file", (dir.listFiles()?.size ?: 0) > 1)

        for (limit in listOf(1, 7, all.size - 1, all.size)) {
            assertEquals("limit $limit", all.takeLast(limit), store.contents(limit))
        }
    }

    @Test
    fun oldEntriesAreDroppedWhenFilesRotate() {
        val dir = File(folder.root, "rotating")
        val store = newStore(dir, maxFileBytes = 200, maxFiles = 3)
        val total = 200
        // 每条日志单独落盘，保证每次写入前都会检查文件大小
        repeat(total) { store.appendAndFlush(4, "entry-%03d".format(it)) }

        val names = store.contents()

        assertTrue("should have kept something", names.isNotEmpty())
        assertTrue("old entries should be dropped, kept ${names.size}", names.size < total)
        // 保留下来的必须是连续的最新一段，且顺序不乱
        assertEquals((total - names.size until total).map { "entry-%03d".format(it) }, names)
        assertTrue("at most 3 files", (dir.listFiles()?.size ?: 0) <= 3)
    }

    @Test
    fun filesStayCloseToTheSizeLimitEvenWhenManyLargeEntriesAreQueuedAtOnce() {
        val dir = File(folder.root, "burst")
        val maxFileBytes = 256L * 1024
        val store = newStore(dir, maxFileBytes = maxFileBytes, maxFiles = 3)
        val entry = "x".repeat(20_000)
        // 连续入队，后台线程会把积压的日志合成一大批来写
        repeat(600) { store.append(4, entry) }
        store.flush(30_000)

        val sizes = dir.listFiles()!!.map { it.length() }

        // 写入按 64K 字符分块，每块写入前才检查大小：最多超出上限一个块加一条日志
        val allowed = maxFileBytes + 64 * 1024 + entry.length + 100
        assertTrue("files should be rotated, sizes=$sizes", sizes.size > 1)
        assertTrue("every file should stay near the limit, sizes=$sizes", sizes.all { it <= allowed })
    }

    @Test
    fun clearRemovesEverythingAndLaterEntriesAreStillStored() {
        val store = newStore(maxFileBytes = 200, maxFiles = 3)
        repeat(60) { store.appendAndFlush(4, "entry-%03d".format(it)) }

        store.clear()

        assertTrue(store.readRecent(100).isEmpty())
        store.appendAndFlush(4, "after clear")
        assertEquals(listOf("after clear"), store.contents())
    }

    @Test
    fun anotherInstanceCanReadAndClearWhileTheWriterKeepsAppending() {
        // 用同一目录下的两个实例模拟「脚本进程写、主进程读和清空」
        val dir = File(folder.root, "shared")
        val writer = newStore(dir)
        val reader = newStore(dir)

        writer.appendAndFlush(4, "a")
        assertEquals(listOf("a"), reader.contents())

        reader.clear()
        assertTrue(reader.readRecent(10).isEmpty())

        writer.appendAndFlush(4, "b")
        assertEquals(listOf("b"), reader.contents())
    }

    @Test
    fun entriesAppendedBeforeClearAreNotWrittenAfterwards() {
        val store = newStore()
        repeat(1000) { store.append(4, "stale $it") }

        store.clear()
        store.appendAndFlush(4, "fresh")

        assertEquals(listOf("fresh"), store.contents())
    }

    @Test
    fun incompleteTrailingLineIsIgnored() {
        val dir = File(folder.root, "partial")
        dir.mkdirs()
        File(dir, "console.log").writeText("4\tcomplete\n4\tstill being writ")

        assertEquals(listOf("complete"), newStore(dir).contents())
    }

    @Test
    fun malformedLinesAreSkipped() {
        val dir = File(folder.root, "malformed")
        dir.mkdirs()
        File(dir, "console.log").writeText("no tab here\n4\tok\nx\tnot a level\n")

        assertEquals(listOf("ok"), newStore(dir).contents())
    }

    @Test
    fun overlongEntriesAreTruncated() {
        val store = newStore()
        val huge = "x".repeat(200_000)
        store.append(4, huge)

        val content = store.contents().single()

        assertTrue(content.length < huge.length)
        assertTrue(content.startsWith("x".repeat(1000)))
        assertTrue(content.contains("truncated"))
    }

    /**
     * 回归测试（真机上发现）：解析每一行时不能一路扫描到文件末尾，
     * 否则读取日志文件是 O(行数 × 文件大小)：真机上一个写满的 2MB 日志文件就能让应用启动时主线程卡死。
     *
     * 桌面 JVM 的 String.indexOf 做了向量化，2MB 不足以暴露问题，所以这里用远大于上限的文件，
     * 让二次方复杂度在 JVM 上也会超时，而线性实现只需要几百毫秒。
     */
    @Test(timeout = 10_000)
    fun parsingTimeDoesNotGrowQuadraticallyWithFileSize() {
        val dir = File(folder.root, "huge")
        dir.mkdirs()
        val padding = "x".repeat(80)
        val text = StringBuilder()
        var count = 0
        while (text.length < 32 * 1024 * 1024) {
            text.append("3\tentry ").append(count++).append(' ').append(padding).append('\n')
        }
        File(dir, "console.log").writeText(text.toString())
        val store = newStore(dir)

        // 请求全部条数，会解析整个文件，这是会暴露二次方复杂度的路径
        val everything = store.readRecent(Int.MAX_VALUE)
        assertEquals(count, everything.size)
        assertEquals("entry ${count - 1} $padding", everything.last().content)

        // 只请求最新的一部分时，结果必须和全量解析的末尾一致
        val recent = store.readRecent(5000)
        assertEquals(everything.takeLast(5000).map { it.content }, recent.map { it.content })
    }
}
