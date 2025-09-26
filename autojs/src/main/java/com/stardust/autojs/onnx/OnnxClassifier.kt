package com.stardust.autojs.onnx

import com.stardust.autojs.runtime.ScriptRuntime
import java.nio.FloatBuffer

class OnnxClassifier(private val runtime: ScriptRuntime) {

    private var wrapper: OnnxWrapper? = null

    fun loadModel(path: String) {
        wrapper = OnnxWrapper(path)
    }

    fun predict(input: FloatArray): FloatArray {
        val w = wrapper ?: throw IllegalStateException("Model not loaded. Call loadModel() first.")
        
        // 确保输入缓冲区是可读的
        val inputBuffer = FloatBuffer.wrap(input)
        
        val outputArray = w.run(inputBuffer)
        
        if (outputArray.isEmpty()) {
            throw IllegalStateException("Model returned empty output")
        }
        
        // 返回第一个输出（假设单输出模型）
        return outputArray[0]
    }

    fun unloadModel() {
        wrapper?.close()
        wrapper = null
    }
}
