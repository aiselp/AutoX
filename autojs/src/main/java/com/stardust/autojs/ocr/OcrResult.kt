//autojs/src/main/java/com/stardust/autojs/ocr/OcrResult.kt
package com.stardust.autojs.ocr

import android.graphics.Bitmap
import org.opencv.core.Point

data class OcrResult(
    val detResults: List<DetResult>,
    val clsResults: List<ClsResult>,
    val recResults: List<RecResult>,
    val detTime: Double,
    val clsTime: Double,
    val recTime: Double,
    val fullTime: Double,
    val boxImage: Bitmap,
    val text: String,
    // 新增字段
    val textWithCoordinates: String = "",
    val textBlocksJson: String = "" // 将复杂对象序列化为JSON字符串
)

data class DetPoint(var x: Int, var y: Int) {
    fun toCvPoint() = Point(x.toDouble(), y.toDouble())
}

data class DetResult(
    val points: List<DetPoint> = listOf(),
    val score: Float,
)

data class ClsResult(
    val index: Int,
    val score: Float,
) {
    val indexDirection: String get() = if (index == 0) "↑" else "↓"
}

data class RecResult(
    val text: String,
    val charScores: List<Float>,
)

// 新增数据类用于文本块信息
data class TextBlock(
    val text: String,
    val score: Float,
    val coordinates: List<Coordinate>,
    val detScore: Float
)

data class Coordinate(
    val x: Int,
    val y: Int
)
