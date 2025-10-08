//autojs/src/main/java/com/stardust/autojs/ocr/OcrResult.kt
package com.stardust.autojs.ocr

import android.graphics.Bitmap
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.opencv.core.Point

@Parcelize
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
    // 新增字段 - 使用基本类型和Parcelable类型
    val textWithCoordinates: String = "",
    val textBlocksJson: String = "" // 将复杂对象序列化为JSON字符串
) : Parcelable

@Parcelize
data class DetPoint(var x: Int, var y: Int) : Parcelable {
    fun toCvPoint() = Point(x.toDouble(), y.toDouble())
}

@Parcelize
data class DetResult(
    val points: List<DetPoint> = listOf(),
    val score: Float,
) : Parcelable

@Parcelize
data class ClsResult(
    val index: Int,
    val score: Float,
) : Pararcelable {
    val indexDirection: String get() = if (index == 0) "↑" else "↓"
}

@Parcelize
data class RecResult(
    val text: String,
    val charScores: List<Float>,
) : Parcelable

// 新增数据类用于文本块信息
@Parcelize
data class TextBlock(
    val text: String,
    val score: Float,
    val coordinates: List<Coordinate>,
    val detScore: Float
) : Parcelable

@Parcelize
data class Coordinate(
    val x: Int,
    val y: Int
) : Parcelable
