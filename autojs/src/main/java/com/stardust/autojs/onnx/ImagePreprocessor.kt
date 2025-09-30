// autojs/src/main/java/com/stardust/autojs/onnx/ImagePreprocessor.kt
package com.stardust.autojs.onnx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log

object ImagePreprocessor {

    // 预处理配置数据类
    data class PreprocessConfig(
        val inputSize: Int,
        val normalizationType: String = "none", // none, imagenet, custom
        val resizeMethod: String = "direct", // direct, center_crop, letterbox
        val mean: FloatArray = floatArrayOf(0f, 0f, 0f),
        val std: FloatArray = floatArrayOf(1f, 1f, 1f),
        val pixelRange: String = "0_1" // 0_1, minus1_1, imagenet
    ) {
        override fun toString(): String {
            return "PreprocessConfig(size=$inputSize, norm=$normalizationType, resize=$resizeMethod, range=$pixelRange)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PreprocessConfig

            if (inputSize != other.inputSize) return false
            if (normalizationType != other.normalizationType) return false
            if (resizeMethod != other.resizeMethod) return false
            if (pixelRange != other.pixelRange) return false
            if (!mean.contentEquals(other.mean)) return false
            if (!std.contentEquals(other.std)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = inputSize
            result = 31 * result + normalizationType.hashCode()
            result = 31 * result + resizeMethod.hashCode()
            result = 31 * result + pixelRange.hashCode()
            result = 31 * result + mean.contentHashCode()
            result = 31 * result + std.contentHashCode()
            return result
        }
    }

    /**
     * 从模型元数据创建预处理配置
     */
    fun createConfigFromMetadata(metadata: Map<String, String>?): PreprocessConfig {
        if (metadata == null) {
            Log.w("ImagePreprocessor", "元数据为空，使用默认配置")
            return PreprocessConfig(inputSize = 224)
        }

        // 1. 从元数据解析输入尺寸
        val inputSize = parseInputSizeFromMetadata(metadata)
        
        // 2. 检测模型类型并选择相应配置
        return detectModelConfig(metadata, inputSize).also { config ->
            Log.d("ImagePreprocessor", "从元数据创建配置: $config")
        }
    }

    /**
     * 从元数据解析输入尺寸
     */
    private fun parseInputSizeFromMetadata(metadata: Map<String, String>): Int {
        // 优先从 imgsz 读取
        val imgszValue = metadata["imgsz"]
        if (imgszValue != null) {
            try {
                val size = parseImageSize(imgszValue)
                if (size > 0) {
                    Log.d("ImagePreprocessor", "从imgsz解析到输入尺寸: $size")
                    return size
                }
            } catch (e: Exception) {
                Log.w("ImagePreprocessor", "解析imgsz失败: $imgszValue", e)
            }
        }

        // 尝试从其他键读取
        val sizeKeys = listOf("input_size", "img_size", "input_shape")
        for (key in sizeKeys) {
            val value = metadata[key]
            if (value != null) {
                try {
                    val size = parseImageSize(value)
                    if (size > 0) {
                        Log.d("ImagePreprocessor", "从 $key 解析到输入尺寸: $size")
                        return size
                    }
                } catch (e: Exception) {
                    Log.w("ImagePreprocessor", "解析 $key 失败: $value", e)
                }
            }
        }

        // 最终备用：使用默认尺寸
        val defaultSize = 224
        Log.w("ImagePreprocessor", "无法从元数据确定输入尺寸，使用默认: $defaultSize")
        return defaultSize
    }

    /**
     * 解析图像尺寸
     */
    private fun parseImageSize(value: String): Int {
        return try {
            Log.d("ImagePreprocessor", "解析图像尺寸: $value")
            
            when {
                value.toIntOrNull() != null -> {
                    // 直接是数字: 32, 128, 224, etc.
                    value.toInt()
                }
                value.startsWith("[") && value.endsWith("]") -> {
                    // 形状数组: [128, 128] 或 [1,3,32,32]
                    val numbers = value.removeSurrounding("[", "]")
                        .split(",")
                        .map { it.trim().toIntOrNull() }
                        .filterNotNull()
                    
                    when (numbers.size) {
                        2 -> numbers[0] // [128, 128]
                        4 -> numbers[2] // [1,3,32,32] 取32
                        else -> numbers.firstOrNull() ?: -1
                    }
                }
                value.startsWith("(") && value.endsWith(")") -> {
                    // 元组格式: (3,32,32) 或 (3,128,128)
                    val numbers = value.removeSurrounding("(", ")")
                        .split(",")
                        .map { it.trim().toIntOrNull() }
                        .filterNotNull()
                    
                    when (numbers.size) {
                        3 -> numbers[1] // (3,32,32) 取32
                        else -> numbers.firstOrNull() ?: -1
                    }
                }
                else -> -1
            }.also { size ->
                if (size > 0) {
                    Log.d("ImagePreprocessor", "解析到的尺寸: $size")
                } else {
                    Log.w("ImagePreprocessor", "无法解析尺寸: $value")
                }
            }
        } catch (e: Exception) {
            Log.w("ImagePreprocessor", "解析图像尺寸失败: $value", e)
            -1
        }
    }

    /**
     * 检测模型类型并返回相应配置
     */
    private fun detectModelConfig(metadata: Map<String, String>, inputSize: Int): PreprocessConfig {
        val description = metadata["description"]?.toLowerCase() ?: ""
        val task = metadata["task"]?.toLowerCase() ?: ""
        
        // YOLOv8 分类模型
        if ((description.contains("yolov8") && task == "classify") || 
            description.contains("cls") || 
            task == "classify") {
            
            Log.d("ImagePreprocessor", "检测到YOLOv8分类模型")
            return PreprocessConfig(
                inputSize = inputSize,
                normalizationType = "none", // YOLOv8分类模型通常不需要归一化
                resizeMethod = if (inputSize <= 128) "direct" else "center_crop",
                pixelRange = "0_1"
            )
        }
        
        // YOLOv8 目标检测模型
        if ((description.contains("yolov8") && task == "detect") || 
            description.contains("detect") || 
            task == "detect") {
            
            Log.d("ImagePreprocessor", "检测到YOLOv8目标检测模型")
            return PreprocessConfig(
                inputSize = inputSize,
                normalizationType = "none", // YOLO检测模型通常不需要归一化
                resizeMethod = "letterbox", // 检测模型使用letterbox保持宽高比
                pixelRange = "0_1"
            )
        }
        
        // ImageNet 标准模型
        if (description.contains("imagenet") || 
            description.contains("resnet") ||
            description.contains("mobilenet") ||
            description.contains("efficientnet")) {
            
            Log.d("ImagePreprocessor", "检测到ImageNet标准模型")
            return PreprocessConfig(
                inputSize = inputSize,
                normalizationType = "imagenet",
                resizeMethod = "center_crop",
                mean = floatArrayOf(0.485f, 0.456f, 0.406f),
                std = floatArrayOf(0.229f, 0.224f, 0.225f),
                pixelRange = "imagenet"
            )
        }
        
        // 默认配置 - 基于输入尺寸的启发式规则
        Log.d("ImagePreprocessor", "使用基于尺寸的默认配置")
        return PreprocessConfig(
            inputSize = inputSize,
            normalizationType = if (inputSize <= 160) "none" else "imagenet",
            resizeMethod = if (inputSize <= 128) "direct" else "center_crop",
            pixelRange = "0_1"
        )
    }

    /**
     * 使用配置进行预处理
     */
    fun preprocessWithConfig(bitmap: Bitmap, config: PreprocessConfig): FloatArray {
        Log.d("ImagePreprocessor", "开始预处理: $config")

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
            }

            result[idx++] = r
            result[idx++] = g
            result[idx++] = b
        }

