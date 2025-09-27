// autojs/src/main/java/com/stardust/autojs/onnx/OnnxWrapper.kt
package com.stardust.autojs.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.nio.FloatBuffer

class OnnxWrapper(modelPath: String) {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelPath)

    val metadataClassNames: List<String>? by lazy {
        try {
            val meta = session.metadata
            val props = meta.customMetadata
            val candidates = listOf("names", "labels", "class_names", "classes")
            for (key in candidates) {
                val value = props[key] ?: continue
                return@lazy if (value.startsWith("[") && value.endsWith("]")) {
                    val arr = org.json.JSONArray(value)
                    (0 until arr.length()).map { arr.getString(it) }
                } else {
                    value.split(Regex("[,\\n\\r]+")).map { it.trim() }.filter { it.isNotEmpty() }
                }
            }
            null
        } catch (e: Exception) {
            Log.w("OnnxWrapper", "Failed to read class names from model metadata", e)
            null
        }
    }

    fun run(input: FloatBuffer): List<FloatArray> {
        val inputName = session.inputNames[0]
        // 安全获取 shape，处理 -1（动态维度）替换为 1
        val rawShape = session.inputInfo[inputName]?.shape
        val shape = rawShape?.map { if (it < 0L) 1L else it }?.toLongArray()
            ?: longArrayOf(1, 3, 224, 224)

        val tensor = OnnxTensor.createTensor(env, input, shape)
        val output = session.run(mapOf(inputName to tensor))
        tensor.close()

        val results = mutableListOf<FloatArray>()
        try {
            for (value in output.values) {
                if (value is OnnxTensor) {
                    val buffer = value.floatBuffer
                    if (buffer != null) {
                        val arr = FloatArray(buffer.remaining())
                        buffer.get(arr)
                        results.add(arr)
                    } else {
                        results.add(floatArrayOf())
                    }
                } else {
                    results.add(floatArrayOf())
                }
                value.close()
            }
        } finally {
            output.close()
        }
        return results
    }

    fun close() {
        session.close()
        env.close()
    }
}
