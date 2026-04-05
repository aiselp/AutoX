package com.equationl.paddleocr4android.Util.paddle

import android.graphics.Point

class OcrResultModel {
    val points: MutableList<Point> = ArrayList()
    val wordIndex: MutableList<Int> = ArrayList()
    var label: String? = null
    @JvmField
    var confidence: Float = 0f
    var clsIdx: Float = 0f
    var clsLabel: String? = null
    var clsConfidence: Float = 0f

    fun addPoints(x: Int, y: Int) {
        val point = Point(x, y)
        points.add(point)
    }

    fun addWordIndex(index: Int) {
        wordIndex.add(index)
    }
}
