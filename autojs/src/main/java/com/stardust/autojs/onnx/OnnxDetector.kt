// autojs/src/main/java/com/stardust/autojs/onnx/OnnxDetector.kt
package com.stardust.autojs.onnx

import com.stardust.autojs.runtime.ScriptRuntime
import java.nio.FloatBuffer

class OnnxDetector(
    private val runtime: ScriptRuntime,
    var inputWidth: Int = 640,
    var inputHeight: Int = 640
) {

    private var wrapper: OnnxWrapper? = null
    private var _userClassNames: List<String>? = null

    private val effectiveClassNames: List<String>
        get() {
            _userClassNames?.let { return it }
            val fromMeta = wrapper?.metadataClassNames
            if (!fromMeta.isNullOrEmpty()) return fromMeta
            return YoloV8PostProcessor.defaultClassNames
        }

    fun loadModel(path: String) {
        wrapper = OnnxWrapper(path)
    }

    fun setClassNames(names: List<String>) {
        _userClassNames = names
    }

    data class DetectionResult(val label: String, val score: Float, val box: FloatArray)

    fun detect(input: FloatArray): List<DetectionResult> {
        val w = wrapper ?: throw IllegalStateException("Model not loaded")
        val outputs = w.run(FloatBuffer.wrap(input))
        if (outputs.isEmpty()) {
            throw IllegalStateException("Model returned empty output")
        }

        return YoloV8PostProcessor.process(
            outputTensor = outputs[0],
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            classNames = effectiveClassNames,
            confThreshold = 0.25f,
            iouThreshold = 0.45f
        )
    }
}
