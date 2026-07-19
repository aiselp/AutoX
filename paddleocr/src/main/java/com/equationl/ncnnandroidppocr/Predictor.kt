package com.equationl.ncnnandroidppocr

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import com.equationl.ncnnandroidppocr.bean.*
import com.equationl.ncnnandroidppocr.cpp.OCRNative
import java.io.File
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.toColorInt
import androidx.core.graphics.createBitmap
import com.equationl.ncnnandroidppocr.OcrConfig.Companion.IMAGE_SIZE_FULL
import com.equationl.ncnnandroidppocr.OcrConfig.Companion.IMAGE_SIZE_SLIM
import java.lang.ref.WeakReference

class Predictor private constructor() {

    var ocrConfig = OcrConfig()
        private set
    var initSuccess = false
    var modelLoaded: Boolean = false
    private var lastConfigHash: Int = 0
    var warmupIterNum: Int = 1

    private val defaultModelPath = ocrConfig.modelPath

    private var ocrNative: OCRNative? = null
    private var inferenceTime: Float = 0f
    private var inputImage: Bitmap? = null
    private var outputImage: Bitmap? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingReleaseRunnable: Runnable? = null

    private class ReleaseRunnable(private val predictorRef: WeakReference<Predictor>) : Runnable {
        override fun run() {
            predictorRef.get()?.let {
                it.releaseModel()
                it.pendingReleaseRunnable = null
            }
        }
    }

    val maxCores = Runtime.getRuntime().availableProcessors()

    var isDrawTextBox: Boolean = false

    companion object {
        @Volatile
        private var instance: Predictor? = null

        fun getInstance(): Predictor {
            return instance ?: synchronized(this) {
                instance ?: Predictor().also { instance = it }
            }
        }

        fun releaseInstance() {
            synchronized(this) {
                instance?.release()
                instance = null
            }
        }

        private const val TAG = "Predictor"
    }

    // ============ 初始化方法 ============

    @JavascriptInterface
    fun initOcr(appCtx: Context, cpuThreadNum: Int, useSlim: Boolean): Boolean {
        ocrConfig.cpuThreadNum = cpuThreadNum
        //ocrConfig.modelPath =  if (useSlim) "models/ocr_v5_for_cpu(slim)" else "models/ocr_v5_for_cpu"
        ocrConfig.imageSize = if (useSlim) IMAGE_SIZE_SLIM else IMAGE_SIZE_FULL

        return init(appCtx)
    }

    @JavascriptInterface
    fun initOcr(appCtx: Context, modelPath: String): Boolean {
        ocrConfig.modelPath = modelPath
        return try {
            init(appCtx)
        } catch (e: Exception) {
            Log.e(TAG, "initOcr failed", e)
            false
        }
    }

    @JavascriptInterface
    fun initOcrWithConfig(appCtx: Context, config: OcrConfig): Boolean {
        ocrConfig = config
        Log.d(TAG, "initOcrWithConfig: $config")
        return try {
            init(appCtx)
        } catch (e: Exception) {
            Log.e(TAG, "initOcrWithConfig failed", e)
            false
        }
    }

    @Throws(Exception::class)
    private fun init(appCtx: Context): Boolean {
        ocrConfig.cpuThreadNum = when {
            ocrConfig.cpuThreadNum in 0..maxCores -> ocrConfig.cpuThreadNum
            else -> 0
        }
        var retry = 3
        while (retry-- > 0) {
            try {
                loadModel(appCtx, ocrConfig)
                if (modelLoaded && checkInitSuccess()) break
            } catch (e: Exception) {
                Log.e(TAG, "init failed, retry left: $retry", e)
            }
            if (retry > 0) Thread.sleep(100)
        }

        Log.d(TAG, "Predictor init: $modelLoaded")
        return modelLoaded
    }

    private fun hashConfig(config: OcrConfig): Int {
        return config.modelPath.hashCode() +
                config.detParamFilename.hashCode() +
                config.detBinFilename.hashCode() +
                config.recParamFilename.hashCode() +
                config.recBinFilename.hashCode() +
                config.imageSize.hashCode() +
                config.device.hashCode() +
                config.useFp16.hashCode() +
                config.cpuThreadNum.hashCode()
    }

    private fun validateImageSize(size: Int): Int {
        if (ocrConfig.modelPath == defaultModelPath) {
            return when {
                size < 128 -> 128
                size > 512 -> 512
                else -> size
            }
        }
        return size
    }

