package com.stardust.autojs.onnx

import ai.onnxruntime.*
import java.io.File
import java.nio.FloatBuffer

class OnnxWrapper(modelPath: String) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val sessionOptions = OrtSession.SessionOptions()
        session = env.createSession(File(modelPath).absolutePath, sessionOptions)
    }

    fun run(inputBuffer: FloatBuffer): Array<FloatArray> {
        val shape = longArrayOf(1, inputBuffer.remaining().toLong())
        
        return OnnxTensor.createTensor(env, inputBuffer, shape).use { tensor ->
            session.run(mapOf(session.inputNames.iterator().next() to tensor)).use { results ->
                @Suppress("UNCHECKED_CAST")
                (results[0].value as Array<FloatArray>)
            }
        }
    }

    fun close() {
        try {
            session.close()
        } catch (e: Exception) {
            // 忽略关闭异常
        }
        try {
            env.close()
        } catch (e: Exception) {
            // 忽略关闭异常
        }
    }
}
