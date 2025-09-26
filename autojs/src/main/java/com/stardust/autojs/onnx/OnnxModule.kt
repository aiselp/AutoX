package com.stardust.autojs.onnx

import com.stardust.autojs.runtime.ScriptRuntime
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import android.content.Context

class OnnxModule(private val runtime: ScriptRuntime) {

    private lateinit var session: OrtSession

    fun loadModel(context: Context, modelPath: String) {
        val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
        val modelFile = context.getFileStreamPath(modelPath)
        session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
    }

    fun runModel(inputs: Map<String, OnnxTensor>): Map<String, Any> {
        // 使用 run 替代 evaluate
        val result = session.run(inputs)
        val outputMap = mutableMapOf<String, Any>()
        result.forEach { tensor ->
            outputMap[tensor.key] = tensor.value
        }
        return outputMap
    }

    fun dispose() {
        session.close()
    }
}
