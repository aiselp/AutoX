package com.stardust.autojs.onnx

class OnnxWrapper {

    fun softmax(array: FloatArray): FloatArray {
        val max = array.maxOrNull() ?: 0f
        val exps = FloatArray(array.size)
        var sum = 0f
        array.forEachIndexed { i, value ->
            val e = Math.exp((value - max).toDouble()).toFloat()
            exps[i] = e
            sum += e
        }
        return exps.map { it / sum }.toFloatArray()
    }

    fun argmax(array: FloatArray): Int {
        var maxIndex = 0
        var maxValue = Float.NEGATIVE_INFINITY
        array.forEachIndexed { i, value ->
            if (value > maxValue) {
                maxValue = value
                maxIndex = i
            }
        }
        return maxIndex
    }
}
