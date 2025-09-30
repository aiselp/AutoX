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

    // 增强的元数据读取
    val metadata: Map<String, String> by lazy {
        try {
            val meta = session.metadata
            meta.customMetadata ?: emptyMap()
        } catch (e: Exception) {
            Log.w("OnnxWrapper", "Failed to read model metadata", e)
            emptyMap()
        }
    }

    // 读取类别名称
    val metadataClassNames: List<String>? by lazy {
        try {
            // 尝试多种可能的键名
            val nameKeys = listOf("names", "labels", "class_names", "classes")
            for (key in nameKeys) {
                val value = metadata[key] ?: continue
                return@lazy parseClassNames(value)
            }
            null
        } catch (e: Exception) {
            Log.w("OnnxWrapper", "Failed to read class names from metadata", e)
            null
        }
    }

    // 读取输入尺寸
    val metadataInputSize: Int? by lazy {
        try {
            // 尝试多种可能的键名
            val sizeKeys = listOf("imgsz", "input_size", "img_size", "input_shape")
            for (key in sizeKeys) {
                val value = metadata[key] ?: continue
                return@lazy parseInputSize(value)
            }
            null
        } catch (e: Exception) {
            Log.w("OnnxWrapper", "Failed to read input size from metadata", e)
            null
        }
    }

    // 解析类别名称
    private fun parseClassNames(value: String): List<String> {
        return try {
            if (value.startsWith("[") && value.endsWith("]")) {
                // JSON 数组格式: ["class0", "class1", ...]
                val arr = org.json.JSONArray(value)
                (0 until arr.length()).map { arr.getString(it) }
            } else if (value.startsWith("{") && value.endsWith("}")) {
                // JSON 对象格式: {"0": "class0", "1": "class1", ...}
                val obj = org.json.JSONObject(value)
                (0 until obj.length()).map { obj.getString(it.toString()) }
            } else {
                // 逗号分隔格式: class0,class1,class2,...
                value.split(Regex("[,;\\n\\r]+"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Log.w("OnnxWrapper", "Failed to parse class names: $value", e)
            emptyList()
        }
    }

    // 解析输入尺寸
    private fun parseInputSize(value: String): Int {
        return try {
            when {
                value.toIntOrNull() != null -> {
                    // 直接是数字: 32, 128, 224, etc.
                    value.toInt()
                }
                value.startsWith("[") && value.endsWith("]") -> {
                    // 形状数组: [1,3,32,32] 或 [1,3,128,128]
                    val shapeMatch = Regex("""\[.*?,.*?,(\d+),(\d+)\]""").find(value)
                    shapeMatch?.groupValues?.get(1)?.toInt() ?: throw NumberFormatException("Invalid shape format")
                }
                value.startsWith("(") && value.endsWith(")") -> {
                    // 元组格式: (3,32,32) 或 (3,128,128)
                    val tupleMatch = Regex("""\(.*?,(\d+),(\d+)\)""").find(value)
                    tupleMatch?.groupValues?.get(1)?.toInt() ?: throw NumberFormatException("Invalid tuple format")
                }
                else -> throw NumberFormatException("Unsupported format: $value")
            }
        } catch (e: Exception) {
            Log.w("OnnxWrapper", "Failed to parse input size: $value", e)
            throw e
        }
    }

    // 获取输入形状信息（备用方案）
    val inputShape: IntArray? by lazy {
        try {
            val inputInfo = session.inputInfo
            val inputName = session.inputNames.iterator().next()
            val tensorInfo = inputInfo[inputName]?.info as? ai.onnxruntime.TensorInfo
            tensorInfo?.shape?.map { if (it < 0L) 1L else it }?.map { it.toInt() }?.toIntArray()
        } catch (e: Exception) {
            Log.w("OnnxWrapper", "Failed to get input shape", e)
            null
        }
    }

    // 获取所有元数据信息（用于调试）
    fun getMetadataInfo(): Map<String, Any> {
        return mapOf(
            "all_metadata" to metadata,
            "class_names" to (metadataClassNames ?: "未找到"),
            "input_size" to (metadataInputSize ?: "未找到"),
            "input_shape" to (inputShape?.contentToString() ?: "未找到")
        )
    }

    fun run(input: FloatBuffer): List<FloatArray> {
        val inputName = session.inputNames.iterator().next()
        
        // 安全获取 shape，处理动态维度
        val inputInfo = session.inputInfo
        val tensorInfo = inputInfo[inputName]?.info as? ai.onnxruntime.TensorInfo
        val rawShape = tensorInfo?.shape
        val shape = if (rawShape != null) {
            rawShape.map { if (it < 0L) 1L else it }.toLongArray()
        } else {
            longArrayOf(1, 3, 224, 224) // 默认shape
        }

        val tensor = OnnxTensor.createTensor(env, input, shape)
        val output = session.run(mapOf(inputName to tensor))
        tensor.close()

        val results = mutableListOf<FloatArray>()
        try {
            for (i in 0 until output.size()) {
                val value = output.get(i)
                if (value is OnnxTensor) {
                    val buffer = value.floatBuffer
                    if (buffer != null) {
                        val arr = FloatArray(buffer.remaining())
                        buffer.duplicate().get(arr) // 使用duplicate避免影响原始buffer
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
