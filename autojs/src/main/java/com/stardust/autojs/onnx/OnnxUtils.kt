package com.stardust.autojs.onnx

object OnnxUtils {

    fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exp = logits.map { Math.exp((it - max).toDouble()).toFloat() }
        val sum = exp.sum()
        return exp.map { it / sum }.toFloatArray()
    }

    fun argMax(values: FloatArray): Int {
        var maxIndex = 0
        var maxValue = Float.NEGATIVE_INFINITY
        for ((i, v) in values.withIndex()) {
            if (v > maxValue) {
                maxValue = v
                maxIndex = i
            }
        }
        return maxIndex
    }
}
