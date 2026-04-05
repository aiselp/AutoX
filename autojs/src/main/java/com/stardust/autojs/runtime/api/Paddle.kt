package com.stardust.autojs.runtime.api

import android.content.Context
import com.equationl.paddleocr4android.CpuPowerMode
import com.equationl.paddleocr4android.OcrConfig
import com.equationl.paddleocr4android.OcrResult
import com.equationl.paddleocr4android.Util.paddle.Predictor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stardust.app.GlobalAppContext.get
import com.stardust.autojs.core.image.ImageWrapper

class Paddle {

    private val predictor = Predictor()
    private val availableProcessors = Runtime.getRuntime().availableProcessors()

    private fun initOcr(context: Context, cpuThreadNum: Int, useSlim: Boolean) {
        predictor.initOcr(context, cpuThreadNum, useSlim)
    }

    private fun initOcr(context: Context, myModelPath: String): Boolean {
        return predictor.initOcr(context, myModelPath)
    }

    fun getOcrConfig(): Map<String, Any> {
        val gson = Gson()
        val json = gson.toJson(predictor.ocrConfig)
        val type = object : TypeToken<Map<String, Any>>() {}.type
        return gson.fromJson(json, type)
    }

    fun initOcrWithConfig(config: MutableMap<String, Any>) : Boolean {
        return predictor.initOcrWithConfig(get(), mapToOcrConfig(config))
    }

    @JvmOverloads
    fun initOcr(
        modelPath: String? = null,
        labelPath: String? = null,
        cpuThreadNum: Int? = null,
        cpuPowerMode: String? = null
    ): Boolean {
        val defaultOcrConfig = OcrConfig()
        val finalModelPath = modelPath ?: defaultOcrConfig.modelPath
        val finalLabelPath = labelPath ?:  defaultOcrConfig.labelPath
        val finalCpuThreadNum = cpuThreadNum ?: availableProcessors
        val finalCpuPowerMode = parseCpuPowerMode(cpuPowerMode) ?: defaultOcrConfig.cpuPowerMode
        return predictor.initOcr(get(), finalModelPath, finalLabelPath, finalCpuThreadNum, finalCpuPowerMode)
    }
    fun mapToOcrConfig(map: MutableMap<String, Any>): OcrConfig {
        return OcrConfig().apply {
            (map["modelPath"] as? String)?.let { modelPath = it }
            (map["labelPath"] as? String)?.let { labelPath = it }
            (map["cpuThreadNum"] as? Number)?.toInt()?.let { cpuThreadNum = it }
            (map["cpuPowerMode"] as? String)?.let { parseCpuPowerMode(it)?.let { mode -> cpuPowerMode = mode } }
            (map["scoreThreshold"] as? Number)?.toFloat()?.let { scoreThreshold = it }
            (map["detLongSize"] as? Number)?.toInt()?.let { detLongSize = it }
            (map["detModelFilename"] as? String)?.let { detModelFilename = it }
            (map["recModelFilename"] as? String)?.let { recModelFilename = it }
            (map["clsModelFilename"] as? String)?.let { clsModelFilename = it }
            (map["isRunDet"] as? Boolean)?.let { isRunDet = it }
            (map["isRunCls"] as? Boolean)?.let { isRunCls = it }
            (map["isRunRec"] as? Boolean)?.let { isRunRec = it }
            (map["isUseOpencl"] as? Boolean)?.let { isUseOpencl = it }
            (map["isDrwwTextPositionBox"] as? Boolean)?.let { isDrwwTextPositionBox = it }
        }
    }
    private fun parseCpuPowerMode(mode: String?): CpuPowerMode? {
        return when (mode?.uppercase()) {
            "LITE_POWER_HIGH" -> CpuPowerMode.LITE_POWER_HIGH
            "LITE_POWER_LOW" -> CpuPowerMode.LITE_POWER_LOW
            "LITE_POWER_FULL" -> CpuPowerMode.LITE_POWER_FULL
            "LITE_POWER_NO_BIND" -> CpuPowerMode.LITE_POWER_NO_BIND
            "LITE_POWER_RAND_HIGH" -> CpuPowerMode.LITE_POWER_RAND_HIGH
            "LITE_POWER_RAND_LOW" -> CpuPowerMode.LITE_POWER_RAND_LOW
            else -> null
        }
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
        if (!predictor.isLoaded()) {
            initOcr(get(), cpuThreadNum, useSlim)
        }
        return predictor.runOcr(bitmap, cpuThreadNum)
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
        if (!predictor.isLoaded()) {
            initOcr(get(), myModelPath)
        }
        return predictor.runOcr(bitmap, cpuThreadNum)
    }

    fun ocr(image: ImageWrapper, useSlim: Boolean): List<OcrResult> {
        return ocr(image, availableProcessors, useSlim)
    }

    fun ocr(image: ImageWrapper, myModelPath: String): List<OcrResult> {
        return ocr(image, availableProcessors, myModelPath)
    }

    fun release(): Boolean {
        return predictor.release()
    }

    fun releaseDelayed(): Boolean {
        return predictor.releaseDelayed()
    }

    fun releaseDelayed(delayMillis: Long): Boolean {
        return predictor.releaseDelayed(delayMillis)
    }
}