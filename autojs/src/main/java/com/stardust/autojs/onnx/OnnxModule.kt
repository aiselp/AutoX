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
        val results = d.detect(input)
        return results.map { r ->
            mapOf(
                "label" to r.label,
                "score" to r.score.toDouble(),
                "box" to listOf(r.box[0].toDouble(), r.box[1].toDouble(), r.box[2].toDouble(), r.box[3].toDouble())
            )
        }.toTypedArray()
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
    fun getClassifierOutputInfo(name: String, imagePath: String, inputSize: Int): String {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        val file = File(imagePath)
        if (!file.exists()) throw IllegalArgumentException("Image not found: $imagePath")
        
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw RuntimeException("Failed to decode image: $imagePath")
        
        try {
            val input = ImagePreprocessor.preprocessClassification(bitmap, inputSize)
            val outputInfo = c.getOutputInfo(input)
            return org.json.JSONObject(outputInfo).toString()
        } finally {
            bitmap.recycle()
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