    @Throws(Exception::class)
    private fun loadModel(appCtx: Context, config: OcrConfig): Boolean {
        try {
            Log.d(TAG, "modelPath: ${config.modelPath}, modelLoaded: $modelLoaded")

            val currentHash = hashConfig(config)

            if (modelLoaded && currentHash == lastConfigHash) {
                return true
            }

            releaseModel()
            modelLoaded = false

            if (config.modelPath.isEmpty()) {
                throw Exception("modelPath is Empty!")
            }

            var realPath = config.modelPath
            if (realPath[0] != '/') {
                realPath = appCtx.cacheDir.toString() + "/" + config.modelPath
                copyDirectoryFromAssets(appCtx, config.modelPath, realPath)
            }

            val native = OCRNative()

            val detParamPath = "$realPath/${config.detParamFilename}"
            val detModelPath = "$realPath/${config.detBinFilename}"
            val recParamPath = "$realPath/${config.recParamFilename}"
            val recModelPath = "$realPath/${config.recBinFilename}"
            val targetSize = validateImageSize(config.imageSize)
            Log.d(TAG, "detParamPath: $detParamPath, targetSize: $targetSize, imageSize: ${config.imageSize}")

            val success = native.loadModelByPath(
                detParamPath,
                detModelPath,
                recParamPath,
                recModelPath,
                targetSize,
                config.device.ordinal,
                config.useFp16,
                config.cpuThreadNum
            )

            if (!success) {
                Log.e(TAG, "loadModelByPath returned false")
                return false
            }

            ocrNative = native
            modelLoaded = true
            lastConfigHash = currentHash
            return true
        } catch (e: Exception) {
            Log.e(TAG, "loadModel exception", e)
            return false
        }
    }

