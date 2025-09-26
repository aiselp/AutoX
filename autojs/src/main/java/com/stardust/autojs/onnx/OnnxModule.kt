package com.stardust.autojs.onnx

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONObject
import java.io.File

// ⚠️ 如果 AutoX 没有 ScriptRuntime/JSThread/JavaScriptInterface，请去掉相关 import 和注解
// import com.stardust.autojs.engine.ScriptRuntime
// import com.stardust.autojs.engine.JSThread
// import android.webkit.JavascriptInterface

class OnnxModule(private val context: Context) {

    private val detector = OnnxDetector()
    private val classifier = OnnxClassifier()
    private var labels: List<String> = emptyList()

    companion object {
        private const val TAG = "OnnxModule"
    }

    fun init(options: String): Boolean {
        return try {
            val json = JSONObject(options)
            val detectModelPath = json.optString("detectModel", "").trim()
            val classifyModelPath = json.optString("classifyModel", "").trim()
            val labelsPath = json.optString("labels", "").trim()

            Log.d(TAG, "🔧 Initializing ONNX models...")

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

            if (detectModelPath.isNotEmpty()) {
                val modelFile = File(detectModelPath)
                if (!modelFile.exists() || !modelFile.canRead() || !detector.init(detectModelPath)) {
                    Log.e(TAG, "❌ Failed to initialize detector: $detectModelPath")
                    success = false
                } else {
                    Log.i(TAG, "✅ Detector initialized: $detectModelPath")
                }
            }

            if (classifyModelPath.isNotEmpty()) {
                val modelFile = File(classifyModelPath)
                if (!modelFile.exists() || !modelFile.canRead() || !classifier.init(classifyModelPath)) {
                    Log.e(TAG, "❌ Failed to initialize classifier: $classifyModelPath")
                    success = false
                } else {
                    Log.i(TAG, "✅ Classifier initialized: $classifyModelPath")
                }
            }

            if (success) Log.i(TAG, "🎉 ONNX module initialized successfully")
            else Log.e(TAG, "❌ ONNX module initialization failed")

            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Init failed due to exception", e)
            false
        }
    }

    fun detect(bitmap: Bitmap): List<Map<String, Any>> {
        return try {
            val detections = detector.detect(bitmap, labels)
            val result = mutableListOf<Map<String, Any>>()
            for (d in detections) {
                result.add(
                    mapOf(
                        "classId" to d.classId,
                        "className" to d.className,
                        "confidence" to d.confidence,
                        "left" to d.left,
                        "top" to d.top,
                        "right" to d.right,
                        "bottom" to d.bottom
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Detect failed", e)
            emptyList()
        }
    }

    fun classify(bitmap: Bitmap): Map<String, Any>? {
        return try {
            val result = classifier.classify(bitmap, labels) ?: return null
            mapOf(
                "classId" to result.classId,
                "className" to result.className,
                "confidence" to result.confidence
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Classify failed", e)
            null
        }
    }

    fun release() {
        try {
            detector.release()
            classifier.release()
            Log.i(TAG, "🗑️ ONNX models released")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during release", e)
        }
    }

    fun getLabels(): List<String> {
        return labels
    }
}
