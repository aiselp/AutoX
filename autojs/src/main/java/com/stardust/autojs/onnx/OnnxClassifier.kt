package com.stardust.autojs.onnx

import com.stardust.autojs.runtime.ScriptRuntime
import java.nio.FloatBuffer

class OnnxClassifier(private val runtime: ScriptRuntime) {

    private var wrapper: OnnxWrapper? = null

    fun loadModel(path: String) {
        wrapper = OnnxWrapper(path)
    }

    fun predict(input: FloatArray): FloatArray {
        val w = wrapper ?: throw IllegalStateException("Model not loaded")
        val inputBuffer: FloatBuffer = FloatBuffer.wrap(input)
        val output = w.run(inputBuffer)
        
        // 修复：从 Array<FloatArray> 中提取第一个 FloatArray
        return output.firstOrNull() ?: throw IllegalStateException("No output from model")
    }
    
    // 可选：如果需要处理多个输出
    fun predictMultiple(input: FloatArray): Array<FloatArray> {
        val w = wrapper ?: throw IllegalStateException("Model not loaded")
        val inputBuffer: FloatBuffer = FloatBuffer.wrap(input)
        return w.run(inputBuffer)
    }
}
