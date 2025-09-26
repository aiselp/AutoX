package com.stardust.autojs.onnx

import android.util.Log
import org.opencv.core.Mat
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 简单的 ONNX 分类器封装
 */
class OnnxClassifier(modelPath: String) {

    private val ortWrapper = OnnxWrapper(modelPath)

    data class ClassificationResult(
        val labelIndex: Int,
        val confidence: Float
    )

    /**
     * 输入图像，返回分类结果
     */
    fun predict(image: Mat): ClassificationResult {
        val logits = ortWrapper.run(image)
        var bestIdx = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in logits.indices) {
            if (logits[i] > bestScore) {
                bestScore = logits[i]
                bestIdx = i
            }
        }
        return ClassificationResult(bestIdx, softmax(logits)[bestIdx])
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxVal = logits.maxOrNull() ?: 0f
        val expVals = FloatArray(logits.size)
        var sum = 0.0
        for (i in logits.indices) {
            expVals[i] = kotlin.math.exp((logits[i] - maxVal).toDouble()).toFloat()
            sum += expVals[i]
        }
        for (i in expVals.indices) {
            expVals[i] = (expVals[i] / sum).toFloat()
        }
        return expVals
    }
}
