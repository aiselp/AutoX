// autojs/src/main/java/com/stardust/autojs/onnx/OnnxDetector.kt
package com.stardust.autojs.onnx

import android.util.Log
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
        Log.d("OnnxDetector", "检测模型加载完成，输入尺寸: ${inputWidth}x${inputHeight}")
        Log.d("OnnxDetector", "元数据类别: ${wrapper?.metadataClassNames}")
    }

    fun setClassNames(names: List<String>) {
        _userClassNames = names
        Log.d("OnnxDetector", "设置检测类别名称: ${names.size} 个类别")
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

    fun debugOutput(input: FloatArray): Map<String, Any> {
        val w = wrapper ?: throw IllegalStateException("Model not loaded")
        val outputs = w.run(FloatBuffer.wrap(input))
        if (outputs.isEmpty()) {
            throw IllegalStateException("Model returned empty output")
        }
        
        val outputTensor = outputs[0]
        
        val result = mutableMapOf<String, Any>()
        result["output_size"] = outputTensor.size
        result["output_sample"] = outputTensor.take(20).toList()
        result["output_range"] = mapOf<String, Any>(
            "min" to (outputTensor.minOrNull() ?: 0f),
            "max" to (outputTensor.maxOrNull() ?: 0f)
        )
        
        // 分析输出结构
        val numClasses = effectiveClassNames.size
        val expectedDim = 4 + numClasses
        if (outputTensor.size % expectedDim == 0) {
            result["detected_format"] = "YOLOv8标准格式"
            result["num_boxes"] = outputTensor.size / expectedDim
        }
        
        return result
    }

    fun close() {
        wrapper?.close()
        wrapper = null
    }
}
