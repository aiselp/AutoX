// 文件: OnnxModule.kt
package com.stardust.autojs.onnx

import com.stardust.autojs.engine.ScriptRuntime
import com.stardust.autojs.runtime.api.JSThread
import com.stardust.autojs.annotation.JavascriptInterface
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONObject
import java.io.File

class OnnxModule(private val runtime: ScriptRuntime) {

    private val detector = OnnxDetector()
    private val classifier = OnnxClassifier()
    private var labels: List<String> = emptyList()

    companion object {
        private const val TAG = "OnnxModule"
    }

    /**
     * 初始化 ONNX 模型
     * @param options JSON 字符串，格式：
     * {
     *   "detectModel": "/path/to/yolov8n.onnx",
     *   "classifyModel": "/path/to/resnet18.onnx",
     *   "labels": "/path/to/labels.txt"
     * }
     * @return 是否初始化成功
     */
    @JavascriptInterface
    fun init(options: String): Boolean {
        return try {
            val json = JSONObject(options)
            val detectModelPath = json.optString("detectModel", "").trim()
            val classifyModelPath = json.optString("classifyModel", "").trim()
            val labelsPath = json.optString("labels", "").trim()

            Log.d(TAG, "🔧 Initializing ONNX models...")

            // 加载标签
            if (labelsPath.isNotEmpty()) {
                val file = File(labelsPath)
                if (file.exists() && file.canRead()) {
                    labels = file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
                    Log.i(TAG, "✅ Loaded ${labels.size} labels from $labelsPath")
                } else {
                    Log.w(TAG, "⚠️ Labels file not found or unreadable: $labelsPath")
                    labels = emptyList()
                }
            }

            var success = true

            // 初始化检测模型
            if (detectModelPath.isNotEmpty()) {
                val modelFile = File(detectModelPath)
                if (!modelFile.exists()) {
                    Log.e(TAG, "❌ Detect model not found: $detectModelPath")
                    success = false
                } else if (!modelFile.canRead()) {
                    Log.e(TAG, "❌ Cannot read detect model: $detectModelPath")
                    success = false
                } else if (!detector.init(detectModelPath)) {
                    Log.e(TAG, "❌ Failed to initialize detector with model: $detectModelPath")
                    success = false
                } else {
                    Log.i(TAG, "✅ Detector initialized: $detectModelPath")
                }
            }

            // 初始化分类模型
            if (classifyModelPath.isNotEmpty()) {
                val modelFile = File(classifyModelPath)
                if (!modelFile.exists()) {
                    Log.e(TAG, "❌ Classify model not found: $classifyModelPath")
                    success = false
                } else if (!modelFile.canRead()) {
                    Log.e(TAG, "❌ Cannot read classify model: $classifyModelPath")
                    success = false
                } else if (!classifier.init(classifyModelPath)) {
                    Log.e(TAG, "❌ Failed to initialize classifier with model: $classifyModelPath")
                    success = false
                } else {
                    Log.i(TAG, "✅ Classifier initialized: $classifyModelPath")
                }
            }

            if (success) {
                Log.i(TAG, "🎉 ONNX module initialized successfully")
            } else {
                Log.e(TAG, "❌ ONNX module initialization failed")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Init failed due to exception", e)
            false
        }
    }

    /**
     * 执行目标检测
     * @param bitmap Android Bitmap 对象
     * @return List<Map<String, Any>> 检测结果列表
     * [
     *   { "classId": 0, "className": "person", "confidence": 0.95, "left": 100, "top": 50, "right": 200, "bottom": 300 },
     *   ...
     * ]
     */
    @JavascriptInterface
    fun detect(bitmap: Any): List<Map<String, Any>> {
        return try {
            val inputBitmap = when (bitmap) {
                is Bitmap -> bitmap
                else -> {
                    Log.e(TAG, "❌ Invalid bitmap type: ${bitmap.javaClass}")
                    return emptyList()
                }
            }

            if (labels.isEmpty()) {
                Log.w(TAG, "⚠️ No labels loaded, using 'class_X' as fallback")
            }

            val detections = detector.detect(inputBitmap, labels)
            detections.map { detection ->
                mapOf(
                    "classId" to detection.classId,
                    "className" to detection.className,
                    "confidence" to detection.confidence,
                    "left" to detection.left,
                    "top" to detection.top,
                    "right" to detection.right,
                    "bottom" to detection.bottom
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Detect failed", e)
            emptyList()
        }
    }

    /**
     * 执行图像分类
     * @param bitmap Android Bitmap 对象
     * @return Map<String, Any>? 分类结果
     * { "classId": 207, "className": "tiger", "confidence": 0.99 }
     */
    @JavascriptInterface
    fun classify(bitmap: Any): Map<String, Any>? {
        return try {
            val inputBitmap = when (bitmap) {
                is Bitmap -> bitmap
                else -> {
                    Log.e(TAG, "❌ Invalid bitmap type: ${bitmap.javaClass}")
                    return null
                }
            }

            if (labels.isEmpty()) {
                Log.w(TAG, "⚠️ No labels loaded, using 'class_X' as fallback")
            }

            val result = classifier.classify(inputBitmap, labels) ?: return null

            mapOf(
                "classId" to result["classId"]!!,
                "className" to result["className"]!!,
                "confidence" to result["confidence"]!!
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Classify failed", e)
            null
        }
    }

    /**
     * 释放所有模型资源
     */
    @JavascriptInterface
    @JSThread
    fun release() {
        try {
            detector.release()
            classifier.release()
            Log.i(TAG, "🗑️ ONNX models released")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during release", e)
        }
    }

    /**
     * 获取当前标签列表
     */
    @JavascriptInterface
    fun getLabels(): List<String> {
        return labels
    }
}
