package com.equationl.paddleocr4android.Util.paddle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.core.graphics.toColorInt
import com.equationl.paddleocr4android.OcrResult
import com.equationl.paddleocr4android.CpuPowerMode
import com.equationl.paddleocr4android.OcrConfig
import com.equationl.paddleocr4android.exception.InitModelException
import com.equationl.paddleocr4android.exception.RunModelException
import java.io.File
import java.util.Date
import java.util.Vector
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

open class Predictor {
    var initSuccess = false
    var modelLoaded: Boolean = false
    var warmupIterNum: Int = 1
    var inferIterNum: Int = 1
    var ocrConfig = OcrConfig()
    protected var paddlePredictor: OCRPredictorNative? = null
    protected var inferenceTime: Float = 0f
    private var pendingReleaseRunnable: Runnable? = null

    // Only for object detection
    var wordLabels: Vector<String> = Vector<String>()
        protected set

    protected var scoreThreshold: Float = 0.1f
    var inputImage: Bitmap? = null
        set(value) {
            if (field !== value) {
                field?.recycle()
            }
            field = value?.copy(Bitmap.Config.ARGB_8888, true)
        }
    protected var outputImage: Bitmap? = null
    @Volatile
    protected var outputResult: String = ""
    protected var rawResultArray: ArrayList<OcrResultModel>? = null
    var isDrwwTextPositionBox: Boolean = false

    @Throws(InitModelException::class)
    fun init(
        appCtx: Context
    ): Boolean {
        modelLoaded = loadModel(appCtx, ocrConfig.modelPath, if (ocrConfig.isUseOpencl) 1 else 0, ocrConfig.cpuThreadNum, ocrConfig.cpuPowerMode)
        if (!modelLoaded) {
            return false
        }
        modelLoaded = loadLabel(appCtx, ocrConfig.labelPath)
        Log.d(TAG, "Predictor init: $modelLoaded")
        return modelLoaded
    }

    @Throws(InitModelException::class)
    protected fun loadModel(
        appCtx: Context,
        modelPath: String,
        useOpencl: Int,
        cpuThreadNum: Int,
        cpuPowerMode: CpuPowerMode
    ): Boolean {
        if (modelPath == ocrConfig.modelPath && modelLoaded) return true
        // Release model if exists
        releaseModel()

        // Load model
        if (modelPath.isEmpty()) {
            throw InitModelException("modelPath is Empty!")
        }

        var realPath = modelPath
        if (modelPath[0] != '/') {
            // Read model files from custom path if the first character of mode path is '/'
            // otherwise copy model to cache from assets
            realPath = appCtx.cacheDir.toString() + "/" + modelPath
            Utils.copyDirectoryFromAssets(appCtx, modelPath, realPath)
        }
        if (realPath.isEmpty()) {
            throw InitModelException("Get Model Real Path Fail")
        }

        val config = OCRPredictorNative.Config()
        config.useOpencl = useOpencl
        config.cpuThreadNum = cpuThreadNum
        config.cpuPower = cpuPowerMode.toString()
        config.detModelFilename = realPath + File.separator + ocrConfig.detModelFilename
        config.recModelFilename = realPath + File.separator + ocrConfig.recModelFilename
        config.clsModelFilename = realPath + File.separator + ocrConfig.clsModelFilename
        Log.i(
            "Predictor",
            "model path" + config.detModelFilename + " ; " + config.recModelFilename + ";" + config.clsModelFilename
        )
        if (!File(config.detModelFilename).canRead() || !File(config.detModelFilename).canRead() || !File(
                config.detModelFilename
            ).canRead()
        ) {
            throw InitModelException("无法读取模型，请检查模型路径是否正确且是否有权限读取！")
        }
        paddlePredictor = OCRPredictorNative(config)

        ocrConfig.cpuThreadNum = cpuThreadNum
        ocrConfig.cpuPowerMode = cpuPowerMode
        ocrConfig.modelPath = realPath
        modelLoaded = true
        return true
    }