    private fun copyDirectoryFromAssets(appCtx: Context, srcDir: String, dstDir: String) {
        try {
            val dstFile = File(dstDir)
            if (!dstFile.exists()) dstFile.mkdirs()

            for (fileName in appCtx.assets.list(srcDir) ?: return) {
                val srcSubPath = "$srcDir/$fileName"
                val dstSubPath = "$dstDir/$fileName"
                val subFiles = appCtx.assets.list(srcSubPath)
                if (!subFiles.isNullOrEmpty()) {
                    copyDirectoryFromAssets(appCtx, srcSubPath, dstSubPath)
                } else {
                    copyFileFromAssets(appCtx, srcSubPath, dstSubPath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "复制 assets 目录失败: $srcDir -> $dstDir", e)
        }
    }

    private fun copyFileFromAssets(appCtx: Context, srcPath: String, dstPath: String) {
        try {
            appCtx.assets.open(srcPath).use { input ->
                java.io.FileOutputStream(File(dstPath)).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "复制文件失败: $srcPath -> $dstPath", e)
        }
    }

    fun releaseModel() {
        ocrNative?.release()
        ocrNative = null
        modelLoaded = false
    }

    // ============ 识别方法 ============

    @JavascriptInterface
    fun runOcr(inputImage: Bitmap?): List<OcrResult> {
        cancelPendingRelease()
        if (inputImage == null) return emptyList()

        this.inputImage?.recycle()
        this.inputImage = inputImage.copy(Bitmap.Config.ARGB_8888, false)

        return try {
            val success = runModel()
            if (success) {
                transformData(rawResultArray)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "runOcr failed", e)
            emptyList()
        }
    }

    /*
    @JavascriptInterface
    fun ocrText(appCtx: Context, bitmap: Bitmap?, cpuThreadNum: Int, useSlim: Boolean = true): Array<String?> {
        val wordsResult = ocr(appCtx, bitmap, cpuThreadNum, useSlim)
        return wordsResult.map { it.text }.toTypedArray()
    }

    @JavascriptInterface
    fun ocr(appCtx: Context, inputImage: Bitmap?, cpuThreadNum: Int, useSlim: Boolean = true): List<AutoXResult> {
        if (!isLoaded() && !initOcr(appCtx, cpuThreadNum, useSlim)) {
            return emptyList()
        }
        return runOcr(inputImage)
    }
     */

    @Throws(Exception::class)
    fun runModel(): Boolean {
        val bitmap = inputImage
        if (bitmap == null || !isLoaded()) {
            throw Exception("输入图片为空或模型未加载")
        }

        Log.d(TAG, "runModel: bitmap config=${bitmap.config}, size=${bitmap.width}x${bitmap.height}")

        rawResultArray = null
        outputImage?.recycle()
        outputImage = null

        try {
            repeat(warmupIterNum) {
                ocrNative?.detectBitmap(bitmap)
            }
            warmupIterNum = 0

            val start = System.currentTimeMillis()
            val ncnnResult = ocrNative?.detectBitmap(bitmap)
            val end = System.currentTimeMillis()
            inferenceTime = (end - start).toFloat()

            if (ncnnResult == null) {
                throw Exception("OCR识别返回空")
            }

            rawResultArray = transformToAutoXResult(ncnnResult)
            Log.i(TAG, "Inference Time: $inferenceTime ms, Box Size: ${rawResultArray?.size}")

            if (isDrawTextBox) {
                drawResults(rawResultArray ?: emptyList())
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "runModel error", e)
            throw e
        }
    }

    private var rawResultArray: List<OcrResult>? = null

    private fun transformToAutoXResult(ncnnResult: NcnnOcrResult): List<OcrResult> {
        val results = mutableListOf<OcrResult>()

        for (textLine in ncnnResult.textLines) {
            if (textLine.confidence < ocrConfig.scoreThreshold) continue

            // 计算 bounds
            var left = Int.MAX_VALUE
            var top = Int.MAX_VALUE
            var right = Int.MIN_VALUE
            var bottom = Int.MIN_VALUE
            for (point in textLine.points) {
                left = min(left, point.x)
                top = min(top, point.y)
                right = max(right, point.x)
                bottom = max(bottom, point.y)
            }

            results.add(OcrResult(
                confidence = textLine.confidence,
                inferenceTime = ncnnResult.inferenceTime.toFloat(),
                text = textLine.text,
                bounds = Rect(left, top, right, bottom),
                drawBitmap = null
            ))
        }

        return results
    }

    private fun drawResults(results: List<OcrResult>) {
        if (!isDrawTextBox) return

        outputImage?.recycle()
        outputImage = inputImage?.copy(Bitmap.Config.ARGB_8888, false)

        val canvas = Canvas(outputImage!!)
        val paint = Paint().apply {
            color = "#3B85F5".toColorInt()
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
        val paintFill = Paint().apply {
            style = Paint.Style.FILL
            color = "#3B85F5".toColorInt()
            alpha = 50
        }

        for (result in results) {
            val bounds = result.bounds
            val path = Path()
            path.moveTo(bounds.left.toFloat(), bounds.top.toFloat())
            path.lineTo(bounds.right.toFloat(), bounds.top.toFloat())
            path.lineTo(bounds.right.toFloat(), bounds.bottom.toFloat())
            path.lineTo(bounds.left.toFloat(), bounds.bottom.toFloat())
            path.close()
            canvas.drawPath(path, paint)
            canvas.drawPath(path, paintFill)
        }
    }

    private fun transformData(results: List<OcrResult>?): List<OcrResult> {
        if (results.isNullOrEmpty()) return emptyList()

        val sorted = results.sorted()

        if (isDrawTextBox && outputImage != null) {
            return sorted.map { it.copy(drawBitmap = outputImage) }
        }
        return sorted
    }

    @JavascriptInterface
    fun checkInitSuccess(): Boolean {
        if (initSuccess) return true

        val testBitmap = createTestBitmap()
        val results = runOcr(testBitmap)
        testBitmap.recycle()

        val sb = StringBuilder()
        for (result in results) {
            sb.append(result.text)
        }
        initSuccess = sb.toString().contains("测") || sb.toString().contains("试")
        Log.i(TAG, "checkInitSuccess: $sb, success: $initSuccess")
        return initSuccess
    }

    private fun createTestBitmap(): Bitmap {
        val bitmap = createBitmap(100, 50)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 30f
        }
        canvas.drawColor(Color.WHITE)
        canvas.drawText("测试", 10f, 35f, paint)
        return bitmap
    }

    fun isLoaded(): Boolean = ocrNative != null && modelLoaded

    @JavascriptInterface
    fun release(): Boolean {
        return try {
            inputImage?.recycle()
            inputImage = null
            outputImage?.recycle()
            outputImage = null
            rawResultArray = null
            releaseModel()
            System.gc()
            initSuccess = false
            true
        } catch (e: Exception) {
            Log.e(TAG, "release failed", e)
            false
        }
    }

    @JavascriptInterface
    fun releaseDelayed(): Boolean = releaseDelayed(3 * 60 * 1000L)

    @JavascriptInterface
    fun releaseDelayed(delayMillis: Long): Boolean {
        return try {
            cancelPendingRelease()
            Log.d(TAG, "Wait $delayMillis ms to release model")
            pendingReleaseRunnable = ReleaseRunnable(WeakReference(this))
            mainHandler.postDelayed(pendingReleaseRunnable!!, delayMillis)
            true
        } catch (e: Exception) {
            Log.e(TAG, "releaseDelayed failed", e)
            false
        }
    }

    @JavascriptInterface
    fun cancelPendingRelease(): Boolean {
        return try {
            pendingReleaseRunnable?.let {
                mainHandler.removeCallbacks(it)
                pendingReleaseRunnable = null
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "cancelPendingRelease failed", e)
            false
        }
    }

    @JavascriptInterface
    fun isModelLoaded(): Boolean = isLoaded()
}