        Log.d("ImagePreprocessor", "预处理完成 - 输出范围: [${result.minOrNull()}, ${result.maxOrNull()}]")
        return hwcToChw(result, config.inputSize, config.inputSize)
    }

    /**
     * 智能预处理 - 基于元数据的完全自动化
     */
    fun preprocessSmart(bitmap: Bitmap, metadata: Map<String, String>?): FloatArray {
        val config = createConfigFromMetadata(metadata)
        Log.d("ImagePreprocessor", "智能预处理 - 配置: $config")
        return preprocessWithConfig(bitmap, config)
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
        
        val canvas = Canvas(result)
        canvas.drawColor(0xFF000000.toInt()) // 黑色填充
        
        val left = (targetSize - newW) / 2
        val top = (targetSize - newH) / 2
        canvas.drawBitmap(resized, left.toFloat(), top.toFloat(), null)
        
        if (resized != bitmap) {
            resized.recycle()
        }
        
        return result
    }

    // 保持原有的专用方法（向后兼容）
    fun preprocessClassification32x32(bitmap: Bitmap): FloatArray {
        return preprocessWithConfig(bitmap, PreprocessConfig(inputSize = 32))
    }

    fun preprocessClassification128x128(bitmap: Bitmap): FloatArray {
        return preprocessWithConfig(bitmap, PreprocessConfig(inputSize = 128))
    }

    fun preprocessClassification(bitmap: Bitmap, inputSize: Int = 224): FloatArray {
        return preprocessWithConfig(bitmap, PreprocessConfig(inputSize = inputSize))
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