    fun releaseModel() {
        if (paddlePredictor != null) {
            paddlePredictor!!.destory()
            paddlePredictor = null
        }
        modelLoaded = false
        ocrConfig = OcrConfig()
    }

    @Throws(InitModelException::class)
    protected fun loadLabel(appCtx: Context, labelPath: String?): Boolean {
        wordLabels.clear()
        if (labelPath == null) return true //ocr will return index, not text

        wordLabels.add("black")
        // Load word labels from file
        try {
            val assetsInputStream = appCtx.assets.open(labelPath)
            val available = assetsInputStream.available()
            val lines = ByteArray(available)
            assetsInputStream.read(lines)
            assetsInputStream.close()
            val words = String(lines)
            val contents: Array<String?> =
                words.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (content in contents) {
                wordLabels.add(content)
            }
            wordLabels.add(" ")
            Log.i(TAG, "Word label size: " + wordLabels.size)
        } catch (e: Exception) {
            e.printStackTrace()
            throw InitModelException("Load label Fail: " + e.message)
        }
        ocrConfig.labelPath = labelPath
        return true
    }


    @Throws(RunModelException::class)
    fun runModel(): Boolean {  // 移除参数，使用配置
        if (inputImage == null || !isLoaded()) {
            throw RunModelException("输入图片为空或模型未加载")
        }

        // 清理上一次的结果
        rawResultArray = null
        outputImage?.recycle()
        outputImage = null

        // Warm up
        repeat(warmupIterNum) {
            paddlePredictor!!.runImage(
                inputImage,
                ocrConfig.detLongSize,
                if (ocrConfig.isRunDet) 1 else 0,
                if (ocrConfig.isRunCls) 1 else 0,
                if (ocrConfig.isRunRec) 1 else 0
            )
        }

        warmupIterNum = 0
        val start = Date()
        var results = paddlePredictor!!.runImage(
            inputImage,
            ocrConfig.detLongSize,
            if (ocrConfig.isRunDet) 1 else 0,
            if (ocrConfig.isRunCls) 1 else 0,
            if (ocrConfig.isRunRec) 1 else 0
        )
        val end = Date()
        inferenceTime = (end.time - start.time) / inferIterNum.toFloat()

        results = postprocess(results)

        // 使用 scoreThreshold 过滤结果
        rawResultArray = ArrayList(results.filter { it.confidence >= scoreThreshold })
        Log.i(TAG, "[stat] Inference Time: $inferenceTime ; Box Size ${rawResultArray?.size}")
        drawResults(rawResultArray!!)

        return true
    }
    fun isLoaded(): Boolean {
        return paddlePredictor != null && modelLoaded
    }
    private fun postprocess(results: ArrayList<OcrResultModel>): ArrayList<OcrResultModel> {
        for (r in results) {
            val word = StringBuilder()
            for (index in r.wordIndex) {
                if (index >= 0 && index < wordLabels.size) {
                    word.append(wordLabels[index])
                } else {
                    Log.e(TAG, "Word index is not in label list:$index")
                    word.append("×")
                }
            }
            r.label = word.toString()
            r.clsLabel = if (r.clsIdx == 1f) "180" else "0" // 假设有这些属性
        }
        return results
    }

