// autojs/src/main/java/com/stardust/autojs/onnx/ImagePreprocessor.kt
package com.stardust.autojs.onnx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

object ImagePreprocessor {

    // 预处理配置数据类
    data class PreprocessConfig(
        val inputSize: Int,
        val normalizationType: String = "auto", // auto, none, imagenet, custom
        val resizeMethod: String = "direct", // direct, center_crop, letterbox
        val mean: FloatArray = floatArrayOf(0f, 0f, 0f),
        val std: FloatArray = floatArrayOf(1f, 1f, 1f)
    ) {
        // 常用预处理配置预设
        companion object {
            // YOLOv8 分类模型常用配置
            val YOLOV8_CLS_32 = PreprocessConfig(
                inputSize = 32,
                normalizationType = "none", // 小尺寸模型通常不需要复杂归一化
                resizeMethod = "direct"
            )
            
            val YOLOV8_CLS_128 = PreprocessConfig(
                inputSize = 128,
                normalizationType = "none",
                resizeMethod = "direct"
            )
            
            val YOLOV8_CLS_224 = PreprocessConfig(
                inputSize = 224,
                normalizationType = "imagenet",
                resizeMethod = "center_crop",
                mean = floatArrayOf(0.485f, 0.456f, 0.406f),
                std = floatArrayOf(0.229f, 0.224f, 0.225f)
            )
            
            // ImageNet 标准配置
            val IMAGENET_STANDARD = PreprocessConfig(
                inputSize = 224,
                normalizationType = "imagenet",
                resizeMethod = "center_crop",
                mean = floatArrayOf(0.485f, 0.456f, 0.406f),
                std = floatArrayOf(0.229f, 0.224f, 0.225f)
            )
            
            // 无归一化配置
            val NO_NORMALIZATION = PreprocessConfig(
                inputSize = 224,
                normalizationType = "none",
                resizeMethod = "direct"
            )
        }
    }

    /**
     * 智能预处理 - 根据输入尺寸自动选择最佳配置
     */
    fun preprocessSmart(bitmap: Bitmap, inputSize: Int): FloatArray {
        val config = autoDetectConfig(inputSize)
        Log.d("ImagePreprocessor", "智能预处理 - 尺寸: $inputSize, 配置: ${config.normalizationType}/${config.resizeMethod}")
        return preprocessWithConfig(bitmap, config)
    }

    /**
     * 自动检测最佳预处理配置
     */
    private fun autoDetectConfig(inputSize: Int): PreprocessConfig {
        return when (inputSize) {
            in 1..64 -> {
                // 小尺寸模型通常训练时使用简单预处理
                PreprocessConfig.YOLOV8_CLS_32.copy(inputSize = inputSize)
            }
            in 65..160 -> {
                // 中等尺寸模型
                PreprocessConfig.YOLOV8_CLS_128.copy(inputSize = inputSize)
            }
            else -> {
                // 大尺寸模型通常使用ImageNet标准预处理
                PreprocessConfig.IMAGENET_STANDARD.copy(inputSize = inputSize)
            }
        }.also { config ->
            Log.d("ImagePreprocessor", "自动检测配置: 尺寸=$inputSize, 归一化=${config.normalizationType}, 缩放=${config.resizeMethod}")
        }
    }

    /**
     * 使用配置进行预处理
     */
    fun preprocessWithConfig(bitmap: Bitmap, config: PreprocessConfig): FloatArray {
        val resized = when (config.resizeMethod) {
            "direct" -> resizeDirect(bitmap, config.inputSize)
            "center_crop" -> resizeWithCenterCrop(bitmap, config.inputSize)
            "letterbox" -> resizeWithLetterBox(bitmap, config.inputSize)
            else -> resizeDirect(bitmap, config.inputSize)
        }

        val pixels = IntArray(config.inputSize * config.inputSize)
        resized.getPixels(pixels, 0, config.inputSize, 0, 0, config.inputSize, config.inputSize)
        
        if (resized != bitmap) {
            resized.recycle()
        }

        val result = FloatArray(3 * config.inputSize * config.inputSize)
        var idx = 0
        
        for (pixel in pixels) {
            var r = (pixel shr 16 and 0xFF) / 255f
            var g = (pixel shr 8 and 0xFF) / 255f
            var b = (pixel and 0xFF) / 255f

            // 应用归一化
            when (config.normalizationType) {
                "none" -> {
                    // 保持0-1范围
                }
                "imagenet" -> {
                    r = (r - config.mean[0]) / config.std[0]
                    g = (g - config.mean[1]) / config.std[1]
                    b = (b - config.mean[2]) / config.std[2]
                }
                "custom" -> {
                    r = (r - 0.5f) / 0.5f
                    g = (g - 0.5f) / 0.5f
                    b = (b - 0.5f) / 0.5f
                }
                "auto" -> {
                    // 自动选择：小尺寸用none，大尺寸用imagenet
                    if (config.inputSize <= 128) {
                        // 保持0-1
                    } else {
                        r = (r - 0.485f) / 0.229f
                        g = (g - 0.456f) / 0.224f
                        b = (b - 0.406f) / 0.225f
                    }
                }
            }

            result[idx++] = r
            result[idx++] = g
            result[idx++] = b
        }

        Log.d("ImagePreprocessor", "预处理完成 - 尺寸: ${config.inputSize}, 归一化: ${config.normalizationType}")
        return hwcToChw(result, config.inputSize, config.inputSize)
    }

