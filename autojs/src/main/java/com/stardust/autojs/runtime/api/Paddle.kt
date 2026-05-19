package com.stardust.autojs.runtime.api

import android.content.Context
import android.util.Log
import com.equationl.ncnnandroidppocr.OcrConfig
import com.equationl.ncnnandroidppocr.Predictor
import com.equationl.ncnnandroidppocr.bean.AutoXResult
//import com.equationl.ncnnandroidppocr.bean.ModelType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stardust.app.GlobalAppContext.get
import com.stardust.autojs.core.image.ImageWrapper
import com.equationl.ncnnandroidppocr.bean.Device

class Paddle {

    private val predictor: Predictor
        get() = Predictor.getInstance()
    //private val availableProcessors = Runtime.getRuntime().availableProcessors()

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
        cpuThreadNum: Int? = null,
    ): Boolean {
        val config = OcrConfig().apply {
            modelPath?.let { this.modelPath = it }
            cpuThreadNum?.let { this.cpuThreadNum = it }
        }
        return predictor.initOcrWithConfig(get(), config)
    }
    fun mapToOcrConfig(map: MutableMap<String, Any>): OcrConfig {
        return OcrConfig().apply {
            (map["useSlim"] as? Boolean)?.let {
                useSlim = it
                //modelPath = if (useSlim) "models/ocr_v5_for_cpu(slim)" else "models/ocr_v5_for_cpu"
                imageSize = if (useSlim) 256 else 512
            }
            (map["modelPath"] as? String)?.let {
                modelPath = it
                useSlim = false
            }
            (map["cpuThreadNum"] as? Number)?.toInt()?.let { cpuThreadNum = it }
            (map["scoreThreshold"] as? Number)?.toFloat()?.let { scoreThreshold = it }
            (map["device"] as? String)?.let {
                device = when (it.uppercase()) {
                    "GPU" -> Device.GPU
                    "Vulkan" -> Device.TurnipVulkan
                    else -> Device.CPU
                }
            }
            (map["imageSize"] as? Number)?.toInt()?.let { imageSize = it }
            //(map["modelType"] as? String)?.let { modelType = ModelType.valueOf(it) }
            (map["useFp16"] as? Boolean)?.let { useFp16 = it }
            (map["isDrawTextBox"] as? Boolean)?.let { isDrawTextBox = it }
            (map["detParamFilename"] as? String)?.let { detParamFilename = it }
            (map["detBinFilename"] as? String)?.let { detBinFilename = it }
            (map["recParamFilename"] as? String)?.let { recParamFilename = it }
            (map["recBinFilename"] as? String)?.let { recBinFilename = it }
        }
    }

    @JvmOverloads
    fun ocr(
        image: ImageWrapper,
        cpuThreadNum: Int = 0,
        useSlim: Boolean = true
    ): List<AutoXResult> {
        val bitmap = image.bitmap
        if (bitmap == null || bitmap.isRecycled) {
            return emptyList()
        }
        if (!predictor.isLoaded()) {
            initOcr(get(), cpuThreadNum, useSlim)
        }
        return predictor.runOcr(bitmap)
    }

    fun ocr(
        image: ImageWrapper,
        cpuThreadNum: Int,
        myModelPath: String
    ): List<AutoXResult> {

        val bitmap = image.bitmap
        if (bitmap == null || bitmap.isRecycled) {
            return emptyList()
        }
        if (!predictor.isLoaded()) {
            initOcr(myModelPath, cpuThreadNum)
        }
        return predictor.runOcr(bitmap)
    }

    fun ocr(image: ImageWrapper, useSlim: Boolean): List<AutoXResult> {
        return ocr(image, 0, useSlim)
    }

    fun ocr(image: ImageWrapper, myModelPath: String): List<AutoXResult> {
        return ocr(image, 0, myModelPath)
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