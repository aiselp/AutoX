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
        return detect(imageWrapper, true, 1920, 50, 0.3f, 0.6f, 1.5f, true, true)
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

    // 原有的 Bitmap 方法
    @android.webkit.JavascriptInterface
    fun detect(
        bmp: Bitmap,
        scaleUp: Boolean = true,
        maxSideLen: Int = 1920,
        padding: Int = 50,
        boxScoreThresh: Float = 0.3f,
        boxThresh: Float = 0.6f,
        unClipRatio: Float = 1.5f,
        doCls: Boolean = true,
        mostCls: Boolean = true
    ): String {
        Log.i(TAG, "=====Prepare from Bitmap=====")
        
        // 检查初始化状态
        if (!isInitialized) {
            return """{"error": "OCR Engine not initialized: $initError", "success": false}"""
        }
        
        Log.i(TAG, "Parameter: scaleUp($scaleUp), maxSideLen($maxSideLen), padding($padding),boxScoreThresh($boxScoreThresh),boxThresh($boxThresh),unClipRatio($unClipRatio),doCls($doCls),mostCls($mostCls)")

        try {
            Log.i(TAG, "---------- step: input Bitmap -> Mat(RGBA) -> Mat(BGR) ----------")
            val inputRGBA = Mat(bmp.width, bmp.height, CvType.CV_8UC4)
            Utils.bitmapToMat(bmp, inputRGBA)
            val inputBGR = Mat()
            cvtColor(inputRGBA, inputBGR, COLOR_RGBA2BGR)

            Log.i(TAG, "---------- step: Resize ----------")
            val originMaxSide = max(inputBGR.cols(), inputBGR.rows())
            var resize = if (scaleUp) {
                //支持放大和缩小
                if (maxSideLen <= 0) originMaxSide else maxSideLen
            } else {
                //仅支持缩小
                if (maxSideLen <= 0 || originMaxSide < maxSideLen) originMaxSide else maxSideLen
            }
            resize += 2 * padding
            Log.i(TAG, "resize=$resize")
            val paddingRect = Rect(padding, padding, inputBGR.cols(), inputBGR.rows())
            val paddingSrc = makePadding(inputBGR, padding)
            val s = getScaleParam(paddingSrc, resize)
            Log.i(TAG, "$s")

            val ocrResult = fullDetect(paddingSrc, paddingRect, s, boxScoreThresh, boxThresh, unClipRatio, doCls, mostCls)
            Log.i(TAG, ocrResult.toString())

            // 返回 JSON 字符串
            return """{
                "text": "${ocrResult.text.replace("\"", "\\\"")}",
                "fullTime": ${ocrResult.fullTime},
                "detTime": ${ocrResult.detTime},
                "recTime": ${ocrResult.recTime},
                "clsTime": ${ocrResult.clsTime},
                "success": true
            }"""
        } catch (e: Exception) {
            Log.e(TAG, "OCR detection error: ${e.message}", e)
            return """{"error": "OCR processing failed: ${e.message}", "success": false}"""
        }
    }

    // 添加其他便捷方法
    @android.webkit.JavascriptInterface
    fun detectFromBase64(base64Image: String): String {
        return detectFromBase64(base64Image, true, 1920, 50, 0.3f, 0.6f, 1.5f, true, true)
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
        // 简化的调用，使用默认参数
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

        val detTickMeter = TickMeter().apply { start() }
        Log.i(TAG, "---------- step: Get DetResults ----------")
        val detResults = det.getDetResults(src, s, boxScoreThresh, boxThresh, unClipRatio)
        detTickMeter.stop()

        Log.i(TAG, "---------- step: Draw TextBoxes ----------")
        drawTextBoxes(textBoxPaddingImg, detResults, thickness)

        Log.i(TAG, "---------- step: Get PartMats ----------")
        val partMats = getPartMats(src, detResults)

        val clsTickMeter = TickMeter().apply { start() }
        val clsResults = if (doCls) {
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

        val clsPartMats = if (doCls) {
            Log.i(TAG, "---------- step: Rotate partImages ----------")
            partMats.mapIndexed { index, mat ->
                if (clsResults[index].index == 1) {
                    matRotateClockWise180(mat)
                } else mat
            }
        } else partMats

        val recTickMeter = TickMeter().apply { start() }
        Log.i(TAG, "---------- step: Get RecResults ----------")
        val recResults = rec.getRecResults(clsPartMats)
        recTickMeter.stop()

        Log.i(TAG, "---------- step: output box Mat(BGR) -> Mat(RGBA) -> Bitmap ----------")
        val outRGBA = Mat()
        cvtColor(textBoxPaddingImg.submat(paddingRect), outRGBA, COLOR_BGR2RGBA)
        val boxImage = Bitmap.createBitmap(
            outRGBA.cols(), outRGBA.rows(), Bitmap.Config.ARGB_8888
        )
        matToBitmap(outRGBA, boxImage)

        val text = recResults.joinToString(separator = "\n") { it.text }
        fullTickMeter.stop()
        return OcrResult(
            detResults = detResults,
            detTime = detTickMeter.timeMilli,
            clsResults = clsResults,
            clsTime = clsTickMeter.timeMilli,
            recResults = recResults,
            recTime = recTickMeter.timeMilli,
            boxImage = boxImage,
            fullTime = fullTickMeter.timeMilli,
            text = text,
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
