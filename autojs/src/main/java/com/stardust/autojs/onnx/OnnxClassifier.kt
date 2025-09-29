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
    private var _userInputSize: Int? = null  // 用户设置的输入尺寸
    private var outputType: OutputType = OutputType.AUTO_DETECT

    enum class OutputType {
        LOGITS, PROBABILITIES, AUTO_DETECT
    }

    // 获取有效的输入尺寸：用户设置 > 元数据 > 默认224
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
                // 形状通常是 [batch, channels, height, width]
                val size = fromShape[fromShape.size - 1] // 取最后一个维度作为尺寸
                if (size > 0) {
                    Log.d("OnnxClassifier", "从输入形状推断尺寸: $size")
                    return size
                }
            }
            
            // 4. 默认使用224
            Log.d("OnnxClassifier", "使用默认输入尺寸: 224")
            return 224
        }

    private val effectiveClassNames: List<String>
        get() {
            _userClassNames?.let { return it }
            val fromMeta = wrapper?.metadataClassNames
            if (!fromMeta.isNullOrEmpty()) return fromMeta
            return emptyList()
        }

    fun loadModel(path: String) {
        wrapper = OnnxWrapper(path)
        Log.d("OnnxClassifier", "模型加载完成")
        Log.d("OnnxClassifier", "元数据类别: ${wrapper?.metadataClassNames}")
        Log.d("OnnxClassifier", "元数据输入尺寸: ${wrapper?.metadataInputSize}")
        Log.d("OnnxClassifier", "输入形状: ${wrapper?.inputShape?.contentToString()}")
        Log.d("OnnxClassifier", "有效输入尺寸: $effectiveInputSize")
    }

    fun setClassNames(names: List<String>) {
        _userClassNames = names
        Log.d("OnnxClassifier", "设置类别名称: ${names.size} 个类别")
    }

    // 新增：设置输入尺寸
    fun setInputSize(size: Int) {
        _userInputSize = size
        Log.d("OnnxClassifier", "用户设置输入尺寸: $size")
    }

    // 新增：获取输入尺寸信息
    fun getInputSizeInfo(): Map<String, Any> {
        return mapOf(
            "user_set" to (_userInputSize ?: "未设置"),
            "from_metadata" to (wrapper?.metadataInputSize ?: "无"),
            "from_shape" to (wrapper?.inputShape?.contentToString() ?: "无"),
            "effective_size" to effectiveInputSize
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
        Log.d("OnnxClassifier", "原始输出前5个值: ${rawOutput.take(5).joinToString()}")
        
        // 自动检测或使用指定类型
        val currentOutputType = if (outputType == OutputType.AUTO_DETECT) {
            detectOutputType(rawOutput)
        } else {
            outputType
        }
        
        val probs = when (currentOutputType) {
            OutputType.LOGITS -> {
                Log.d("OnnxClassifier", "应用softmax处理logits")
                val softmaxResult = softmax(rawOutput)
                // 验证softmax结果
                if (softmaxResult.sum().isNaN()) {
                    Log.e("OnnxClassifier", "Softmax结果异常，使用原始值")
                    rawOutput
                } else {
                    softmaxResult
                }
            }
            OutputType.PROBABILITIES -> {
                Log.d("OnnxClassifier", "输出已经是概率，跳过softmax")
                rawOutput
            }
            else -> {
                softmax(rawOutput)
            }
        }
        
        // 记录处理后的概率信息
        Log.d("OnnxClassifier", "最终概率 - 最大: ${probs.maxOrNull()}, 总和: ${probs.sum()}")
        Log.d("OnnxClassifier", "前5个概率: ${probs.take(5).joinToString()}")
        
        val size = probs.size
        
        // 优先使用用户设置的类别名称，然后是模型元数据，最后是自动生成
        val names = when {
            !_userClassNames.isNullOrEmpty() -> {
                if (_userClassNames!!.size == size) {
                    _userClassNames!!
                } else {
                    Log.w("OnnxClassifier", "用户类别名称数量 (${_userClassNames!!.size}) 与模型输出数量 ($size) 不匹配")
                    (0 until size).map { "class_$it" }
                }
            }
            !wrapper?.metadataClassNames.isNullOrEmpty() -> {
                val metaNames = wrapper!!.metadataClassNames!!
                if (metaNames.size == size) {
                    metaNames
                } else {
                    Log.w("OnnxClassifier", "元数据类别名称数量 (${metaNames.size}) 与模型输出数量 ($size) 不匹配")
                    (0 until size).map { "class_$it" }
                }
            }
            else -> (0 until size).map { "class_$it" }
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
            val label = if (idx < names.size) names[idx] else "class_$idx"
            Log.d("OnnxClassifier", "  $label: $score")
        }

        return sortedResults.map { (idx, score) ->
            ClassificationResult(
                label = if (idx < names.size) names[idx] else "class_$idx",
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

    fun close() {
        wrapper?.close()
        wrapper = null
    }
}
