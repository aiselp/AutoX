package org.autojs.autoxjs.mcp

data class ScreenshotInfo(
    val path: String,
    val width: Int,
    val height: Int,
    val timestamp: Long
)

class ScreenshotStore {
    @Volatile
    private var last: ScreenshotInfo? = null

    fun update(info: ScreenshotInfo) {
        last = info
    }

    fun get(): ScreenshotInfo? = last
}
