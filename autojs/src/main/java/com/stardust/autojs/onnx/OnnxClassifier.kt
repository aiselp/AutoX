// autojs/src/main/java/com/stardust/autojs/onnx/OnnxClassifier.kt
package com.stardust.autojs.onnx

import android.graphics.Bitmap
import android.util.Log
import com.stardust.autojs.runtime.ScriptRuntime
import kotlin.math.abs
import kotlin.math.exp
import java.nio.FloatBuffer

class OnnxClassifier(private val runtime: ScriptRuntime) {

    private var wrapper: OnnxWrapper? = null
    private var _userClassNames: List<String>? = null
    private var _userInputSize: Int? = null

    enum class OutputType {
        LOGITS, PROBABILITIES, AUTO_DETECT
    }
    private var outputType: OutputType = OutputType.AUTO_DETECT
    
    // 预处理配置 - 基于元数据自动创建
    private var preprocessConfig: ImagePreprocessor.PreprocessConfig? = null
    
    // 获取有效的预处理配置
    val effectivePreprocessConfig: ImagePreprocessor.PreprocessConfig
        get() {
            preprocessConfig?.let { return it }
            
            // 从模型元数据自动创建配置
            val config = ImagePreprocessor.createConfigFromMetadata(wrapper?.metadata)
            preprocessConfig = config
            Log.d("OnnxClassifier", "自动创建预处理配置: $config")
            return config
        }

    // 获取有效的输入尺寸：用户设置 > 元数据 > 默认224
    val effectiveInputSize: Int
        get() {
            // 1. 优先使用用户设置的尺寸
            _userInputSize?.let { return it }
            
            // 2. 使用预处理配置中的尺寸（从元数据解析）
            return effectivePreprocessConfig.inputSize
        }

    // 获取有效的类别名称
    val effectiveClassNames: List<String>
        get() {
            // 1. 优先使用用户设置的类别名称
            _userClassNames?.let { 
                if (it.isNotEmpty()) {
                    Log.d("OnnxClassifier", "使用用户设置的类别名称: ${it.size} 个")
                    return it
                }
            }
            
            // 2. 使用元数据中的类别名称
            val fromMeta = wrapper?.metadataClassNames
            if (!fromMeta.isNullOrEmpty()) {
                Log.d("OnnxClassifier", "使用元数据类别名称: ${fromMeta.size} 个")
                return fromMeta
            }
            
            // 3. 尝试从输出维度推断
            try {
                val outputDim = getOutputDimension()
                if (outputDim > 0) {
                    val autoNames = (0 until outputDim).map { "class_$it" }
                    Log.w("OnnxClassifier", "自动生成类别名称: $autoNames")
                    return autoNames
                }
            } catch (e: Exception) {
                Log.w("OnnxClassifier", "Failed to auto-generate class names", e)
            }
            
            Log.e("OnnxClassifier", "无法确定类别名称，返回空列表")
            return emptyList()
        }

    /**
     * 完全自动化的模型加载
     */
    fun loadModelAuto(path: String) {
        wrapper = OnnxWrapper(path)
        Log.d("OnnxClassifier", "模型自动加载完成: $path")
        
        // 初始化预处理配置
        val config = effectivePreprocessConfig
        Log.d("OnnxClassifier", "自动预处理配置: $config")
        Log.d("OnnxClassifier", "自动输入尺寸: $effectiveInputSize")
        Log.d("OnnxClassifier", "自动类别数量: ${effectiveClassNames.size}")
        
        // 记录详细的元数据信息
        wrapper?.metadata?.forEach { (key, value) ->
            Log.d("OnnxClassifier", "元数据: $key = $value")
        }
    }

    /**
     * 完全自动化的图像预处理
     */
    fun preprocessImageAuto(bitmap: Bitmap): FloatArray {
        return ImagePreprocessor.preprocessSmart(bitmap, wrapper?.metadata)
    }

    /**
     * 完全自动化的分类 - 接收Bitmap对象
     */
    fun classifyAuto(bitmap: Bitmap, topK: Int = 1): List<ClassificationResult> {
        val input = preprocessImageAuto(bitmap)
        return classify(input, topK)
    }

