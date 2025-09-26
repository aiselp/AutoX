package com.stardust.autojs.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer

class OnnxWrapper(modelPath: String) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        session = env.createSession(File(modelPath).absolutePath, OrtSession.SessionOptions())
    }

    fun run(inputBuffer: FloatBuffer): Array<FloatArray> {
        val shape = longArrayOf(1, inputBuffer.remaining().toLong())
        val tensor = OnnxTensor.createTensor(env, inputBuffer, shape)
        session.use { s ->
            val results = s.run(mapOf(s.inputNames.iterator().next() to tensor))
            val output = results[0].value as Array<FloatArray>
            results.forEach { it.close() }
            return output
        }
    }
}
