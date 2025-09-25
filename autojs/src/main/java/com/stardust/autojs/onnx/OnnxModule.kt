// OnnxModule.kt
package com.stardust.autojs.onnx

import com.stardust.autojs.engine.ScriptRuntime
import com.stardust.autojs.runtime.api.JSThread
import com.stardust.autojs.annotation.JavascriptInterface

import android.util.Log
import org.json.JSONObject
import java.io.File

class OnnxModule(private val runtime: ScriptRuntime) {

    private val detector = OnnxDetector()
    private val classifier = OnnxClassifier()
    private var labels: List<String> = emptyList()

    @JavascriptInterface
    fun init(options: String): Boolean {
        return try {
            val json = JSONObject(options)
            val detectModel = json.optString("detectModel", "")
            val classifyModel = json.optString("classifyModel", "")
            val labelsFile = json.optString("labels", "")

            // 读取标签文件
            if (labelsFile.isNotEmpty()) {
                val file = File(labelsFile)
                if (file.exists()) {
                    labels = file.readLines()
                    Log.i("OnnxModule", "✅ Loaded ${labels.size} labels from $labelsFile")
                } else {
                    Log.w("OnnxModule", "⚠️ Labels file not found: $labelsFile")
                }
            }

            var success = true

            // 初始化检测模型
            if (detectModel.isNotEmpty()) {
                if (!File(detectModel).exists()) {
                    Log.e("OnnxModule", "❌ Detect model not found: $detectModel")
                    success = false
                } else if (!detector.init(detectModel)) {
                    Log.e("OnnxModule", "❌ Failed to init detector")
                    success = false
                }
            }

            // 初始化分类模型
            if (classifyModel.isNotEmpty()) {
                if (!File(classifyModel).exists()) {
                    Log.e("OnnxModule", "❌ Classify model not found: $classifyModel")
                    success = false
                } else if (!classifier.init(classifyModel)) {
                    Log.e("OnnxModule", "❌ Failed to init classifier")
                    success = false
                }
            }

            success
        } catch (e: Exception) {
            Log.e("OnnxModule", "❌ Init failed", e)
            false
        }
    }

    @JavascriptInterface
    fun detect(bitmap: Any): List<Map<String, Any>> {
        return try {
            detector.detect(bitmap, labels)
        } catch (e: Exception) {
            Log.e("OnnxModule", "❌ Detect failed", e)
            emptyList()
        }
    }

    @JavascriptInterface
    fun classify(bitmap: Any): Map<String, Any>? {
        return try {
            classifier.classify(bitmap, labels)
        } catch (e: Exception) {
            Log.e("OnnxModule", "❌ Classify failed", e)
            null
        }
    }

    @JavascriptInterface
    @JSThread
    fun release() {
        detector.release()
        classifier.release()
        // 其他资源释放...
        Log.i("OnnxModule", "🗑️ ONNX models released")
    }
}
