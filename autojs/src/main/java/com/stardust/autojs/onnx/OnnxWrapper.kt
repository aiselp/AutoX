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
                Log.d("OnnxWrapper", "尝试从键 '$key' 解析类别名称: $value")
                val parsedNames = parseClassNames(value)
                if (parsedNames.isNotEmpty()) {
                    Log.d("OnnxWrapper", "从键 '$key' 成功解析到 ${parsedNames.size} 个类别名称: ${parsedNames.joinToString()}")
                    return@lazy parsedNames
                } else {
                    Log.w("OnnxWrapper", "从键 '$key' 解析类别名称失败")
                }
            }
            Log.w("OnnxWrapper", "所有键都未能解析出类别名称")
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
                    try {
                        val obj = org.json.JSONObject(value)
                        val names = mutableListOf<String>()
                        for (i in 0 until obj.length()) {
                            names.add(obj.getString(i.toString()))
                        }
                        names
                    } catch (e: Exception) {
                        Log.d("OnnxWrapper", "JSONObject解析失败，尝试Python字典格式")
                        parsePythonDict(value)
                    }
                }
                value.contains(":") && (value.contains("'") || value.contains("\"")) -> {
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

    // 解析 Python 字典格式 - 完全重写版本
    private fun parsePythonDict(value: String): List<String> {
        return try {
            Log.d("OnnxWrapper", "解析Python字典: $value")
            
            val namesMap = mutableMapOf<Int, String>()
            var currentIndex = -1
            var currentName = StringBuilder()
            var inString = false
            var inKey = true
            var stringQuoteChar = ' '
            
            // 手动解析字典格式
            for (i in value.indices) {
                val char = value[i]
                
                when {
                    // 检测键的开始（数字）
                    inKey && char.isDigit() -> {
                        currentIndex = char.toString().toInt()
                        // 处理多位数
                        var j = i + 1
                        while (j < value.length && value[j].isDigit()) {
                            currentIndex = currentIndex * 10 + value[j].toString().toInt()
                            j++
                        }
                        Log.d("OnnxWrapper", "找到索引: $currentIndex")
                    }
                    // 检测键值分隔符
                    inKey && char == ':' -> {
                        inKey = false
                        Log.d("OnnxWrapper", "切换到值解析")
                    }
                    // 检测字符串开始
                    !inKey && !inString && (char == '\'' || char == '"') -> {
                        inString = true
                        stringQuoteChar = char
                        currentName = StringBuilder()
                        Log.d("OnnxWrapper", "开始解析字符串值")
                    }
                    // 检测字符串结束
                    !inKey && inString && char == stringQuoteChar -> {
                        inString = false
                        inKey = true
                        
                        val name = currentName.toString()
                        if (currentIndex != -1 && name.isNotEmpty()) {
                            namesMap[currentIndex] = name
                            Log.d("OnnxWrapper", "添加类别: $currentIndex -> $name")
                        }
                        
                        currentIndex = -1
                        Log.d("OnnxWrapper", "完成解析字符串值")
                    }
                    // 在字符串中收集字符
                    !inKey && inString -> {
                        currentName.append(char)
                    }
                    // 跳过空格和逗号
                    char.isWhitespace() || char == ',' -> {
                        // 忽略空白和分隔符
                    }
                }
            }
            
            Log.d("OnnxWrapper", "总共找到 ${namesMap.size} 个类别")
            
            // 按索引排序返回
            val sortedNames = namesMap.toSortedMap().values.toList()
            Log.d("OnnxWrapper", "排序后的类别: ${sortedNames.joinToString()}")
            
            sortedNames
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
        "input_shape" to (inputShape?.contentToString() ?: "未找到"),
        "all_metadata_keys" to metadata.keys.toList()  // 修复这里
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
