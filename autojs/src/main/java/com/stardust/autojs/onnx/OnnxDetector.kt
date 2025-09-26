package com.stardust.autojs.onnx

import android.util.Log
import org.opencv.core.Mat
import kotlin.math.max
import kotlin.math.min

/**
 * 简单的 ONNX 检测器封装
 */
class OnnxDetector(modelPath: String) {

    private val ortWrapper = OnnxWrapper(modelPath)

    data class DetectionResult(
        val labelIndex: Int,
        val confidence: Float,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float
    )

    /**
     * 输入图像，返回检测框结果
     */
    fun predict(image: Mat): List<DetectionResult> {
        val rawOutput = ortWrapper.run(image)
        val detections = mutableListOf<DetectionResult>()

        // 简单的解析逻辑，具体要根据模型输出调整
        for (i in rawOutput.indices step 6) {
            if (i + 5 >= rawOutput.size) break
            val conf = rawOutput[i + 1]
            if (conf < 0.5f) continue
            detections.add(
                DetectionResult(
                    labelIndex = rawOutput[i].toInt(),
                    confidence = conf,
                    x = rawOutput[i + 2],
                    y = rawOutput[i + 3],
                    w = rawOutput[i + 4],
                    h = rawOutput[i + 5]
                )
            )
        }

        // NMS (非极大值抑制) 示例
        return nms(detections, 0.5f)
    }

    private fun nms(dets: List<DetectionResult>, iouThresh: Float): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        val sorted = dets.sortedByDescending { it.confidence }.toMutableList()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            results.add(best)

            val it = sorted.iterator()
            while (it.hasNext()) {
                val other = it.next()
                if (iou(best, other) > iouThresh) {
                    it.remove()
                }
            }
        }
        return results
    }

    private fun iou(a: DetectionResult, b: DetectionResult): Float {
        val x1 = max(a.x, b.x)
        val y1 = max(a.y, b.y)
        val x2 = min(a.x + a.w, b.x + b.w)
        val y2 = min(a.y + a.h, b.y + b.h)

        val interW = max(0f, x2 - x1)
        val interH = max(0f, y2 - y1)
        val inter = interW * interH
        val union = a.w * a.h + b.w * b.h - inter
        return if (union <= 0) 0f else inter / union
    }
}
