package com.stardust.autojs.core.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleLogFormatterTest {
    private fun entries(vararg contents: String) = contents.mapIndexed { index, content ->
        LogEntry(id = index, level = 4, content = content, newLine = true)
    }

    @Test
    fun joinPutsEachEntryOnItsOwnLine() {
        assertEquals("a\nb\nc", ConsoleLogFormatter.join(entries("a", "b", "c")))
    }

    @Test
    fun joinKeepsNewlinesInsideAnEntry() {
        assertEquals("a1\na2\nb", ConsoleLogFormatter.join(entries("a1\na2", "b")))
    }

    @Test
    fun joinOfNothingIsEmpty() {
        assertEquals("", ConsoleLogFormatter.join(emptyList()))
    }

    @Test
    fun joinLatestReturnsEverythingWhenItFits() {
        val result = ConsoleLogFormatter.joinLatest(entries("a", "b", "c"), 100)

        assertEquals("a\nb\nc", result.text)
        assertEquals(3, result.count)
    }

    @Test
    fun joinLatestKeepsTheNewestEntriesThatFit() {
        // 每条 4 个字符 + 1 个换行符，限制 10 个字符时只放得下最新的 2 条
        val result = ConsoleLogFormatter.joinLatest(entries("aaaa", "bbbb", "cccc", "dddd"), 10)

        assertEquals("cccc\ndddd", result.text)
        assertEquals(2, result.count)
    }

    @Test
    fun joinLatestAlwaysReturnsTheNewestEntryEvenIfItIsTooLong() {
        val result = ConsoleLogFormatter.joinLatest(entries("old", "0123456789"), 4)

        assertEquals("6789", result.text)
        assertEquals(1, result.count)
    }

    @Test
    fun joinLatestOfNothingIsEmpty() {
        val result = ConsoleLogFormatter.joinLatest(emptyList(), 100)

        assertEquals("", result.text)
        assertEquals(0, result.count)
    }

    @Test
    fun buildExportContainsHeaderCountAndLogs() {
        val text = ConsoleLogFormatter.buildExport("My App console log\nApp version: 1.0\n", entries("a", "b"))

        assertTrue(text.startsWith("My App console log\nApp version: 1.0\n"))
        assertTrue(text.contains("Log entries: 2"))
        assertTrue(text.endsWith("a\nb\n"))
    }

    @Test
    fun exportFileNameLooksLikeATimestampedTextFile() {
        val name = ConsoleLogFormatter.exportFileName(0L)

        assertTrue(name, Regex("autox-log-\\d{8}-\\d{6}\\.txt").matches(name))
    }
}
