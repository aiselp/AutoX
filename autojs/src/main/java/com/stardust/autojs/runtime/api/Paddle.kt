package com.stardust.autojs.runtime.api

//import com.equationl.ncnnandroidppocr.bean.ModelType
import android.content.Context
import com.equationl.ncnnandroidppocr.OcrConfig
import com.equationl.ncnnandroidppocr.OcrConfig.Companion.IMAGE_SIZE_FULL
import com.equationl.ncnnandroidppocr.OcrConfig.Companion.IMAGE_SIZE_SLIM
import com.equationl.ncnnandroidppocr.Predictor
import com.equationl.ncnnandroidppocr.bean.OcrResult
import com.equationl.ncnnandroidppocr.bean.Device
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stardust.app.GlobalAppContext.get
import com.stardust.autojs.core.image.ImageWrapper

class Paddle {

    private val predictor: Predictor
        get() = Predictor.getInstance()
    //private val availableProcessors = Runtime.getRuntime().availableProcessors()
    private var baseConfig: OcrConfig? = null

    private fun getTargetConfig(cpuThreadNum: Int = -1, useSlim: Boolean? = null): OcrConfig {
        val base = baseConfig ?: OcrConfig()
        val target = OcrConfig()
        target.modelPath = base.modelPath
        target.scoreThreshold = base.scoreThreshold
        target.device = base.device
        target.useFp16 = base.useFp16
        target.isDrawTextBox = base.isDrawTextBox
        target.detParamFilename = base.detParamFilename
        target.detBinFilename = base.detBinFilename
        target.recParamFilename = base.recParamFilename
        target.recBinFilename = base.recBinFilename
        target.cpuThreadNum = if (cpuThreadNum > 0) cpuThreadNum else base.cpuThreadNum
        target.imageSize = if (useSlim != null) (if (useSlim) IMAGE_SIZE_SLIM else IMAGE_SIZE_FULL) else base.imageSize
        target.useSlim = useSlim ?: base.useSlim
        return target
    }

    private fun ensureConfig(target: OcrConfig) {
        val current = predictor.ocrConfig
        if (predictor.isLoaded() &&
            current.cpuThreadNum == target.cpuThreadNum &&
            current.imageSize == target.imageSize &&
            current.device == target.device &&
            current.useFp16 == target.useFp16) {
            return
        }
        predictor.initOcrWithConfig(get(), target)
    }

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
        baseConfig = mapToOcrConfig(config)
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
                imageSize = if (useSlim) IMAGE_SIZE_SLIM else IMAGE_SIZE_FULL
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
    fun ocr(image: ImageWrapper, cpuThreadNum: Int = -1, useSlim: Boolean? = null): List<OcrResult> {
        val bitmap = image.bitmap
        if (bitmap == null || bitmap.isRecycled) return emptyList()

        val target = getTargetConfig(cpuThreadNum, useSlim)
        ensureConfig(target)
        return predictor.runOcr(bitmap)
    }

    fun ocr(image: ImageWrapper, cpuThreadNum: Int, myModelPath: String): List<OcrResult> {
        val bitmap = image.bitmap
        if (bitmap == null || bitmap.isRecycled) return emptyList()

        val config = OcrConfig().apply {
            this.cpuThreadNum = cpuThreadNum
            this.modelPath = myModelPath
        }
        if (!predictor.isLoaded() || predictor.ocrConfig != config) {
            predictor.initOcr(get(), myModelPath)
        }
        return predictor.runOcr(bitmap)
    }

    fun ocr(image: ImageWrapper, useSlim: Boolean): List<OcrResult> {
        return ocr(image, -1, useSlim)
    }

    fun ocr(image: ImageWrapper, myModelPath: String): List<OcrResult> {
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