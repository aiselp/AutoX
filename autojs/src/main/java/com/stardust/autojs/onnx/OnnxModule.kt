// autojs/src/main/java/com/stardust/autojs/onnx/OnnxModule.kt
package com.stardust.autojs.onnx

import android.graphics.BitmapFactory
import android.util.Log
import com.stardust.autojs.runtime.ScriptRuntime
import java.io.File

class OnnxModule(private val runtime: ScriptRuntime) {

    private val classifiers = mutableMapOf<String, OnnxClassifier>()
    private val detectors = mutableMapOf<String, OnnxDetector>()

    // === 分类器 ===

    @android.webkit.JavascriptInterface
    fun loadClassifier(name: String, path: String, classNamesJson: String? = null) {
        val classifier = OnnxClassifier(runtime)
        classifier.loadModel(path)
        if (classNamesJson != null) {
            try {
                val arr = org.json.JSONArray(classNamesJson)
                classifier.setClassNames((0 until arr.length()).map { arr.getString(it) })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid classNames JSON", e)
            }
        }
        classifiers[name] = classifier
    }

    @android.webkit.JavascriptInterface
    fun loadClassifier(name: String, path: String) {
        loadClassifier(name, path, null)
    }

    @android.webkit.JavascriptInterface
    fun classify(name: String, input: FloatArray): FloatArray {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        return c.predict(input)
    }

    @android.webkit.JavascriptInterface
    fun classifyWithLabel(name: String, input: FloatArray, topK: Int): Array<Map<String, Any>> {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        val results = c.classify(input, topK)
        return results.map { r ->
            mapOf("label" to r.label, "score" to r.score.toDouble())
        }.toTypedArray()
    }

    @android.webkit.JavascriptInterface
    fun classifyImage(name: String, imagePath: String, inputSize: Int, topK: Int): Array<Map<String, Any>> {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        val file = File(imagePath)
        if (!file.exists()) throw IllegalArgumentException("Image not found: $imagePath")
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw RuntimeException("Failed to decode image: $imagePath")
        try {
            val input = ImagePreprocessor.preprocessClassification(bitmap, inputSize)
            val results = c.classify(input, topK)
            return results.map { r ->
                mapOf("label" to r.label, "score" to r.score.toDouble())
            }.toTypedArray()
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
                "box" to r.box.map { it.toDouble() }.toDoubleArray()  // 修复这一行
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
}
