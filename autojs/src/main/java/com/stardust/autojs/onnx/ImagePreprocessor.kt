// autojs/src/main/java/com/stardust/autojs/onnx/ImagePreprocessor.kt
package com.stardust.autojs.onnx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

object ImagePreprocessor {

    fun preprocessYoloV8(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        val pixels = IntArray(targetWidth * targetHeight)
        resized.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        resized.recycle()

        val result = FloatArray(3 * targetWidth * targetHeight)
        var idx = 0
        for (pixel in pixels) {
            result[idx++] = ((pixel shr 16 and 0xFF) / 255f)
            result[idx++] = ((pixel shr 8 and 0xFF) / 255f)  
            result[idx++] = ((pixel and 0xFF) / 255f)
        }
        return hwcToChw(result, targetHeight, targetWidth)
    }

    // 为你的分类模型添加专用预处理（32x32输入）
    fun preprocessClassification32x32(bitmap: Bitmap): FloatArray {
        val inputSize = 32
        // 直接缩放到32x32
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        resized.recycle()

        val result = FloatArray(3 * inputSize * inputSize)
        var idx = 0
        
        // 尝试不同的归一化方式
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255f
            val g = (pixel shr 8 and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            // 方式1: 直接使用0-1范围（适用于某些自定义模型）
            result[idx++] = r
            result[idx++] = g
            result[idx++] = b
            
            // 如果方式1不行，可以尝试注释上面三行，使用下面的ImageNet归一化：
            // result[idx++] = (r - 0.485f) / 0.229f
            // result[idx++] = (g - 0.456f) / 0.224f  
            // result[idx++] = (b - 0.406f) / 0.225f
        }
        
        Log.d("ImagePreprocessor", "分类预处理完成，输入尺寸: 32x32")
        return hwcToChw(result, inputSize, inputSize)
    }

    // 保留原有的224x224预处理
    fun preprocessClassification(bitmap: Bitmap, inputSize: Int = 224): FloatArray {
        // Resize short edge to 256
        val scale = 256f / kotlin.math.min(bitmap.width, bitmap.height)
        val newW = (bitmap.width * scale).toInt()
        val newH = (bitmap.height * scale).toInt()
        val resized = Bitmap.createScaledBitmap(bitmap, newW, newH, true)

        // Center crop
        val cropX = (newW - inputSize) / 2
        val cropY = (newH - inputSize) / 2
        val cropped = Bitmap.createBitmap(resized, cropX, cropY, inputSize, inputSize)
        resized.recycle()

        val pixels = IntArray(inputSize * inputSize)
        cropped.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        cropped.recycle()

        val result = FloatArray(3 * inputSize * inputSize)
        var idx = 0
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255f
            val g = (pixel shr 8 and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            // ImageNet normalization
            result[idx++] = (r - 0.485f) / 0.229f
            result[idx++] = (g - 0.456f) / 0.224f  
            result[idx++] = (b - 0.406f) / 0.225f
        }
        return hwcToChw(result, inputSize, inputSize)
    }

    private fun hwcToChw(hwc: FloatArray, h: Int, w: Int): FloatArray {
        val chw = FloatArray(hwc.size)
        val pixels = h * w
        for (i in 0 until pixels) {
            chw[i] = hwc[i * 3]
            chw[pixels + i] = hwc[i * 3 + 1]
            chw[pixels * 2 + i] = hwc[i * 3 + 2]
        }
        return chw
    }
}
