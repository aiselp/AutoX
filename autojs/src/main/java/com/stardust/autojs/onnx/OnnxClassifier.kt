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
        return w.run(inputBuffer)
    }
}