    /**
     * 直接缩放
     */
    private fun resizeDirect(bitmap: Bitmap, targetSize: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
    }

    /**
     * 中心裁剪缩放
     */
    private fun resizeWithCenterCrop(bitmap: Bitmap, targetSize: Int): Bitmap {
        val scale = (targetSize * 1.2f) / kotlin.math.min(bitmap.width, bitmap.height)
        val newW = (bitmap.width * scale).toInt()
        val newH = (bitmap.height * scale).toInt()
        val resized = Bitmap.createScaledBitmap(bitmap, newW, newH, true)

        val cropX = (newW - targetSize) / 2
        val cropY = (newH - targetSize) / 2
        val cropped = Bitmap.createBitmap(resized, cropX, cropY, targetSize, targetSize)
        
        if (resized != bitmap) {
            resized.recycle()
        }
        
        return cropped
    }

    /**
     * LetterBox缩放（保持宽高比）
     */
    private fun resizeWithLetterBox(bitmap: Bitmap, targetSize: Int): Bitmap {
        val scale = targetSize.toFloat() / kotlin.math.max(bitmap.width, bitmap.height)
        val newW = (bitmap.width * scale).toInt()
        val newH = (bitmap.height * scale).toInt()
        
        val resized = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        val result = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        
        val canvas = android.graphics.Canvas(result)
        canvas.drawColor(0xFF000000.toInt()) // 黑色填充
        
        val left = (targetSize - newW) / 2
        val top = (targetSize - newH) / 2
        canvas.drawBitmap(resized, left.toFloat(), top.toFloat(), null)
        
        if (resized != bitmap) {
            resized.recycle()
        }
        
        return result
    }

    /**
     * 从模型元数据推断预处理配置
     */
    fun detectConfigFromMetadata(metadata: Map<String, String>?, inputSize: Int): PreprocessConfig {
        if (metadata == null) {
            return autoDetectConfig(inputSize)
        }

        // 根据模型描述推断预处理方式
        val description = metadata["description"]?.toLowerCase() ?: ""
        val task = metadata["task"]?.toLowerCase() ?: ""
        
        return when {
            // YOLOv8 分类模型
            description.contains("yolov8") && task == "classify" -> {
                when (inputSize) {
                    in 1..64 -> PreprocessConfig.YOLOV8_CLS_32.copy(inputSize = inputSize)
                    in 65..160 -> PreprocessConfig.YOLOV8_CLS_128.copy(inputSize = inputSize)
                    else -> PreprocessConfig.YOLOV8_CLS_224.copy(inputSize = inputSize)
                }
            }
            // 包含ImageNet关键词
            description.contains("imagenet") -> {
                PreprocessConfig.IMAGENET_STANDARD.copy(inputSize = inputSize)
            }
            // 其他情况自动检测
            else -> autoDetectConfig(inputSize)
        }.also { config ->
            Log.d("ImagePreprocessor", "从元数据推断配置: $config")
        }
    }

    // 保持原有的专用方法（向后兼容）
    fun preprocessClassification32x32(bitmap: Bitmap): FloatArray {
        return preprocessSmart(bitmap, 32)
    }

    fun preprocessClassification128x128(bitmap: Bitmap): FloatArray {
        return preprocessSmart(bitmap, 128)
    }

    fun preprocessClassification(bitmap: Bitmap, inputSize: Int = 224): FloatArray {
        return preprocessSmart(bitmap, inputSize)
    }

    // YOLOv8检测预处理保持不变
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
