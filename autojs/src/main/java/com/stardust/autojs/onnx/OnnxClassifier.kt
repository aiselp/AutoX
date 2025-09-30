// autojs/src/main/java/com/stardust/autojs/onnx/OnnxClassifier.kt
package com.stardust.autojs.onnx

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
// 添加预处理配置
    private var preprocessConfig: ImagePreprocessor.PreprocessConfig? = null
    
    // 获取预处理配置
    val effectivePreprocessConfig: ImagePreprocessor.PreprocessConfig
        get() {
            preprocessConfig?.let { return it }
            
            // 自动从元数据推断配置
            val config = ImagePreprocessor.detectConfigFromMetadata(
                wrapper?.metadata, 
                effectiveInputSize
            )
            preprocessConfig = config
            Log.d("OnnxClassifier", "自动推断预处理配置: $config")
            return config
        }

    // 更新加载方法
    fun loadModel(path: String) {
        wrapper = OnnxWrapper(path)
        Log.d("OnnxClassifier", "模型加载完成 - $path")
        
        // 自动推断预处理配置
        val config = effectivePreprocessConfig
        Log.d("OnnxClassifier", "预处理配置: $config")
        
        // ... 其余日志保持不变
    }

    // 添加预处理方法
    fun preprocessImage(bitmap: android.graphics.Bitmap): FloatArray {
        return ImagePreprocessor.preprocessWithConfig(bitmap, effectivePreprocessConfig)
    }
    // 获取有效的输入尺寸：用户设置 > 元数据 > 输入形状 > 默认224
    val effectiveInputSize: Int
        get() {
            // 1. 优先使用用户设置的尺寸
            _userInputSize?.let { return it }
            
            // 2. 尝试从模型元数据读取
            val fromMeta = wrapper?.metadataInputSize
            if (fromMeta != null) {
                Log.d("OnnxClassifier", "从元数据读取输入尺寸: $fromMeta")
                return fromMeta
            }
            
            // 3. 从输入形状推断
            val fromShape = wrapper?.inputShape
            if (fromShape != null && fromShape.size >= 3) {
                val size = fromShape[fromShape.size - 1]
                if (size > 0) {
                    Log.d("OnnxClassifier", "从输入形状推断尺寸: $size")
                    return size
                }
            }
            
            // 4. 默认使用224
            Log.d("OnnxClassifier", "使用默认输入尺寸: 224")
            return 224
        }

    // 获取有效的类别名称 - 修复版本
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
                // 先尝试获取模型输出维度
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

    // 获取模型输出维度
    private fun getOutputDimension(): Int {
        return try {
            // 尝试从输入形状推断输出维度
            wrapper?.inputShape?.let { shape ->
                if (shape.size >= 4) {
                    // 假设分类模型的输出是 [batch, num_classes]
                    // 我们创建一个最小输入来探测输出维度
                    val inputSize = effectiveInputSize
                    val dummyInput = FloatArray(3 * inputSize * inputSize) { 0.1f }
                    val output = predict(dummyInput)
                    Log.d("OnnxClassifier", "探测到输出维度: ${output.size}")
                    return output.size
                }
            }
            0
        } catch (e: Exception) {
            Log.w("OnnxClassifier", "Failed to get output dimension", e)
            0
        }
    }

    fun loadModel(path: String) {
        wrapper = OnnxWrapper(path)
        Log.d("OnnxClassifier", "模型加载完成 - $path")
        Log.d("OnnxClassifier", "元数据 keys: ${wrapper?.metadata?.keys}")
        
        // 详细记录类别名称解析过程
        val metaClassNames = wrapper?.metadataClassNames
        if (metaClassNames != null) {
            Log.d("OnnxClassifier", "成功从元数据解析类别名称: $metaClassNames")
        } else {
            Log.w("OnnxClassifier", "无法从元数据解析类别名称")
            // 记录原始 names 值用于调试
            val namesValue = wrapper?.metadata?.get("names")
            Log.w("OnnxClassifier", "原始 names 值: $namesValue")
        }
        
        Log.d("OnnxClassifier", "元数据输入尺寸: ${wrapper?.metadataInputSize}")
        Log.d("OnnxClassifier", "输入形状: ${wrapper?.inputShape?.contentToString()}")
        Log.d("OnnxClassifier", "有效输入尺寸: $effectiveInputSize")
        Log.d("OnnxClassifier", "有效类别: $effectiveClassNames")
    }

    // 简化的加载方法 - 只需要路径
    fun loadModelAuto(path: String) {
        loadModel(path)
        // 自动设置从元数据读取的类别和尺寸
        wrapper?.metadataClassNames?.let { 
            Log.d("OnnxClassifier", "自动设置类别名称: $it")
        }
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
            "from_shape" to (wrapper?.inputShape?.contentToString() ?: "无"),
            "effective_size" to effectiveInputSize
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
            "all_metadata_keys" to (wrapper?.metadata?.keys ?: emptySet()),
            "metadata_info" to (wrapper?.getMetadataInfo() ?: emptyMap())
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
            // 如果sum为0，均匀分布
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
        
        // 更严格的概率检测
        val isProbRange = min >= 0f && max <= 1f
        val sumCloseToOne = abs(sum - 1.0f) < 0.05f
        
        // 检测标准1：值范围在[0,1]且总和非常接近1 → 已经是概率
        if (isProbRange && sumCloseToOne) {
            Log.d("OnnxClassifier", "检测到输出已经是概率值 (总和: $sum)")
            return OutputType.PROBABILITIES
        }
        
        // 检测标准2：有负值 → 可能是logits
        if (min < 0f) {
            Log.d("OnnxClassifier", "检测到输出包含负值，判断为logits")
            return OutputType.LOGITS
        }
        
        // 检测标准3：如果所有值都很小但为正，也可能是logits
        if (max < 10f && !sumCloseToOne) {
            Log.d("OnnxClassifier", "输出值较小但总和不接近1，判断为logits")
            return OutputType.LOGITS
        }
        
        // 对于不确定的情况，添加详细日志
        Log.d("OnnxClassifier", "自动检测不确定，详细分析:")
        logits.take(5).forEachIndexed { i, v -> 
            Log.d("OnnxClassifier", "  输出[$i] = $v")
        }
        
        // 默认使用logits处理
        return OutputType.LOGITS
    }

    fun classify(input: FloatArray, topK: Int = 1): List<ClassificationResult> {
        val rawOutput = predict(input)
        
        Log.d("OnnxClassifier", "原始输出长度: ${rawOutput.size}")
        Log.d("OnnxClassifier", "有效类别名称: $effectiveClassNames")
        
        // 验证类别名称数量是否匹配
        if (effectiveClassNames.size != rawOutput.size) {
            Log.w("OnnxClassifier", 
                "警告: 类别名称数量 (${effectiveClassNames.size}) 与输出维度 (${rawOutput.size}) 不匹配")
        }
        
        // 自动检测或使用指定类型
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
        
        // 创建带索引的概率列表并排序
        val indexed = mutableListOf<Pair<Int, Float>>()
        for (i in probs.indices) {
            indexed.add(Pair(i, probs[i]))
        }
        
        // 按置信度降序排序并取前topK个
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
        
        // 构建 top3_raw
        val top3List = logits.mapIndexed { index, value -> 
            mapOf<String, Any>("index" to index, "value" to value) 
        }.sortedByDescending { it["value"] as Float }.take(3)
        
        result["top3_raw"] = top3List
        
        return result
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
