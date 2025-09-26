package com.stardust.autojs.onnx

import com.stardust.autojs.runtime.ScriptRuntime
import ai.onnxruntime.OnnxTensor
import android.graphics.Bitmap

class OnnxClassifier(private val runtime: ScriptRuntime) {

    private val wrapper = OnnxWrapper()

    fun classify(bitmap: Bitmap): Int {
        val inputTensor = preprocess(bitmap)
        val outputs = runtime.putProperty("onnx_classifier_input", inputTensor) as Map<String, OnnxTensor>
        val result = runtime.putProperty("onnx_classifier_result", outputs) as Map<String, Any>
        val scores = result.values.firstOrNull() as? FloatArray ?: return -1
        return wrapper.argmax(wrapper.softmax(scores))
    }

    private fun preprocess(bitmap: Bitmap): Map<String, OnnxTensor> {
        // TODO: 根据你的模型输入改 preprocessing
        return emptyMap()
    }
}
