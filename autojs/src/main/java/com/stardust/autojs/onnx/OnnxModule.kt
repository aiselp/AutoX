// autojs/src/main/java/com/stardust/autojs/onnx/OnnxModule.kt
package com.stardust.autojs.onnx

import android.graphics.BitmapFactory
import android.util.Log
import com.stardust.autojs.runtime.ScriptRuntime
import java.io.File

class OnnxModule(private val runtime: ScriptRuntime) {

    private val classifiers = mutableMapOf<String, OnnxClassifier>()
    private val detectors = mutableMapOf<String, OnnxDetector>()

    // === 分类器 - 简化接口 ===

    // 最简单的加载方法 - 只需要名称和路径
    @android.webkit.JavascriptInterface
    fun loadClassifierAuto(name: String, path: String) {
        val classifier = OnnxClassifier(runtime)
        classifier.loadModelAuto(path) // 自动从元数据读取配置
        classifiers[name] = classifier
        
        val initInfo = classifier.getInitializationInfo()
        Log.d("OnnxModule", "分类器 '$name' 自动初始化完成: $initInfo")
    }

    // 原有的加载方法（保持兼容）
    @android.webkit.JavascriptInterface
    fun loadClassifier(name: String, path: String, classNamesJson: String? = null, inputSize: Int = 0) {
        val classifier = OnnxClassifier(runtime)
        classifier.loadModel(path)
        
        if (inputSize > 0) {
            classifier.setInputSize(inputSize)
        }
        
        if (classNamesJson != null) {
            try {
                val arr = org.json.JSONArray(classNamesJson)
                classifier.setClassNames((0 until arr.length()).map { arr.getString(it) })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid classNames JSON", e)
            }
        }
        classifiers[name] = classifier
        
        Log.d("OnnxModule", "分类器 '$name' 加载完成，最终输入尺寸: ${classifier.effectiveInputSize}")
    }

    // 两个参数的方法（保持兼容）
    @android.webkit.JavascriptInterface
    fun loadClassifier(name: String, path: String) {
        loadClassifier(name, path, null, 0)
    }

    // 三个参数的方法（保持兼容）
    @android.webkit.JavascriptInterface
    fun loadClassifier(name: String, path: String, classNamesJson: String?) {
        loadClassifier(name, path, classNamesJson, 0)
    }

    // 专门的尺寸设置方法
    @android.webkit.JavascriptInterface
    fun loadClassifierWithSize(name: String, path: String, inputSize: Int) {
        loadClassifier(name, path, null, inputSize)
    }

    @android.webkit.JavascriptInterface
    fun loadClassifierWithSizeAndNames(name: String, path: String, inputSize: Int, classNamesJson: String) {
        loadClassifier(name, path, classNamesJson, inputSize)
    }

    // 获取分类器初始化信息
    @android.webkit.JavascriptInterface
    fun getClassifierInitInfo(name: String): String {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        val info = c.getInitializationInfo()
        return org.json.JSONObject(info).toString()
    }

    // 获取分类器输入尺寸信息
    @android.webkit.JavascriptInterface
    fun getClassifierInputSizeInfo(name: String): String {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        val info = c.getInputSizeInfo()
        return org.json.JSONObject(info).toString()
    }

    // 获取分类器元数据信息
    @android.webkit.JavascriptInterface
    fun getClassifierMetadata(name: String): String {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        val wrapper = c.getWrapperForDebug()
        val metadataInfo = wrapper?.getMetadataInfo() ?: mapOf("error" to "No wrapper")
        return org.json.JSONObject(metadataInfo).toString()
    }

