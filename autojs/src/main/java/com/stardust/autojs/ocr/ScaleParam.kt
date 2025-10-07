//autojs/src/main/java/com/stardust/autojs/ocr/ScaleParam.kt
package com.stardust.autojs.ocr

data class ScaleParam(
    val srcWidth: Int,
    val srcHeight: Int,
    val dstWidth: Int,
    val dstHeight: Int,
    val ratioWidth: Float,
    val ratioHeight: Float,
)