    // 获取模型输出维度
    private fun getOutputDimension(): Int {
        return try {
            val inputSize = effectiveInputSize
            val dummyInput = FloatArray(3 * inputSize * inputSize) { 0.1f }
            val output = predict(dummyInput)
            Log.d("OnnxClassifier", "探测到输出维度: ${output.size}")
            output.size
        } catch (e: Exception) {
            Log.w("OnnxClassifier", "Failed to get output dimension", e)
            0
        }
    }

    // 原有的加载方法（保持兼容）
    fun loadModel(path: String) {
        wrapper = OnnxWrapper(path)
        Log.d("OnnxClassifier", "模型加载完成 - $path")
        
        // 记录加载信息
        Log.d("OnnxClassifier", "元数据 keys: ${wrapper?.metadata?.keys}")
        Log.d("OnnxClassifier", "有效输入尺寸: $effectiveInputSize")
        Log.d("OnnxClassifier", "有效类别: $effectiveClassNames")
        Log.d("OnnxClassifier", "预处理配置: $effectivePreprocessConfig")
    }

    fun setClassNames(names: List<String>) {
        _userClassNames = names
        Log.d("OnnxClassifier", "设置类别名称: ${names.size} 个类别")
    }

    fun setInputSize(size: Int) {
        _userInputSize = size
        Log.d("OnnxClassifier", "用户设置输入尺寸: $size")
    }

    // 获取输入尺寸信息
    fun getInputSizeInfo(): Map<String, Any> {
        return mapOf(
            "user_set" to (_userInputSize ?: "未设置"),
            "from_metadata" to (wrapper?.metadataInputSize ?: "无"),
            "effective_size" to effectiveInputSize,
            "preprocess_config" to effectivePreprocessConfig.toString()
        )
    }

    // 获取完整的初始化信息
    fun getInitializationInfo(): Map<String, Any> {
        return mapOf(
            "metadata_found" to !wrapper?.metadata.isNullOrEmpty(),
            "class_names_from_metadata" to (wrapper?.metadataClassNames != null),
            "input_size_from_metadata" to (wrapper?.metadataInputSize != null),
            "effective_class_count" to effectiveClassNames.size,
            "effective_input_size" to effectiveInputSize,
            "preprocess_config" to effectivePreprocessConfig.toString(),
            "all_metadata_keys" to (wrapper?.metadata?.keys ?: emptySet())
        )
    }

    // 获取预处理配置信息
    fun getPreprocessConfigInfo(): Map<String, Any> {
        val config = effectivePreprocessConfig
        return mapOf(
            "input_size" to config.inputSize,
            "normalization_type" to config.normalizationType,
            "resize_method" to config.resizeMethod,
            "pixel_range" to config.pixelRange,
            "mean" to config.mean.toList(),
            "std" to config.std.toList()
        )
    }

    fun setOutputType(type: OutputType) {
        this.outputType = type
        Log.d("OnnxClassifier", "手动设置输出类型: $type")
    }

    data class ClassificationResult(val label: String, val score: Float)

    private fun softmax(logits: FloatArray): FloatArray {
        if (logits.isEmpty()) return floatArrayOf()
        
        val max = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size)
        var sum = 0.0
        
        for (i in logits.indices) {
            val expValue = exp((logits[i] - max).toDouble())
            exps[i] = expValue.toFloat()
            sum += expValue
        }
        
        if (sum == 0.0) {
            val uniform = 1.0f / logits.size
            return FloatArray(logits.size) { uniform }
        }
        
        for (i in exps.indices) {
            exps[i] = (exps[i] / sum).toFloat()
        }
        
