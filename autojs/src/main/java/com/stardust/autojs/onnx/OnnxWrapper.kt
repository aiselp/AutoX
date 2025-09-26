package com.stardust.autojs.onnx

import ai.onnxruntime.*

/**
 * ONNX 模型包装类
 */
class OnnxWrapper(modelPath: String, val labels: Array<String>) {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    val inputShape: LongArray
    val outputName: String

    init {
        val options = OrtSession.SessionOptions()
        session = env.createSession(modelPath, options)

        val inputInfo = session.inputInfo.values.first().info as TensorInfo
        inputShape = inputInfo.shape

        outputName = session.outputInfo.keys.first()
    }

    fun run(input: OnnxTensor): Map<String, Any> {
        session.use {
            val results = it.run(mapOf(session.inputNames.first() to input))
            val output = mutableMapOf<String, Any>()
            for ((name, v) in results.withIndices()) {
                when (val v0 = v.value) {
                    is FloatArray -> output[name] = v0
                    is Array<*> -> output[name] = v0 as Array<FloatArray>
                }
            }
            return output
        }
    }
}