    // 调试方法：获取详细的分类器状态 - 修复类型问题
    @android.webkit.JavascriptInterface
    fun debugClassifier(name: String): String {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        
        val debugInfo = mutableMapOf<String, Any>()
        
        // 基本信息
        debugInfo["effective_input_size"] = c.effectiveInputSize
        debugInfo["effective_class_names"] = c.effectiveClassNames
        debugInfo["effective_class_count"] = c.effectiveClassNames.size
        
        // 元数据信息 - 修复类型问题
        val wrapper = c.getWrapperForDebug()
        debugInfo["metadata_keys"] = wrapper?.metadata?.keys ?: emptySet<String>()
        
        // 修复：安全处理可能为null的值
        wrapper?.metadata?.get("imgsz")?.let { debugInfo["metadata_imgsz"] = it }
        wrapper?.metadata?.get("names")?.let { debugInfo["metadata_names"] = it }
        
        // 修复：将可空类型转换为非空类型
        debugInfo["parsed_input_size"] = wrapper?.metadataInputSize?.toString() ?: "null"
        debugInfo["parsed_class_names"] = wrapper?.metadataClassNames?.toString() ?: "null"
        
        // 修复：处理可能为null的输入形状
        debugInfo["input_shape"] = wrapper?.inputShape?.contentToString() ?: "null"
        
        // 修复：显式类型转换
        return org.json.JSONObject(debugInfo as Map<*, *>).toString()
    }

    // 智能分类 - 完全自动化
@android.webkit.JavascriptInterface
fun classifyImage(name: String, imagePath: String, topK: Int): Array<Map<String, Any>> {
    val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
    val file = File(imagePath)
    if (!file.exists()) throw IllegalArgumentException("Image not found: $imagePath")
    
    val bitmap = BitmapFactory.decodeFile(imagePath)
        ?: throw RuntimeException("Failed to decode image: $imagePath")
    
    try {
        val inputSize = c.effectiveInputSize
        val config = c.effectivePreprocessConfig
        
        Log.d("OnnxModule", "智能分类 - 尺寸: $inputSize, 预处理: ${config.normalizationType}/${config.resizeMethod}")
        
        // 使用智能预处理
        val input = c.preprocessImage(bitmap)
        
        val results = c.classify(input, topK)
        return results.map { r ->
            mapOf("label" to r.label, "score" to r.score.toDouble())
        }.toTypedArray()
    } finally {
        bitmap.recycle()
    }
}


    // 原有的 classifyImage 方法（保持兼容性）
    @android.webkit.JavascriptInterface
    fun classifyImageWithSize(name: String, imagePath: String, inputSize: Int, topK: Int): Array<Map<String, Any>> {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        val file = File(imagePath)
        if (!file.exists()) throw IllegalArgumentException("Image not found: $imagePath")
        
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw RuntimeException("Failed to decode image: $imagePath")
        
        try {
            Log.d("OnnxModule", "分类图像，指定输入尺寸: $inputSize")
            
            val input = if (inputSize == 32) {
                ImagePreprocessor.preprocessClassification32x32(bitmap)
            } else {
                ImagePreprocessor.preprocessClassification(bitmap, inputSize)
            }
            
            val results = c.classify(input, topK)
            return results.map { r ->
                mapOf("label" to r.label, "score" to r.score.toDouble())
            }.toTypedArray()
        } finally {
            bitmap.recycle()
        }
    }

    // 改进的 getOutputInfo 方法
    @android.webkit.JavascriptInterface
    fun getClassifierOutputInfo(name: String, imagePath: String): String {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        val file = File(imagePath)
        if (!file.exists()) throw IllegalArgumentException("Image not found: $imagePath")
        
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw RuntimeException("Failed to decode image: $imagePath")
        
        try {
            val inputSize = c.effectiveInputSize
            Log.d("OnnxModule", "获取输出信息，使用输入尺寸: $inputSize")
            
            val input = if (inputSize == 32) {
                ImagePreprocessor.preprocessClassification32x32(bitmap)
            } else {
                ImagePreprocessor.preprocessClassification(bitmap, inputSize)
            }
            
            val outputInfo = c.getOutputInfo(input)
            return org.json.JSONObject(outputInfo).toString()
        } finally {
            bitmap.recycle()
        }
    }

    // 原有的 classifyImage32x32 方法（保持兼容性）
    @android.webkit.JavascriptInterface
    fun classifyImage32x32(name: String, imagePath: String, topK: Int): Array<Map<String, Any>> {
        return classifyImageWithSize(name, imagePath, 32, topK)
    }