        return exps
    }

    private fun detectOutputType(logits: FloatArray): OutputType {
        if (logits.isEmpty()) return OutputType.LOGITS
        
        val sum = logits.sum()
        val min = logits.minOrNull() ?: 0f
        val max = logits.maxOrNull() ?: 0f
        
        Log.d("OnnxClassifier", "输出检测 - 最小值: $min, 最大值: $max, 总和: $sum, 长度: ${logits.size}")
        
        val isProbRange = min >= 0f && max <= 1f
        val sumCloseToOne = abs(sum - 1.0f) < 0.05f
        
        if (isProbRange && sumCloseToOne) {
            Log.d("OnnxClassifier", "检测到输出已经是概率值 (总和: $sum)")
            return OutputType.PROBABILITIES
        }
        
        if (min < 0f) {
            Log.d("OnnxClassifier", "检测到输出包含负值，判断为logits")
            return OutputType.LOGITS
        }
        
        if (max < 10f && !sumCloseToOne) {
            Log.d("OnnxClassifier", "输出值较小但总和不接近1，判断为logits")
            return OutputType.LOGITS
        }
        
        Log.d("OnnxClassifier", "自动检测不确定，默认使用logits")
        return OutputType.LOGITS
    }

    fun classify(input: FloatArray, topK: Int = 1): List<ClassificationResult> {
        val rawOutput = predict(input)
        
        Log.d("OnnxClassifier", "原始输出长度: ${rawOutput.size}")
        Log.d("OnnxClassifier", "有效类别名称: $effectiveClassNames")
        
        if (effectiveClassNames.size != rawOutput.size) {
            Log.w("OnnxClassifier", 
                "警告: 类别名称数量 (${effectiveClassNames.size}) 与输出维度 (${rawOutput.size}) 不匹配")
        }
        
        val currentOutputType = if (outputType == OutputType.AUTO_DETECT) {
            detectOutputType(rawOutput)
        } else {
            outputType
        }
        
        val probs = when (currentOutputType) {
            OutputType.LOGITS -> {
                Log.d("OnnxClassifier", "应用softmax处理logits")
                softmax(rawOutput)
            }
            OutputType.PROBABILITIES -> {
                Log.d("OnnxClassifier", "输出已经是概率，跳过softmax")
                rawOutput
            }
            else -> softmax(rawOutput)
        }
        
        val indexed = mutableListOf<Pair<Int, Float>>()
        for (i in probs.indices) {
            indexed.add(Pair(i, probs[i]))
        }
        
        val sortedResults = indexed.sortedByDescending { it.second }.take(topK)

        Log.d("OnnxClassifier", "Top-$topK 结果:")
        sortedResults.forEach { (idx, score) ->
            val label = if (idx < effectiveClassNames.size) effectiveClassNames[idx] else "class_$idx"
            Log.d("OnnxClassifier", "  $label: $score")
        }

        return sortedResults.map { (idx, score) ->
            ClassificationResult(
                label = if (idx < effectiveClassNames.size) effectiveClassNames[idx] else "class_$idx",
                score = score
            )
        }
    }

    fun predict(input: FloatArray): FloatArray {
        val w = wrapper ?: throw IllegalStateException("Model not loaded")
        val results = w.run(FloatBuffer.wrap(input))
        if (results.isEmpty()) {
            throw IllegalStateException("Model returned empty output")
        }
        return results[0]
    }

    fun getOutputInfo(input: FloatArray): Map<String, Any> {
        val logits = predict(input)
        val detectedType = detectOutputType(logits)
        val probs = if (detectedType == OutputType.LOGITS) softmax(logits) else logits
        
        val result = mutableMapOf<String, Any>()
        result["output_type"] = detectedType.name
        result["output_length"] = logits.size
        result["raw_output_sample"] = logits.take(5).toList()
        result["raw_output_range"] = mapOf<String, Any>(
            "min" to (logits.minOrNull() ?: 0f),
            "max" to (logits.maxOrNull() ?: 0f),
            "sum" to logits.sum()
        )
        result["processed_range"] = mapOf<String, Any>(
            "min" to (probs.minOrNull() ?: 0f),
            "max" to (probs.maxOrNull() ?: 0f),
            "sum" to probs.sum()
        )
        
        val top3List = logits.mapIndexed { index, value -> 
            mapOf<String, Any>("index" to index, "value" to value) 
        }.sortedByDescending { it["value"] as Float }.take(3)
        
        result["top3_raw"] = top3List
        
        return result
    }

    /**
     * 获取输出信息 - 接收Bitmap对象
     */
    fun getOutputInfo(bitmap: Bitmap): Map<String, Any> {
        val input = preprocessImageAuto(bitmap)
        return getOutputInfo(input)
    }

    // 用于调试的辅助方法
    fun getWrapperForDebug(): OnnxWrapper? {
        return wrapper
    }

    fun close() {
        wrapper?.close()
        wrapper = null
    }
}