    private fun drawResults(results: ArrayList<OcrResultModel>) {
        val outputResultSb = StringBuilder()
        for (i in results.indices.reversed()) {
            val result = results[i]
            val sb = StringBuilder("")

            if (result.label?.isNotEmpty() == true) {
                sb.append(result.label)
            }
            //Log.i(TAG, sb.toString()) // show LOG in Logcat panel
            outputResultSb.append(sb).append("\n")
        }
        outputResult = outputResultSb.toString()

        if (isDrwwTextPositionBox) {
            outputImage = inputImage?.copy(Bitmap.Config.ARGB_8888, false)
            val canvas = Canvas(outputImage!!)
            val paintFillAlpha = Paint()
            paintFillAlpha.style = Paint.Style.FILL
            paintFillAlpha.color = "#3B85F5".toColorInt()
            paintFillAlpha.alpha = 50

            val paint = Paint()
            paint.color = "#3B85F5".toColorInt()
            paint.strokeWidth = 5f
            paint.style = Paint.Style.STROKE

            for (result in results) {
                val path = Path()
                val points = result.points
                if (points.isEmpty()) {
                    continue
                }
                path.moveTo(points[0].x.toFloat(), points[0].y.toFloat())
                for (i in points.indices.reversed()) {
                    val p = points[i]
                    path.lineTo(p.x.toFloat(), p.y.toFloat())
                }
                canvas.drawPath(path, paint)
                canvas.drawPath(path, paintFillAlpha)
            }
        }
    }

    // ============ AutoX脚本接口 ============


    /**
     * 初始化OCR（v2兼容的另一个版本）
     */
    @JavascriptInterface
    fun initOcr(
        appCtx: Context,
        cpuThreadNum: Int,
        useSlim: Boolean
    ): Boolean {
        ocrConfig.cpuThreadNum = cpuThreadNum

        return try {
            init(appCtx)
        } catch (e: Exception) {
            Log.e(TAG, "initOcr failed", e)
            false
        }
    }

    /**
     * 初始化OCR（v2兼容接口）
     */
    @JavascriptInterface
    fun initOcr(
        appCtx: Context,
        modelPath: String
    ): Boolean {
        ocrConfig.modelPath = modelPath
        return try {
            init(appCtx)
        } catch (e: Exception) {
            Log.e(TAG, "init failed", e)
            false
        }
    }

    /**
     * 初始化OCR（带更多配置，v2兼容）
     */
    @JavascriptInterface
    fun initOcr(
        appCtx: Context,
        modelPath: String,
        labelPath: String?,
        cpuThreadNum: Int,
        cpuPowerMode: CpuPowerMode
    ): Boolean {
        ocrConfig.modelPath = modelPath
        ocrConfig.labelPath = labelPath
        ocrConfig.cpuThreadNum = cpuThreadNum
        ocrConfig.cpuPowerMode = cpuPowerMode
        return init(appCtx)
    }

    @JavascriptInterface
    fun initOcrWithConfig(appCtx: Context, config: OcrConfig): Boolean {
        ocrConfig = config
        Log.d(TAG, "initOcrWithConfig: $config")

        return init(appCtx)
    }

    /**
     * 检查初始化是否成功（v2兼容）
     */
    @JavascriptInterface
    fun checkInitSuccess(): Boolean {
        if (initSuccess) return true
        val checkImgBase64 =
            "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"
        val checkingBitmap = BitmapFactory.decodeByteArray(
            Base64.decode(
                checkImgBase64,
                Base64.DEFAULT
            ), 0, Base64.decode(checkImgBase64, Base64.DEFAULT).size
        )

        val checkingResults = runOcr(checkingBitmap, 4)
        val sb = StringBuilder()
        for (result in checkingResults) {
            sb.append(result.text)
        }
        initSuccess = sb.toString().contains("测") || sb.toString().contains("试")
        Log.i(TAG, "checkInitSuccess结果: $sb\t是否成功: $initSuccess")
        return initSuccess
    }

