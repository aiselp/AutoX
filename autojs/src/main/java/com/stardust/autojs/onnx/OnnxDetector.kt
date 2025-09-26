package com.stardust.autojs.onnx

import com.stardust.autojs.runtime.ScriptRuntime
import ai.onnxruntime.OnnxTensor
import android.graphics.Bitmap

class OnnxDetector(private val runtime: ScriptRuntime) {

    private val wrapper = OnnxWrapper()

    fun detect(bitmap: Bitmap): List<FloatArray> {
        val inputTensor = preprocess(bitmap)
        val outputs = runtime.putProperty("onnx_detector_input", inputTensor) as Map<String, OnnxTensor>
        val result = runtime.putProperty("onnx_detector_result", outputs) as Map<String, Any>
        val detections = mutableListOf<FloatArray>()
        result.values.forEach { output ->
            if (output is FloatArray) {
                detections.add(output)
            }
        }
        return detections
    }

    private fun preprocess(bitmap: Bitmap): Map<String, OnnxTensor> {
        // TODO: 根据你的模型输入改 preprocessing
        return emptyMap()
    }
}
