// autojs/src/main/java/com/stardust/autojs/onnx/OnnxModule.kt
package com.stardust.autojs.onnx

import android.graphics.BitmapFactory
import android.util.Log
import com.stardust.autojs.runtime.ScriptRuntime
import java.io.File

class OnnxModule(private val runtime: ScriptRuntime) {

    private val classifiers = mutableMapOf<String, OnnxClassifier>()
    private val detectors = mutableMapOf<String, OnnxDetector>()

    // === 目标检测 - 全自动接口 ===

    /**
     * 完全自动化的检测器加载 - 推荐使用
     */
    @android.webkit.JavascriptInterface
    fun loadDetectorFullAuto(name: String, path: String) {
        val detector = OnnxDetector(runtime)
        detector.loadModelAuto(path) // 使用完全自动化的加载
        
        detectors[name] = detector
        
        val initInfo = detector.getInitializationInfo()
        Log.d("OnnxModule", "检测器 '$name' 全自动加载完成: $initInfo")
    }

    /**
     * 智能检测 - 完全自动化处理
     * @param name 检测器名称
     * @param imagePath 图像路径
     * @param confThreshold 置信度阈值 (0-1)，默认0.25，值越大检测越严格
     * @param iouThreshold IOU阈值 (0-1)，默认0.45，值越大保留的框越多
     */
    @android.webkit.JavascriptInterface  
    fun detectImageAuto(name: String, imagePath: String, confThreshold: Float = 0.25f, iouThreshold: Float = 0.45f): Array<Map<String, Any>> {
        val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
        val file = File(imagePath)
        if (!file.exists()) throw IllegalArgumentException("Image not found: $imagePath")
        
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw RuntimeException("Failed to decode image: $imagePath")
        
        try {
            Log.d("OnnxModule", "开始智能检测 - 模型: $name, 置信度阈值: $confThreshold, IOU阈值: $iouThreshold")
            
            // 使用完全自动化的检测
            val results = d.detectAuto(bitmap, confThreshold, iouThreshold)
            
            Log.d("OnnxModule", "智能检测完成 - 检测到 ${results.size} 个目标")
            
            // 转换为JavaScript可识别的格式
            return results.map { r ->
                mutableMapOf(
                    "label" to r.label,
                    "score" to r.score.toDouble(),
                    "box" to listOf(r.box[0].toDouble(), r.box[1].toDouble(), r.box[2].toDouble(), r.box[3].toDouble())
                )
            }.toTypedArray()
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 获取检测器预处理配置信息
     */
    @android.webkit.JavascriptInterface
    fun getDetectorPreprocessConfig(name: String): String {
        val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
        val configInfo = d.getPreprocessConfigInfo()
        return org.json.JSONObject(configInfo).toString()
    }

    /**
     * 获取检测器初始化信息
     */
    @android.webkit.JavascriptInterface
    fun getDetectorInitInfo(name: String): String {
        val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
        val info = d.getInitializationInfo()
        return org.json.JSONObject(info).toString()
    }

    /**
     * 获取检测器输入尺寸信息
     */
    @android.webkit.JavascriptInterface
    fun getDetectorInputSizeInfo(name: String): String {
        val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
        val info = d.getInputSizeInfo()
        return org.json.JSONObject(info).toString()
    }

    /**
     * 调试检测输出
     */
    @android.webkit.JavascriptInterface
    fun debugDetection(name: String, imagePath: String): String {
        val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
        val file = File(imagePath)
        if (!file.exists()) throw IllegalArgumentException("Image not found: $imagePath")
        
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw RuntimeException("Failed to decode image: $imagePath")
        
        try {
            val debugInfo = d.debugDetection(bitmap)
            return org.json.JSONObject(debugInfo).toString()
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 设置检测参数
     */
    @android.webkit.JavascriptInterface
    fun setDetectorParams(name: String, confThreshold: Float, iouThreshold: Float) {
        // 这个参数会在detectImageAuto中传递，这里主要是为了接口兼容性
        Log.d("OnnxModule", "设置检测器参数 - 置信度: $confThreshold, IOU: $iouThreshold")
    }

    // === 分类器 - 全自动接口 ===

    /**
     * 完全自动化的分类器加载 - 推荐使用
     */
    @android.webkit.JavascriptInterface
    fun loadClassifierFullAuto(name: String, path: String) {
        val classifier = OnnxClassifier(runtime)
        classifier.loadModelAuto(path) // 使用完全自动化的加载
        
        classifiers[name] = classifier
        
        val initInfo = classifier.getInitializationInfo()
        Log.d("OnnxModule", "分类器 '$name' 全自动加载完成: $initInfo")
    }

    /**
     * 智能分类 - 完全自动化处理
     */
    @android.webkit.JavascriptInterface  
    fun classifyImageAuto(name: String, imagePath: String, topK: Int): Array<Map<String, Any>> {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        val file = File(imagePath)
        if (!file.exists()) throw IllegalArgumentException("Image not found: $imagePath")
        
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw RuntimeException("Failed to decode image: $imagePath")
        
        try {
            Log.d("OnnxModule", "开始智能分类 - 模型: $name")
            
            // 使用完全自动化的分类
            val results = c.classifyAuto(bitmap, topK)
            
            Log.d("OnnxModule", "智能分类完成 - 结果数量: ${results.size}")
            return results.map { r ->
                mapOf("label" to r.label, "score" to r.score.toDouble())
            }.toTypedArray()
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 获取分类器预处理配置信息
     */
    @android.webkit.JavascriptInterface
    fun getPreprocessConfig(name: String): String {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        val configInfo = c.getPreprocessConfigInfo()
        return org.json.JSONObject(configInfo).toString()
    }

    // === 兼容性接口 ===

    @android.webkit.JavascriptInterface
    fun loadClassifierAuto(name: String, path: String) {
        loadClassifierFullAuto(name, path)
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

    // 检测器兼容接口
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
        
        // 记录自动检测的信息
        Log.d("OnnxModule", "检测器 '$name' 加载完成，用户设置: ${width}x${height}, 自动检测: ${detector.effectiveInputSize}")
    }

    @android.webkit.JavascriptInterface
    fun loadDetector(name: String, path: String, width: Int, height: Int) {
        loadDetector(name, path, width, height, null)
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

    // 调试方法：获取详细的分类器状态
    @android.webkit.JavascriptInterface
    fun debugClassifier(name: String): String {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        
        val debugInfo = mutableMapOf<String, Any>()
        
        // 基本信息
        debugInfo["effective_input_size"] = c.effectiveInputSize
        debugInfo["effective_class_names"] = c.effectiveClassNames
        debugInfo["effective_class_count"] = c.effectiveClassNames.size
        
        // 预处理配置
        val config = c.effectivePreprocessConfig
        debugInfo["preprocess_config"] = config.toString()
        
        // 元数据信息
        val wrapper = c.getWrapperForDebug()
        debugInfo["metadata_keys"] = wrapper?.metadata?.keys ?: emptySet<String>()
        
        wrapper?.metadata?.get("imgsz")?.let { debugInfo["metadata_imgsz"] = it }
        wrapper?.metadata?.get("names")?.let { debugInfo["metadata_names"] = it }
        
        debugInfo["parsed_input_size"] = wrapper?.metadataInputSize?.toString() ?: "null"
        debugInfo["parsed_class_names"] = wrapper?.metadataClassNames?.toString() ?: "null"
        debugInfo["input_shape"] = wrapper?.inputShape?.contentToString() ?: "null"
        
        return org.json.JSONObject(debugInfo as Map<*, *>).toString()
    }

    // 智能分类 - 自动使用检测到的输入尺寸
    @android.webkit.JavascriptInterface
    fun classifyImage(name: String, imagePath: String, topK: Int): Array<Map<String, Any>> {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        val file = File(imagePath)
        if (!file.exists()) throw IllegalArgumentException("Image not found: $imagePath")
        
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw RuntimeException("Failed to decode image: $imagePath")
        
        try {
            val inputSize = c.effectiveInputSize
            Log.d("OnnxModule", "智能分类，使用检测到的输入尺寸: $inputSize")
            
            val input = if (inputSize == 32) {
                ImagePreprocessor.preprocessClassification32x32(bitmap)
            } else if (inputSize == 128) {
                ImagePreprocessor.preprocessClassification128x128(bitmap)
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

    // 检测方法（保持兼容）
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
