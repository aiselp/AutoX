package com.equationl.ncnnandroidppocr.bean

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import kotlin.math.abs

data class NcnnOcrResult(
    val text: String,
    val inferenceTime: Long,
    val textLines: List<OcrTextLineResult>,
    val drawBitmap: Bitmap? = null
)

data class OcrTextLineResult(
    val points: List<Point>,
    val text: String,
    val confidence: Float,
    val orientation: Int,
    val textList: List<OcrTextResult>
)

data class OcrTextResult(
    val text: String,
    val id: Int,
    val confidence: Float
)

data class OcrResult(
    @JvmField
    val confidence: Float,
    @JvmField
    val preprocessTime: Float = 0f,
    @JvmField
    val inferenceTime: Float,
    @JvmField
    val text: String,
    @JvmField
    val bounds: Rect,
    @JvmField
    val drawBitmap: Bitmap? = null
) : Comparable<OcrResult> {

    @Deprecated("use text", ReplaceWith("text"))
    @JvmField
    val words: String = text

    @Deprecated("use text", ReplaceWith("text"))
    fun getWords() = text

    @Deprecated("use confidence", ReplaceWith("confidence"))
    fun getConfidence() = confidence

    @Deprecated("use preprocessTime", ReplaceWith("preprocessTime"))
    fun getPreprocessTime() = preprocessTime

    @Deprecated("use inferenceTime", ReplaceWith("inferenceTime"))
    fun getInferenceTime() = inferenceTime

    @Deprecated("use bounds", ReplaceWith("bounds"))
    fun getBounds() = bounds

    override fun compareTo(other: OcrResult): Int {
        val deviation = (bounds.height() / 2f).coerceAtLeast(other.bounds.height() / 2f)
        return if (abs((bounds.top + bounds.bottom) / 2f - (other.bounds.top + other.bounds.bottom) / 2f) < deviation) {
            bounds.left - other.bounds.left
        } else {
            bounds.bottom - other.bounds.bottom
        }
    }
}