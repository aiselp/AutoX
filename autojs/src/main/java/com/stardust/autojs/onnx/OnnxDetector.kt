package com.stardust.autojs.onnx

import org.opencv.core.Mat
import kotlin.math.max
import kotlin.math.min

/**
 * 简单的 ONNX 检测器
 */
class OnnxDetector(private val wrapper: OnnxWrapper) {

    data class Detection(val label: String, val confidence: Float, val box: FloatArray)

    fun detect(image: Mat, confThreshold: Float = 0.5f): List<Detection> {
        val input = OnnxUtils.matToFloatTensor(image, wrapper.inputShape)
        val results = wrapper.run(input)

        // 假设输出是 Nx6: [x1, y1, x2, y2, score, classId]
        val dets = results[wrapper.outputName] as Array<FloatArray>

        val output = mutableListOf<Detection>()
        for (d in dets) {
            val score = d[4]
            if (score >= confThreshold) {
                val clsId = d[5].toInt()
                val label = wrapper.labels.getOrElse(clsId) { "cls_$clsId" }
                val box = floatArrayOf(
                    min(d[0], d[2]),
                    min(d[1], d[3]),
                    max(d[0], d[2]),
                    max(d[1], d[3])
                )
                output.add(Detection(label, score, box))
            }
        }
        return output
    }
}
