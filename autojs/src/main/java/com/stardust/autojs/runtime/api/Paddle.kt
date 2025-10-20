package com.stardust.autojs.runtime.api

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.util.Log
import com.baidu.paddle.lite.ocr.OcrResult
import com.baidu.paddle.lite.ocr.Predictor
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.core.image.ImageWrapper
import com.stardust.concurrent.VolatileDispose
import java.util.Collections

class Paddle {

    private val predictor = Predictor()
    private val availableProcessors = Runtime.getRuntime().availableProcessors()

    private var useCustomModel = false
    private var useSpecificModels = false
    private var customModelPath: String? = null
    private var customLabelPath: String? = null
    private var clsFileName: String? = null
    private var detFileName: String? = null
    private var recFileName: String? = null
    private var scoreThreshold = 0.1f

    @Synchronized
    private fun init(useSlim: Boolean): Boolean {
        if (!predictor.isLoaded || (!useCustomModel && useSlim != predictor.isUseSlim)) {
            if (useSpecificModels) {
                clsFileName?.let { predictor.clsModelFilename = it }
                recFileName?.let { predictor.recModelFilename = it }
                detFileName?.let { predictor.detModelFilename = it }
            }
            predictor.scoreThreshold = scoreThreshold
            
            return if (Looper.getMainLooper() == Looper.myLooper()) {
                val result = VolatileDispose<Boolean>()
                Thread {
                    val initResult = if (useCustomModel) {
                        customModelPath?.let { modelPath ->
                            customLabelPath?.let { labelPath ->
                                predictor.init(GlobalAppContext.get(), modelPath, labelPath)
                            }
                        } ?: false
                    } else {
                        predictor.init(GlobalAppContext.get(), useSlim)
                    }
                    result.setAndNotify(initResult)
                }.start()
                result.blockedGet()
            } else {
                if (useCustomModel) {
                    customModelPath?.let { modelPath ->
                        customLabelPath?.let { labelPath ->
                            predictor.init(GlobalAppContext.get(), modelPath, labelPath)
                        }
                    } ?: false
                } else {
                    predictor.init(GlobalAppContext.get(), useSlim)
                }
            }
        }
        return predictor.isLoaded
    }

    fun initWithCustomModel(modelPath: String, labelPath: String): Boolean {
        predictor.releaseModel()
        useCustomModel = true
        customModelPath = modelPath
        customLabelPath = labelPath
        predictor.checkModelLoaded = false
        return init(false)
    }

    fun initWithSpecificModels(
        modelPath: String, 
        labelPath: String, 
        detFileName: String, 
        recFileName: String, 
        clsFileName: String
    ): Boolean {
        useSpecificModels = true
        predictor.releaseModel()
        useCustomModel = true
        customModelPath = modelPath
        customLabelPath = labelPath
        this.detFileName = detFileName
        this.recFileName = recFileName
        this.clsFileName = clsFileName
        return init(false)
    }

    fun resetDefaultModel() {
        useCustomModel = false
        useSpecificModels = false
        predictor.releaseModel()
    }

    fun release() {
        predictor.releaseModel()
    }

    @JvmOverloads
    fun ocr(
        image: ImageWrapper,
        cpuThreadNum: Int = availableProcessors,
        useSlim: Boolean = true
    ): List<OcrResult> {
        val bitmap = image.bitmap
        if (bitmap == null || bitmap.isRecycled) {
            return emptyList()
        }
        
        if (predictor.cpuThreadNum != cpuThreadNum && !useCustomModel) {
            predictor.releaseModel()
            predictor.cpuThreadNum = cpuThreadNum
        }
        
        init(useSlim)
        return predictor.runOcr(bitmap)
    }

    fun ocr(
        image: ImageWrapper,
        cpuThreadNum: Int,
        myModelPath: String
    ): List<OcrResult> {
        val bitmap = image.bitmap
        if (bitmap == null || bitmap.isRecycled) {
            return emptyList()
        }
        
        if (!predictor.isLoaded) {
            initWithCustomModel(myModelPath, "")
        }
        
        return predictor.runOcr(bitmap)
    }

    fun ocr(image: ImageWrapper, useSlim: Boolean): List<OcrResult> {
        return ocr(image, availableProcessors, useSlim)
    }

    fun ocr(image: ImageWrapper, myModelPath: String): List<OcrResult> {
        return ocr(image, availableProcessors, myModelPath)
    }

    @JvmOverloads
    fun recognizeText(
        image: ImageWrapper,
        cpuThreadNum: Int = availableProcessors,
        useSlim: Boolean = true
    ): Array<String> {
        val wordsResult = ocr(image, cpuThreadNum, useSlim)
        Collections.sort(wordsResult)
        val outputResult = Array(wordsResult.size) { "" }
        for (i in wordsResult.indices) {
            outputResult[i] = wordsResult[i].label
            Log.i("outputResult", outputResult[i]) // show LOG in Logcat panel
        }
        return outputResult
    }

    fun recognizeText(image: ImageWrapper, cpuThreadNum: Int): Array<String> {
        return recognizeText(image, cpuThreadNum, true)
    }

    fun recognizeText(image: ImageWrapper): Array<String> {
        return recognizeText(image, availableProcessors, true)
    }

    fun getScoreThreshold(): Float {
        return scoreThreshold
    }

    fun setScoreThreshold(scoreThreshold: Float) {
        this.scoreThreshold = scoreThreshold
    }
}
