// autojs/src/main/java/com/stardust/autojs/onnx/OnnxClassifier.kt
package com.stardust.autojs.onnx

import android.util.Log
import com.stardust.autojs.runtime.ScriptRuntime
import java.nio.FloatBuffer

class OnnxClassifier(private val runtime: ScriptRuntime) {

    private var wrapper: OnnxWrapper? = null
    private var _userClassNames: List<String>? = null

    private val effectiveClassNames: List<String>
        get() {
            _userClassNames?.let { return it }
            val fromMeta = wrapper?.metadataClassNames
            if (!fromMeta.isNullOrEmpty()) return fromMeta
            return emptyList()
        }

    fun loadModel(path: String) {
        wrapper = OnnxWrapper(path)
    }

    fun setClassNames(names: List<String>) {
        _userClassNames = names
    }

    data class ClassificationResult(val label: String, val score: Float)

    private fun softmax(logits: FloatArray): FloatArray {
        if (logits.isEmpty()) return floatArrayOf()
        
        val max = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size)
        var sum = 0.0
        
        for (i in logits.indices) {
            val expValue = kotlin.math.exp((logits[i] - max).toDouble())
            exps[i] = expValue.toFloat()
            sum += expValue
        }
        
        if (sum == 0.0) return exps
        
        for (i in exps.indices) {
            exps[i] = (exps[i] / sum).toFloat()
        }
        
        return exps
    }

    fun classify(input: FloatArray, topK: Int = 1): List<ClassificationResult> {
        val logits = predict(input)
        val probs = softmax(logits)
        val size = probs.size
        
        // 优先使用用户设置的类别名称，然后是模型元数据，最后是自动生成
        val names = when {
            !_userClassNames.isNullOrEmpty() -> {
                if (_userClassNames!!.size == size) {
                    _userClassNames!!
                } else {
                    Log.w("OnnxClassifier", "User class names size (${_userClassNames!!.size}) doesn't match model output size ($size)")
                    (0 until size).map { "class_$it" }
                }
            }
            !wrapper?.metadataClassNames.isNullOrEmpty() -> {
                val metaNames = wrapper!!.metadataClassNames!!
                if (metaNames.size == size) {
                    metaNames
                } else {
                    Log.w("OnnxClassifier", "Metadata class names size (${metaNames.size}) doesn't match model output size ($size)")
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

    fun close() {
        wrapper?.close()
        wrapper = null
    }
}