    /**
     * 运行OCR并返回v2格式结果（核心接口）
     */
    @JavascriptInterface
    fun runOcr(inputImage: Bitmap?, cpuThreadNum: Int): List<OcrResult> {
        cancelPendingRelease()
        if (inputImage == null) return emptyList()

        ocrConfig.cpuThreadNum = cpuThreadNum
        this.inputImage = inputImage //.copy(Bitmap.Config.ARGB_8888, true)

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

    /**
     * 将v4结果转换为v2格式
     */
    private fun transformData(ocrResultModelList: List<OcrResultModel>?): List<OcrResult> {
        if (ocrResultModelList.isNullOrEmpty()) {
            return emptyList()
        }

        val wordsResult = mutableListOf<OcrResult>()
        for (model in ocrResultModelList) {
            val pointList = model.points
            if (pointList.isEmpty()) continue

            // 计算边界框
            var left = pointList[0].x
            var top = pointList[0].y
            var right = pointList[0].x
            var bottom = pointList[0].y

            for (p in pointList) {
                left = min(left, p.x)
                top = min(top, p.y)
                right = max(right, p.x)
                bottom = max(bottom, p.y)
            }

            val ocrResult = OcrResult(
                confidence = model.confidence,
                preprocessTime = 0f, // v4没有预处理时间记录
                inferenceTime = inferenceTime,
                text = model.label?.trim()?.replace("\r", "") ?: "",
                bounds = Rect(left, top, right, bottom)
            )
            wordsResult.add(ocrResult)
        }

        // 按位置排序（v2的排序逻辑）
        wordsResult.sort()
        return wordsResult
    }

    /**
     * OCR识别文本（返回字符串数组）
     */
    @JavascriptInterface
    fun ocrText(
        appCtx: Context,
        bitmap: Bitmap?,
        cpuThreadNum: Int,
        useSlim: Boolean = false // v4忽略此参数，保持兼容
    ): Array<String?> {
        cancelPendingRelease()
        val wordsResult = ocr(appCtx, bitmap, cpuThreadNum, useSlim)
        val outputResult = arrayOfNulls<String>(wordsResult.size)
        for (i in wordsResult.indices) {
            outputResult[i] = wordsResult[i].text
            Log.i(TAG, outputResult[i] ?: "")
        }
        return outputResult
    }

    /**
     * 完整的OCR识别（v2兼容接口）
     */
    @JavascriptInterface
    fun ocr(
        appCtx: Context,
        inputImage: Bitmap?,
        cpuThreadNum: Int,
        useSlim: Boolean = false
    ): List<OcrResult> {
        cancelPendingRelease()
        var retry = 3
        ocrConfig.cpuThreadNum = cpuThreadNum
        while (paddlePredictor == null || !checkInitSuccess()) {
            if (retry-- < 0) break
            init(appCtx)
        }

        return runOcr(inputImage, cpuThreadNum)
    }

    @JavascriptInterface
    fun release(): Boolean {
        return try {
            releaseModel()
            initSuccess = false
            true
        } catch (e: Exception) {
            Log.e(TAG, "releaseModelImmediately failed", e)
            false
        }
    }

    @JavascriptInterface
    fun releaseDelayed(): Boolean {
        return releaseDelayed(3 * 60 * 1000L)  // 默认3分钟
    }

    @JavascriptInterface
    fun releaseDelayed(delayMillis: Long): Boolean {
        return try {
            cancelPendingRelease()
            Log.d("Predictor", "Wait $delayMillis ms to release model")
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            pendingReleaseRunnable = Runnable {
                releaseModel()
                pendingReleaseRunnable = null
            }
            handler.postDelayed(pendingReleaseRunnable!!, delayMillis)
            true
        } catch (e: Exception) {
            Log.e(TAG, "releaseModelDelayed failed", e)
            false
        }
    }

    @JavascriptInterface
    fun cancelPendingRelease(): Boolean {
        return try {
            if (pendingReleaseRunnable != null) {
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                handler.removeCallbacks(pendingReleaseRunnable!!)
                pendingReleaseRunnable = null
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "cancelPendingRelease failed", e)
            false
        }
    }

    /**
     * 检查是否已加载
     */
    @JavascriptInterface
    fun isModelLoaded(): Boolean {
        return isLoaded()
    }

    companion object {
        private val TAG: String = Predictor::class.java.simpleName
    }
}
