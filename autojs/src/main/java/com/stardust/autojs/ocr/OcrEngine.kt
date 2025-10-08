//autojs/src/main/java/com/stardust/autojs/ocr/OcrEngine.kt
package com.stardust.autojs.ocr

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.android.Utils.matToBitmap
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.TickMeter
import org.opencv.imgproc.Imgproc.*
import java.io.Closeable
import java.lang.Integer.max
import java.lang.Integer.min
import kotlin.math.max as floatMax
import kotlin.math.min as floatMin
import org.json.JSONArray
import org.json.JSONObject

class OcrEngine(context: Context) : Closeable {

    private val TAG = "OcrEngine"
    private val assetManager: AssetManager = context.assets

    private val ortEnv by lazy { OrtEnvironment.getEnvironment() }

    private val det by lazy { 
        try {
            Det(ortEnv, assetManager, MODELS_DIR + DET_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load detection model: ${e.message}")
            throw e
        }
    }

    private val cls by lazy { 
        try {
            Cls(ortEnv, assetManager, MODELS_DIR + CLS_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load classification model: ${e.message}")
            throw e
        }
    }

    private val rec by lazy { 
        try {
            Rec(ortEnv, assetManager, MODELS_DIR + REC_NAME, MODELS_DIR + KEYS_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load recognition model: ${e.message}")
            throw e
        }
    }

    // 添加初始化状态检查
    private var isInitialized = false
    private var initError: String? = null

    init {
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV library found inside package.")
            try {
                // 预加载模型以检查是否可用
                checkModelsAvailable()
                isInitialized = true
                Log.i(TAG, "OCR Engine initialized successfully")
            } catch (e: Exception) {
                initError = e.message
                Log.e(TAG, "OCR Engine initialization failed: ${e.message}")
            }
        } else {
            val error = "Internal OpenCV library not found."
            Log.e(TAG, error)
            initError = error
        }
    }

    private fun checkModelsAvailable() {
        val models = listOf(
            MODELS_DIR + DET_NAME,
            MODELS_DIR + CLS_NAME, 
            MODELS_DIR + REC_NAME,
            MODELS_DIR + KEYS_NAME
        )
        
        // 先检查models目录是否存在
        val dirList = assetManager.list(MODELS_DIR)
        Log.i(TAG, "Files in $MODELS_DIR: ${dirList?.joinToString()}")
        
        models.forEach { modelPath ->
            try {
                val inputStream = assetManager.open(modelPath)
                val fileSize = inputStream.available()
                Log.i(TAG, "Model $modelPath found, size: $fileSize bytes")
                inputStream.close()
            } catch (e: Exception) {
                Log.e(TAG, "Model $modelPath not found: ${e.message}")
                // 列出所有可用的文件
                val availableFiles = assetManager.list("")
                Log.i(TAG, "Available root files: ${availableFiles?.joinToString()}")
                throw RuntimeException("Model file $modelPath not found or inaccessible: ${e.message}")
            }
        }
    }

    override fun close() {
        ortEnv.close()
    }

    // 添加初始化状态检查方法
    @android.webkit.JavascriptInterface
    fun isReady(): String {
        return if (isInitialized) {
            """{"ready": true, "message": "OCR Engine is ready"}"""
        } else {
            """{"ready": false, "error": "$initError"}"""
        }
    }

    // 主要的重载方法 - 接受 ImageWrapper
    @android.webkit.JavascriptInterface
    fun detect(imageWrapper: com.stardust.autojs.core.image.ImageWrapper): String {
        // 使用官方推荐的默认参数
        return detect(imageWrapper, true, 960, 0, 0.3f, 0.6f, 1.5f, true, true)
    }

    // 重载方法 - 接受 ImageWrapper 和完整参数
    @android.webkit.JavascriptInterface
    fun detect(
        imageWrapper: com.stardust.autojs.core.image.ImageWrapper,
        scaleUp: Boolean,
        maxSideLen: Int,
        padding: Int,
        boxScoreThresh: Float,
        boxThresh: Float,
        unClipRatio: Float,
        doCls: Boolean,
        mostCls: Boolean
    ): String {
        Log.i(TAG, "=====Prepare from ImageWrapper=====")
        
        // 检查初始化状态
        if (!isInitialized) {
            return """{"error": "OCR Engine not initialized: $initError", "success": false}"""
        }
        
        Log.i(TAG, "ImageWrapper size: ${imageWrapper.width}x${imageWrapper.height}")
        
        try {
            val bitmap = imageWrapper.bitmap
            if (bitmap == null) {
                Log.e(TAG, "ImageWrapper bitmap is null")
                return """{"error": "ImageWrapper bitmap is null", "success": false}"""
            }
            
            return detect(bitmap, scaleUp, maxSideLen, padding, boxScoreThresh, boxThresh, unClipRatio, doCls, mostCls)
        } catch (e: Exception) {
            Log.e(TAG, "detect from ImageWrapper error: ${e.message}")
            return """{"error": "ImageWrapper conversion failed: ${e.message}", "success": false}"""
        }
    }

    // 原有的 Bitmap 方法 - 使用官方推荐参数作为默认值
    @android.webkit.JavascriptInterface
    fun detect(
        bmp: Bitmap,
        scaleUp: Boolean = true,
        maxSideLen: Int = 960,        // 官方推荐 resize_long: 960
        padding: Int = 0,             // 官方配置无padding
        boxScoreThresh: Float = 0.3f, // 官方 thresh: 0.3
        boxThresh: Float = 0.6f,      // 官方 box_thresh: 0.6
        unClipRatio: Float = 1.5f,    // 官方 unclip_ratio: 1.5
        doCls: Boolean = true,
        mostCls: Boolean = true
    ): String {
        Log.i(TAG, "=====Prepare from Bitmap=====")
        
        // 检查初始化状态
        if (!isInitialized) {
            return """{"error": "OCR Engine not initialized: $initError", "success": false}"""
        }
        
        Log.i(TAG, "Input bitmap size: ${bmp.width}x${bmp.height}")
        Log.i(TAG, "Parameter: scaleUp($scaleUp), maxSideLen($maxSideLen), padding($padding),boxScoreThresh($boxScoreThresh),boxThresh($boxThresh),unClipRatio($unClipRatio),doCls($doCls),mostCls($mostCls)")

        try {
            Log.i(TAG, "---------- step: input Bitmap -> Mat(RGBA) -> Mat(BGR) ----------")
            val inputRGBA = Mat(bmp.width, bmp.height, CvType.CV_8UC4)
            Utils.bitmapToMat(bmp, inputRGBA)
            val inputBGR = Mat()
            cvtColor(inputRGBA, inputBGR, COLOR_RGBA2BGR)

            Log.i(TAG, "---------- step: Resize ----------")
            val originMaxSide = max(inputBGR.cols(), inputBGR.rows())
            
            // 智能图像尺寸分类处理
            val imageType = when {
                originMaxSide < 300 -> "SMALL"
                originMaxSide > 1500 -> "LARGE"
                else -> "MEDIUM"
            }
            
            Log.i(TAG, "Image type: $imageType, size: $originMaxSide")
            
            // 根据图像类型优化参数，但保持官方参数为核心
            val (optimizedMaxSideLen, optimizedPadding) = when (imageType) {
                "SMALL" -> Pair(
                    min(maxSideLen, 800),  // 小图限制最大尺寸
                    max(padding, 10)       // 小图保持最小padding
                )
                "LARGE" -> Pair(
                    if (maxSideLen <= 0) 2560 else maxSideLen,  // 大图允许更大尺寸
                    padding                                    // 保持原padding
                )
                else -> Pair(maxSideLen, padding)              // 中等图片使用原参数
            }
            
            var resize = if (scaleUp) {
                //支持放大和缩小
                if (optimizedMaxSideLen <= 0) originMaxSide else optimizedMaxSideLen
            } else {
                //仅支持缩小
                if (optimizedMaxSideLen <= 0 || originMaxSide < optimizedMaxSideLen) originMaxSide else optimizedMaxSideLen
            }
            resize += 2 * optimizedPadding
            
            Log.i(TAG, "Optimized params - maxSideLen: $optimizedMaxSideLen, padding: $optimizedPadding, resize=$resize")
            
            val paddingRect = Rect(optimizedPadding, optimizedPadding, inputBGR.cols(), inputBGR.rows())
            val paddingSrc = makePadding(inputBGR, optimizedPadding)
            val s = getScaleParam(paddingSrc, resize)
            Log.i(TAG, "$s")

            val ocrResult = fullDetect(paddingSrc, paddingRect, s, boxScoreThresh, boxThresh, unClipRatio, doCls, mostCls)
            Log.i(TAG, "OCR completed, detected ${ocrResult.detResults.size} boxes, text length: ${ocrResult.text.length}")

            // 返回 JSON 字符串
            return """{
                "text": "${ocrResult.text.replace("\"", "\\\"")}",
                "textWithCoordinates": "${ocrResult.textWithCoordinates.replace("\"", "\\\"")}",
                "textBlocks": ${ocrResult.textBlocksJson},
                "fullTime": ${ocrResult.fullTime},
                "detTime": ${ocrResult.detTime},
                "recTime": ${ocrResult.recTime},
                "clsTime": ${ocrResult.clsTime},
                "detectedBoxes": ${ocrResult.detResults.size},
                "imageType": "$imageType",
                "success": true
            }"""
        } catch (e: Exception) {
            Log.e(TAG, "OCR detection error: ${e.message}", e)
            return """{"error": "OCR processing failed: ${e.message}", "success": false}"""
        }
    }

    // 添加专门的小图片检测方法
    @android.webkit.JavascriptInterface
    fun detectSmallImage(
        imageWrapper: com.stardust.autojs.core.image.ImageWrapper
    ): String {
        Log.i(TAG, "=====Small Image Detection=====")
        
        if (!isInitialized) {
            return """{"error": "OCR Engine not initialized: $initError", "success": false}"""
        }
        
        try {
            val bitmap = imageWrapper.bitmap
            if (bitmap == null) {
                return """{"error": "ImageWrapper bitmap is null", "success": false}"""
            }
            
            Log.i(TAG, "Small image size: ${bitmap.width}x${bitmap.height}")
            
            // 小图片专用参数，基于官方参数调整
            return detect(bitmap, 
                scaleUp = true,
                maxSideLen = 800,      // 限制最大尺寸
                padding = 10,          // 最小padding
                boxScoreThresh = 0.2f, // 降低检测阈值
                boxThresh = 0.4f,      // 降低框阈值
                unClipRatio = 1.8f,    // 增加unclip比例
                doCls = false,         // 小图片通常不需要方向分类
                mostCls = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Small image detection error: ${e.message}")
            return """{"error": "Small image processing failed: ${e.message}", "success": false}"""
        }
    }

    // 添加其他便捷方法
    @android.webkit.JavascriptInterface
    fun detectFromBase64(base64Image: String): String {
        // 使用官方推荐参数
        return detectFromBase64(base64Image, true, 960, 0, 0.3f, 0.6f, 1.5f, true, true)
    }

    @android.webkit.JavascriptInterface
    fun detectFromBase64(
        base64Image: String,
        scaleUp: Boolean,
        maxSideLen: Int,
        padding: Int,
        boxScoreThresh: Float,
        boxThresh: Float,
        unClipRatio: Float,
        doCls: Boolean,
        mostCls: Boolean
    ): String {
        try {
            Log.i(TAG, "=====Prepare from Base64=====")
            val imageBytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap == null) {
                return """{"error": "Base64 decode failed", "success": false}"""
            }
            return detect(bitmap, scaleUp, maxSideLen, padding, boxScoreThresh, boxThresh, unClipRatio, doCls, mostCls)
        } catch (e: Exception) {
            Log.e(TAG, "detectFromBase64 error: ${e.message}")
            return """{"error": "Base64 processing failed: ${e.message}", "success": false}"""
        }
    }

    @android.webkit.JavascriptInterface
    fun detectSimple(imageWrapper: com.stardust.autojs.core.image.ImageWrapper): String {
        // 简化的调用，使用官方推荐参数
        return detect(imageWrapper)
    }

    private fun fullDetect(
        src: Mat,
        paddingRect: Rect,
        s: ScaleParam,
        boxScoreThresh: Float,
        boxThresh: Float,
        unClipRatio: Float,
        doCls: Boolean,
        mostCls: Boolean
    ): OcrResult {
        val fullTickMeter = TickMeter().apply { start() }
        val textBoxPaddingImg = src.clone()
        val thickness = getThickness(src)
        Log.i(TAG, "=====Start detect=====")
        Log.i(TAG, "Source image size: ${src.cols()}x${src.rows()}, scale param: $s")

        val detTickMeter = TickMeter().apply { start() }
        Log.i(TAG, "---------- step: Get DetResults ----------")
        
        // 使用官方推荐的参数，不进行动态调整以保持一致性
        Log.i(TAG, "Using official params - boxThresh: $boxThresh, boxScoreThresh: $boxScoreThresh, unClipRatio: $unClipRatio")
        
        val detResults = det.getDetResults(src, s, boxScoreThresh, boxThresh, unClipRatio)
        detTickMeter.stop()

        Log.i(TAG, "Detected ${detResults.size} text boxes")

        // 过滤低质量的检测框
        val filteredDetResults = detResults.filter { it.score > boxScoreThresh }
        if (filteredDetResults.size < detResults.size) {
            Log.i(TAG, "Filtered out ${detResults.size - filteredDetResults.size} low score boxes")
        }

        Log.i(TAG, "---------- step: Draw TextBoxes ----------")
        drawTextBoxes(textBoxPaddingImg, filteredDetResults, thickness)

        Log.i(TAG, "---------- step: Get PartMats ----------")
        val partMats = getPartMats(src, filteredDetResults)
        Log.i(TAG, "Successfully cropped ${partMats.size} part images")

        // 检查裁剪的图像是否有效
        partMats.forEachIndexed { index, mat ->
            if (mat.empty()) {
                Log.w(TAG, "Part image $index is empty")
            } else {
                Log.i(TAG, "Part image $index size: ${mat.cols()}x${mat.rows()}")
            }
        }

        val clsTickMeter = TickMeter().apply { start() }
        val clsResults = if (doCls && partMats.isNotEmpty()) {
            Log.i(TAG, "---------- step: Get ClsResults ----------")
            val results = cls.getClsResults(partMats)
            if (mostCls) {
                results.map {
                    val sum = results.map { it.index }.sum().toFloat()
                    val halfPercent = results.size.toFloat() / 2.0F
                    val mostAngleIndex = if (sum < halfPercent) 0 else 1
                    it.copy(index = mostAngleIndex)
                }
            } else results
        } else emptyList()
        clsTickMeter.stop()

        val clsPartMats = if (doCls && clsResults.isNotEmpty()) {
            Log.i(TAG, "---------- step: Rotate partImages ----------")
            partMats.mapIndexed { index, mat ->
                if (index < clsResults.size && clsResults[index].index == 1) {
                    matRotateClockWise180(mat)
                } else mat
            }
        } else partMats

        val recTickMeter = TickMeter().apply { start() }
        Log.i(TAG, "---------- step: Get RecResults ----------")
        val recResults = rec.getRecResults(clsPartMats)
        recTickMeter.stop()

        Log.i(TAG, "Recognized ${recResults.size} text results")

        // 修复：确保detResults和recResults对应关系正确
        val validDetResults = mutableListOf<DetResult>()
        val validRecResults = mutableListOf<RecResult>()
        
        // 只处理成功裁剪和识别的部分
        for (i in partMats.indices) {
            if (i < recResults.size && i < filteredDetResults.size && !partMats[i].empty()) {
                validDetResults.add(filteredDetResults[i])
                validRecResults.add(recResults[i])
            }
        }

        Log.i(TAG, "Valid results: ${validRecResults.size}")

        // 创建包含坐标信息的文本结果
        val textWithCoordinates = StringBuilder()
        val textBlocksJsonArray = JSONArray()
        
        for (i in validRecResults.indices) {
            val recResult = validRecResults[i]
            val detResult = validDetResults[i]
            
            // 过滤空文本和低置信度结果
            if (recResult.text.isNotBlank() && recResult.charScores.isNotEmpty() && 
                recResult.charScores.average() > 0.5f) {
                
                // 构建JSON对象
                val textBlockJson = JSONObject().apply {
                    put("text", recResult.text)
                    put("score", recResult.charScores.average().toFloat())
                    put("det_score", detResult.score)
                    
                    val coordinatesArray = JSONArray()
                    detResult.points.forEach { point ->
                        // 调整坐标，去除padding
                        val adjustedX = (point.x - paddingRect.x).coerceAtLeast(0)
                        val adjustedY = (point.y - paddingRect.y).coerceAtLeast(0)
                        coordinatesArray.put(JSONObject().apply {
                            put("x", adjustedX)
                            put("y", adjustedY)
                        })
                    }
                    put("coordinates", coordinatesArray)
                }
                textBlocksJsonArray.put(textBlockJson)
                
                textWithCoordinates.append("${recResult.text} [")
                textWithCoordinates.append(detResult.points.joinToString(";") { 
                    "(${it.x - paddingRect.x},${it.y - paddingRect.y})" 
                })
                textWithCoordinates.append("]\n")
            }
        }

        Log.i(TAG, "---------- step: output box Mat(BGR) -> Mat(RGBA) -> Bitmap ----------")
        val outRGBA = Mat()
        cvtColor(textBoxPaddingImg.submat(paddingRect), outRGBA, COLOR_BGR2RGBA)
        val boxImage = Bitmap.createBitmap(
            outRGBA.cols(), outRGBA.rows(), Bitmap.Config.ARGB_8888
        )
        matToBitmap(outRGBA, boxImage)

        // 修复：使用有效的识别结果构建文本
        val text = validRecResults
            .filter { it.text.isNotBlank() && it.charScores.isNotEmpty() && it.charScores.average() > 0.5f }
            .joinToString(separator = "\n") { it.text }
        fullTickMeter.stop()
        
        Log.i(TAG, "Final text: $text")
        Log.i(TAG, "Text with coordinates length: ${textWithCoordinates.length}")
        
        return OcrResult(
            detResults = validDetResults,
            detTime = detTickMeter.timeMilli,
            clsResults = clsResults,
            clsTime = clsTickMeter.timeMilli,
            recResults = validRecResults,
            recTime = recTickMeter.timeMilli,
            boxImage = boxImage,
            fullTime = fullTickMeter.timeMilli,
            text = text,
            textWithCoordinates = textWithCoordinates.toString(),
            textBlocksJson = textBlocksJsonArray.toString()
        )
    }

    companion object {
        // 添加模型目录常量
        private const val MODELS_DIR = "models/"
        private const val DET_NAME = "ch_PP-OCRv5_mobile_det.onnx"
        private const val CLS_NAME = "ch_ppocr_mobile_v2.0_cls_infer.onnx"
        private const val REC_NAME = "ch_PP-OCRv5_rec_mobile_infer.onnx"
        private const val KEYS_NAME = "ppocrv5_dict.txt"
    }
}
