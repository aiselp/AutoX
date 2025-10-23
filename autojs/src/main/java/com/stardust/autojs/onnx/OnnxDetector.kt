// autojs/src/main/java/com/stardust/autojs/onnx/OnnxDetector.kt
package com.stardust.autojs.onnx

import android.graphics.Bitmap
import android.util.Log
import com.stardust.autojs.runtime.ScriptRuntime
import java.nio.FloatBuffer
import kotlin.math.min

class OnnxDetector(
    private val runtime: ScriptRuntime,
    var inputWidth: Int = 640,
    var inputHeight: Int = 640
) {

    private var wrapper: OnnxWrapper? = null
    private var _userClassNames: List<String>? = null

    // 预处理配置 - 基于元数据自动创建
    private var preprocessConfig: ImagePreprocessor.PreprocessConfig? = null
    
    // 获取有效的预处理配置
    val effectivePreprocessConfig: ImagePreprocessor.PreprocessConfig
        get() {
            preprocessConfig?.let { return it }
            
            // 从模型元数据自动创建配置
            val config = ImagePreprocessor.createConfigFromMetadata(wrapper?.metadata)
            preprocessConfig = config
            Log.d("OnnxDetector", "自动创建预处理配置: $config")
            return config
        }

    // 获取有效的输入尺寸：用户设置 > 元数据 > 默认640
    val effectiveInputSize: Int
        get() {
            // 使用预处理配置中的尺寸（从元数据解析）
            return effectivePreprocessConfig.inputSize
        }

    // 更新输入尺寸为自动检测的尺寸
    val effectiveInputWidth: Int
        get() = effectiveInputSize

    val effectiveInputHeight: Int
        get() = effectiveInputSize

    // 获取有效的类别名称 - 修复版本
    val effectiveClassNames: List<String>
        get() {
            // 1. 优先使用用户设置的类别名称
            _userClassNames?.let { 
                if (it.isNotEmpty()) {
                    Log.d("OnnxDetector", "使用用户设置的类别名称: ${it.size} 个")
                    return it
                }
            }
            
            // 2. 使用元数据中的类别名称
            val fromMeta = wrapper?.metadataClassNames
            if (!fromMeta.isNullOrEmpty()) {
                Log.d("OnnxDetector", "使用元数据类别名称: ${fromMeta.size} 个 - $fromMeta")
                return fromMeta
            }
            
            // 3. 根据输出维度推断类别数量
            val numClasses = try {
                val outputInfo = getOutputDimensionInfo()
                (outputInfo["num_classes"] as? Int) ?: -1
            } catch (e: Exception) {
                -1
            }
            
            if (numClasses > 0) {
                Log.w("OnnxDetector", "根据输出维度推断类别数量: $numClasses 个")
                return (0 until numClasses).map { "class_$it" }
            }
            
            // 4. 使用默认的COCO类别名称
            Log.w("OnnxDetector", "使用默认COCO类别名称")
            return YoloV8PostProcessor.defaultClassNames
        }

    /**
     * 完全自动化的模型加载
     */
    fun loadModelAuto(path: String) {
        wrapper = OnnxWrapper(path)
        Log.d("OnnxDetector", "检测模型自动加载完成: $path")
        
        // 初始化预处理配置
        val config = effectivePreprocessConfig
        Log.d("OnnxDetector", "自动预处理配置: $config")
        Log.d("OnnxDetector", "自动输入尺寸: $effectiveInputSize")
        Log.d("OnnxDetector", "自动类别数量: ${effectiveClassNames.size}")
        Log.d("OnnxDetector", "自动类别名称: $effectiveClassNames")
        
        // 记录详细的元数据信息
        wrapper?.metadata?.forEach { (key, value) ->
            Log.d("OnnxDetector", "元数据: $key = $value")
        }
        
        // 验证模型配置
        validateModelConfiguration()
        
        // 调试输出维度信息
        val outputInfo = getOutputDimensionInfo()
        Log.d("OnnxDetector", "输出维度信息: $outputInfo")
    }

    /**
     * 获取输出维度信息
     */
    private fun getOutputDimensionInfo(): Map<String, Any> {
        return try {
            val inputSize = effectiveInputSize
            val dummyInput = FloatArray(3 * inputSize * inputSize) { 0.1f }
            val outputs = predict(dummyInput)
            if (outputs.isEmpty()) {
                return mapOf("error" to "No output")
            }
            
            val outputTensor = outputs[0]
            val result = mutableMapOf<String, Any>()
            result["output_size"] = outputTensor.size
            result["output_range"] = mapOf(
                "min" to (outputTensor.minOrNull() ?: 0f),
                "max" to (outputTensor.maxOrNull() ?: 0f)
            )
            
            // 分析可能的输出格式
            val numClasses = effectiveClassNames.size
            val expectedDim = 4 + numClasses

            if (outputTensor.size % expectedDim == 0) {
                result["detected_format"] = "YOLOv8标准格式"
                result["num_boxes"] = outputTensor.size / expectedDim
                result["num_classes"] = numClasses
                result["expected_dim"] = expectedDim
            } else if (outputTensor.size % 84 == 0) {
                result["detected_format"] = "84维格式"
                result["num_boxes"] = outputTensor.size / 84
                result["num_classes"] = 80 // COCO标准
                result["expected_dim"] = 84
            } else {
                result["detected_format"] = "未知格式"
                // 尝试推断类别数量
                for (nc in 1..1000) {
                    if (outputTensor.size % (4 + nc) == 0) {
                        val nb = outputTensor.size / (4 + nc)
                        if (nb > 0 && nb < 10000) {
                            result["inferred_num_classes"] = nc
                            result["inferred_num_boxes"] = nb
                            break
                        }
                    }
                }
            }
            
            result
        } catch (e: Exception) {
            Log.w("OnnxDetector", "获取输出维度信息失败", e)
            mapOf("error" to (e.message ?: "Unknown error"))
        }
    }

    /**
     * 验证模型配置合理性
     */
    private fun validateModelConfiguration() {
        val classCount = effectiveClassNames.size
        val inputSize = effectiveInputSize
        
        Log.d("OnnxDetector", "配置验证 - 类别: $classCount, 输入尺寸: $inputSize")
        
        // 检测模型类型
        val metadata = wrapper?.metadata
        val task = metadata?.get("task") ?: ""
        val description = metadata?.get("description") ?: ""
        
        if (task.contains("detect") || description.contains("detect")) {
            Log.d("OnnxDetector", "检测到目标检测模型")
        } else {
            Log.w("OnnxDetector", "模型可能不是目标检测模型，请确认")
        }
    }

    /**
     * 完全自动化的图像预处理
     */
    fun preprocessImageAuto(bitmap: Bitmap): FloatArray {
        return ImagePreprocessor.preprocessSmart(bitmap, wrapper?.metadata)
    }

    /**
     * 完全自动化的目标检测 - 接收Bitmap对象
     */
    fun detectAuto(bitmap: Bitmap, confThreshold: Float = 0.25f, iouThreshold: Float = 0.45f): List<DetectionResult> {
        val input = preprocessImageAuto(bitmap)
        return detectWithInput(input, confThreshold, iouThreshold)
    }

    // 原有的加载方法（保持兼容）
    fun loadModel(path: String) {
        wrapper = OnnxWrapper(path)
        Log.d("OnnxDetector", "检测模型加载完成，输入尺寸: ${inputWidth}x${inputHeight}")
        Log.d("OnnxDetector", "自动输入尺寸: $effectiveInputSize")
        Log.d("OnnxDetector", "自动类别名称: $effectiveClassNames")
    }

    fun setClassNames(names: List<String>) {
        _userClassNames = names
        Log.d("OnnxDetector", "设置检测类别名称: ${names.size} 个类别 - $names")
    }

    data class DetectionResult(val label: String, val score: Float, val box: FloatArray)

    /**
     * 预测方法
     */
    private fun predict(input: FloatArray): List<FloatArray> {
        val w = wrapper ?: throw IllegalStateException("Model not loaded")
        return w.run(FloatBuffer.wrap(input))
    }

    /**
     * 使用预处理后的输入进行检测 - 修复版本
     */
    fun detectWithInput(input: FloatArray, confThreshold: Float = 0.25f, iouThreshold: Float = 0.45f): List<DetectionResult> {
        val outputs = predict(input)
        if (outputs.isEmpty()) {
            throw IllegalStateException("Model returned empty output")
        }

        Log.d("OnnxDetector", "开始后处理，输出长度: ${outputs[0].size}, 类别数: ${effectiveClassNames.size}")
        
        return YoloV8PostProcessor.process(
            outputTensor = outputs[0],
            inputWidth = effectiveInputWidth,
            inputHeight = effectiveInputHeight,
            classNames = effectiveClassNames,
            confThreshold = confThreshold,
            iouThreshold = iouThreshold
        )
    }

    // 原有的detect方法（保持兼容）
    fun detect(input: FloatArray): List<DetectionResult> {
        return detectWithInput(input)
    }

    /**
     * 调试方法：直接处理输出并返回详细信息 - 接收Bitmap对象
     */
    fun debugDetection(bitmap: Bitmap, confThreshold: Float = 0.25f, iouThreshold: Float = 0.45f): Map<String, Any> {
        val input = preprocessImageAuto(bitmap)
        val outputs = predict(input)
        if (outputs.isEmpty()) {
            throw IllegalStateException("Model returned empty output")
        }
        
        val outputTensor = outputs[0]
        val result = mutableMapOf<String, Any>()
        
        result["output_size"] = outputTensor.size
        result["output_range"] = mapOf(
            "min" to (outputTensor.minOrNull() ?: 0f),
            "max" to (outputTensor.maxOrNull() ?: 0f)
        )
        
        // 分析输出结构
        val numClasses = effectiveClassNames.size
        val expectedDim = 4 + numClasses
        result["num_classes"] = numClasses
        result["expected_dim"] = expectedDim
        
        if (outputTensor.size % expectedDim == 0) {
            result["detected_format"] = "YOLOv8标准格式"
            result["num_boxes"] = outputTensor.size / expectedDim
        } else {
            result["detected_format"] = "未知格式"
        }
        
        // 处理并返回实际检测结果用于调试
        val detectionResults = detectWithInput(input, confThreshold, iouThreshold)
        result["actual_detections"] = detectionResults.map { 
            mapOf(
                "label" to it.label,
                "score" to it.score,
                "box" to it.box.toList()
            )
        }
        
        // 尝试处理几个框看看原始输出
        val sampleBoxes = mutableListOf<Map<String, Any>>()
        val numBoxes = min(5, outputTensor.size / expectedDim)
        
        for (i in 0 until numBoxes) {
            val offset = i * expectedDim
            val boxInfo = mutableMapOf<String, Any>()
            
            boxInfo["index"] = i
            boxInfo["x_center"] = outputTensor[offset]
            boxInfo["y_center"] = outputTensor[offset + 1]
            boxInfo["width"] = outputTensor[offset + 2]
            boxInfo["height"] = outputTensor[offset + 3]
            
            // 获取类别分数
            val classScores = mutableListOf<Float>()
            for (c in 0 until numClasses) {
                classScores.add(outputTensor[offset + 4 + c])
            }
            boxInfo["class_scores"] = classScores
            boxInfo["max_score"] = classScores.maxOrNull() ?: 0f
            boxInfo["max_class_id"] = classScores.indexOf(classScores.maxOrNull() ?: 0f)
            boxInfo["confidence"] = YoloV8PostProcessor.sigmoid(classScores.maxOrNull() ?: 0f)
            
            sampleBoxes.add(boxInfo)
        }
        
        result["sample_boxes"] = sampleBoxes
        
        return result
    }

    /**
     * 获取预处理配置信息
     */
    fun getPreprocessConfigInfo(): Map<String, Any> {
        val config = effectivePreprocessConfig
        return mapOf(
            "input_size" to config.inputSize,
            "normalization_type" to config.normalizationType,
            "resize_method" to config.resizeMethod,
            "pixel_range" to config.pixelRange,
            "mean" to config.mean.toList(),
            "std" to config.std.toList()
        )
    }

    /**
     * 获取初始化信息
     */
    fun getInitializationInfo(): Map<String, Any> {
        return mapOf(
            "metadata_found" to !wrapper?.metadata.isNullOrEmpty(),
            "class_names_from_metadata" to (wrapper?.metadataClassNames != null),
            "input_size_from_metadata" to (wrapper?.metadataInputSize != null),
            "effective_class_count" to effectiveClassNames.size,
            "effective_input_size" to effectiveInputSize,
            "preprocess_config" to effectivePreprocessConfig.toString(),
            "all_metadata_keys" to (wrapper?.metadata?.keys ?: emptySet())
        )
    }

    /**
     * 获取检测器元数据信息
     */
    fun getMetadataInfo(): Map<String, Any> {
        val wrapper = wrapper ?: return mapOf("error" to "No wrapper")
        val metadataInfo = wrapper.getMetadataInfo()
        
        // 添加类别名称信息
        val result = mutableMapOf<String, Any>()
        result.putAll(metadataInfo)
        result["effective_class_names"] = effectiveClassNames
        result["effective_class_count"] = effectiveClassNames.size
        
        return result
    }

    /**
     * 获取输入尺寸信息
     */
    fun getInputSizeInfo(): Map<String, Any> {
        return mapOf(
            "user_set" to "${inputWidth}x${inputHeight}",
            "from_metadata" to (wrapper?.metadataInputSize ?: "无"),
            "effective_size" to effectiveInputSize,
            "preprocess_config" to effectivePreprocessConfig.toString()
        )
    }

    fun debugOutput(input: FloatArray): Map<String, Any> {
        val outputs = predict(input)
        if (outputs.isEmpty()) {
            throw IllegalStateException("Model returned empty output")
        }
        
        val outputTensor = outputs[0]
        
        val result = mutableMapOf<String, Any>()
        result["output_size"] = outputTensor.size
        result["output_sample"] = outputTensor.take(20).toList()
        result["output_range"] = mapOf(
            "min" to (outputTensor.minOrNull() ?: 0f),
            "max" to (outputTensor.maxOrNull() ?: 0f)
        )
        
        // 分析输出结构
        val numClasses = effectiveClassNames.size
        val expectedDim = 4 + numClasses
        if (outputTensor.size % expectedDim == 0) {
            result["detected_format"] = "YOLOv8标准格式"
            result["num_boxes"] = outputTensor.size / expectedDim
            result["num_classes"] = numClasses
            result["expected_dim"] = expectedDim
        } else {
            result["detected_format"] = "未知格式"
            result["analysis_note"] = "输出维度可能与预期不符"
        }
        
        return result
    }

    // 用于调试的辅助方法
    fun getWrapperForDebug(): OnnxWrapper? {
        return wrapper
    }

    fun close() {
        wrapper?.close()
        wrapper = null
    }
}
