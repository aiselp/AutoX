package com.stardust.autojs.onnx

import com.stardust.autojs.runtime.ScriptRuntime
import java.nio.FloatBuffer

class OnnxDetector(private val runtime: ScriptRuntime) {

    private var wrapper: OnnxWrapper? = null

    fun loadModel(path: String) {
        wrapper = OnnxWrapper(path)
    }

    data class DetectionResult(val label: String, val score: Float, val box: FloatArray)

    fun detect(input: FloatArray): List<DetectionResult> {
        val w = wrapper ?: throw IllegalStateException("Model not loaded")
        val output = w.run(FloatBuffer.wrap(input))
        // 假设输出为 [N, 6]，每行: [label_idx, score, x1, y1, x2, y2]
        val results = mutableListOf<DetectionResult>()
        for ((idx, row) in output.withIndex()) {
            if (row.size < 6) continue
            val label = "class_${row[0].toInt()}"
            results.add(DetectionResult(label, row[1], row.sliceArray(2..5)))
        }
        return results
    }
}
