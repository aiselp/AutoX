// autojs/src/main/java/com/stardust/autojs/onnx/OnnxClassifier.kt
package com.stardust.autojs.onnx

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
        val max = logits.maxOrNull() ?: 0f
        val exps = logits.map { kotlin.math.exp((it - max).toDouble()) }
        val sum = exps.sum()
        return exps.map { (it / sum).toFloat() }.toFloatArray()
    }

    fun classify(input: FloatArray, topK: Int = 1): List<ClassificationResult> {
        val logits = predict(input)
        val probs = softmax(logits)
        val size = probs.size
        val names = if (effectiveClassNames.size == size) {
            effectiveClassNames
        } else {
            (0 until size).map { "class_$it" }
        }

        val indexed = probs.mapIndexed { i, score -> Pair(i, score) }
            .sortedByDescending { it.second }
            .take(topK)

        return indexed.map { (idx, score) ->
            ClassificationResult(names[idx], score)
        }
    }

    fun predict(input: FloatArray): FloatArray {
        val w = wrapper ?: throw IllegalStateException("Model not loaded")
        return w.run(FloatBuffer.wrap(input))[0]
    }
}