    // 原有的 getClassifierOutputInfo32x32 方法（保持兼容性）
    @android.webkit.JavascriptInterface
    fun getClassifierOutputInfo32x32(name: String, imagePath: String): String {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        val file = File(imagePath)
        if (!file.exists()) throw IllegalArgumentException("Image not found: $imagePath")
        
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw RuntimeException("Failed to decode image: $imagePath")
        
        try {
            val input = ImagePreprocessor.preprocessClassification32x32(bitmap)
            val outputInfo = c.getOutputInfo(input)
            return org.json.JSONObject(outputInfo).toString()
        } finally {
            bitmap.recycle()
        }
    }

    // === 检测器 ===

    @android.webkit.JavascriptInterface
    fun loadDetector(name: String, path: String, width: Int, height: Int, classNamesJson: String? = null) {
        val detector = OnnxDetector(runtime, width, height)
        detector.loadModel(path)
        if (classNamesJson != null) {
            try {
                val arr = org.json.JSONArray(classNamesJson)
                detector.setClassNames((0 until arr.length()).map { arr.getString(it) })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid classNames JSON", e)
            }
        }
        detectors[name] = detector
    }

    @android.webkit.JavascriptInterface
    fun loadDetector(name: String, path: String, width: Int, height: Int) {
        loadDetector(name, path, width, height, null)
    }

    @android.webkit.JavascriptInterface
    fun detect(name: String, input: FloatArray): Array<Map<String, Any>> {
        val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
        val results = d.detect(input)
        return results.map { r ->
            mapOf(
                "label" to r.label,
                "score" to r.score.toDouble(),
                "box" to listOf(r.box[0].toDouble(), r.box[1].toDouble(), r.box[2].toDouble(), r.box[3].toDouble())
            )
        }.toTypedArray()
    }

    @android.webkit.JavascriptInterface
    fun detectImage(name: String, imagePath: String): Array<Map<String, Any>> {
        val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
        val file = File(imagePath)
        if (!file.exists()) throw IllegalArgumentException("Image not found: $imagePath")
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw RuntimeException("Failed to decode image: $imagePath")
        try {
            val input = ImagePreprocessor.preprocessYoloV8(bitmap, d.inputWidth, d.inputHeight)
            return detect(name, input)
        } finally {
            bitmap.recycle()
        }
    }

    // === 调试方法 ===

    @android.webkit.JavascriptInterface
    fun setClassifierOutputType(name: String, type: String) {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        when (type.toLowerCase()) {
            "logits" -> c.setOutputType(OnnxClassifier.OutputType.LOGITS)
            "probabilities" -> c.setOutputType(OnnxClassifier.OutputType.PROBABILITIES)
            "auto" -> c.setOutputType(OnnxClassifier.OutputType.AUTO_DETECT)
            else -> throw IllegalArgumentException("Unknown output type: $type")
        }
    }

    @android.webkit.JavascriptInterface
    fun debugDetectorOutput(name: String, imagePath: String): String {
        val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
        val file = File(imagePath)
        if (!file.exists()) throw IllegalArgumentException("Image not found: $imagePath")
        
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw RuntimeException("Failed to decode image: $imagePath")
        
        try {
            val input = ImagePreprocessor.preprocessYoloV8(bitmap, d.inputWidth, d.inputHeight)
            val outputInfo = d.debugOutput(input)
            return org.json.JSONObject(outputInfo).toString()
        } finally {
            bitmap.recycle()
        }
    }

    @android.webkit.JavascriptInterface
    fun unloadClassifier(name: String) {
        classifiers[name]?.close()
        classifiers.remove(name)
    }

    @android.webkit.JavascriptInterface
    fun unloadDetector(name: String) {
        detectors[name]?.close()
        detectors.remove(name)
    }

    @android.webkit.JavascriptInterface
    fun cleanup() {
        classifiers.values.forEach { it.close() }
        detectors.values.forEach { it.close() }
        classifiers.clear()
        detectors.clear()
    }
}
