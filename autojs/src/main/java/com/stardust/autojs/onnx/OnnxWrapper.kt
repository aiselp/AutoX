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

    // 读取类别名称 - 修复版本
    val metadataClassNames: List<String>? by lazy {
        try {
            val nameKeys = listOf("names", "labels", "class_names", "classes")
            for (key in nameKeys) {
                val value = metadata[key] ?: continue
                val parsedNames = parseClassNames(value)
                if (parsedNames.isNotEmpty()) {
                    Log.d("OnnxWrapper", "从键 '$key' 解析到 ${parsedNames.size} 个类别名称")
                    return@lazy parsedNames
                }
            }
            null
        } catch (e: Exception) {
            Log.w("OnnxWrapper", "Failed to read class names from metadata", e)
            null
        }
    }

    // 读取输入尺寸 - 修复版本
    val metadataInputSize: Int? by lazy {
        try {
            val sizeKeys = listOf("imgsz", "input_size", "img_size", "input_shape")
            for (key in sizeKeys) {
                val value = metadata[key] ?: continue
                val parsedSize = parseInputSize(value)
                if (parsedSize > 0) {
                    Log.d("OnnxWrapper", "从键 '$key' 解析到输入尺寸: $parsedSize")
                    return@lazy parsedSize
                }
            }
            null
        } catch (e: Exception) {
            Log.w("OnnxWrapper", "Failed to read input size from metadata", e)
            null
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

    // 解析类别名称 - 增强版本
    private fun parseClassNames(value: String): List<String> {
        return try {
            Log.d("OnnxWrapper", "解析类别名称: $value")
            
            when {
                value.startsWith("[") && value.endsWith("]") -> {
                    // JSON 数组格式: ["class0", "class1", ...]
                    val arr = org.json.JSONArray(value)
                    (0 until arr.length()).map { arr.getString(it) }
                }
                value.startsWith("{") && value.endsWith("}") -> {
                    // JSON 对象格式: {"0": "class0", "1": "class1", ...}
                    val obj = org.json.JSONObject(value)
                    val names = mutableListOf<String>()
                    for (i in 0 until obj.length()) {
                        names.add(obj.getString(i.toString()))
                    }
                    names
                }
                value.contains(":") && value.contains("'") -> {
                    // Python 字典格式: {0: '女仆', 1: '小丑', 2: '幽灵', ...}
                    parsePythonDict(value)
                }
                else -> {
                    // 逗号分隔格式: class0,class1,class2,...
                    value.split(Regex("[,;\\n\\r]+"))
                        .map { it.trim().removeSurrounding("'", "'").removeSurrounding("\"", "\"") }
                        .filter { it.isNotEmpty() }
                }
            }.also { names ->
                Log.d("OnnxWrapper", "解析结果: ${names.joinToString()}")
            }
        } catch (e: Exception) {
            Log.w("OnnxWrapper", "Failed to parse class names: $value", e)
            emptyList()
        }
    }

    // 解析 Python 字典格式: {0: '女仆', 1: '小丑', 2: '幽灵', ...}
    private fun parsePythonDict(value: String): List<String> {
        return try {
            val pattern = Regex("""(\d+):\s*'([^']*)'""")
            val matches = pattern.findAll(value)
            val namesMap = mutableMapOf<Int, String>()
            
            for (match in matches) {
                val index = match.groupValues[1].toInt()
                val name = match.groupValues[2]
                namesMap[index] = name
            }
            
            // 按索引排序返回
            namesMap.toSortedMap().values.toList()
        } catch (e: Exception) {
            Log.w("OnnxWrapper", "Failed to parse Python dict: $value", e)
            emptyList()
        }
    }

    // 解析输入尺寸 - 增强版本
    private fun parseInputSize(value: String): Int {
        return try {
            Log.d("OnnxWrapper", "解析输入尺寸: $value")
            
            when {
                value.toIntOrNull() != null -> {
                    // 直接是数字: 32, 128, 224, etc.
                    value.toInt()
                }
                value.startsWith("[") && value.endsWith("]") -> {
                    // 形状数组: [128, 128] 或 [1,3,32,32]
                    if (value.contains(",")) {
                        val numbers = value.removeSurrounding("[", "]")
                            .split(",")
                            .map { it.trim().toIntOrNull() }
                            .filterNotNull()
                        
                        // 对于 [128, 128] 取第一个值
                        // 对于 [1,3,32,32] 取倒数第二个值
                        when (numbers.size) {
                            2 -> numbers[0] // [128, 128]
                            4 -> numbers[2] // [1,3,32,32] 取32
                            else -> numbers.firstOrNull() ?: throw NumberFormatException("Invalid array size")
                        }
                    } else {
                        value.removeSurrounding("[", "]").toInt()
                    }
                }
                value.startsWith("(") && value.endsWith(")") -> {
                    // 元组格式: (3,32,32) 或 (3,128,128)
                    val numbers = value.removeSurrounding("(", ")")
                        .split(",")
                        .map { it.trim().toIntOrNull() }
                        .filterNotNull()
                    
                    when (numbers.size) {
                        3 -> numbers[1] // (3,32,32) 取32
                        else -> numbers.firstOrNull() ?: throw NumberFormatException("Invalid tuple size")
                    }
                }
                else -> throw NumberFormatException("Unsupported format: $value")
            }.also { size ->
                Log.d("OnnxWrapper", "解析到的尺寸: $size")
            }
        } catch (e: Exception) {
            Log.w("OnnxWrapper", "Failed to parse input size: $value", e)
            -1
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
                        buffer.duplicate().get(arr)
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
