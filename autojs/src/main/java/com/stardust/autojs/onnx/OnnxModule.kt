package com.stardust.autojs.onnx

import android.webkit.JavascriptInterface
import com.stardust.autojs.runtime.ScriptRuntime
import java.nio.FloatBuffer

class OnnxModule(private val runtime: ScriptRuntime) {

    private val classifiers = mutableMapOf<String, OnnxClassifier>()
    private val detectors = mutableMapOf<String, OnnxDetector>()

    @JavascriptInterface
    fun loadClassifier(name: String, path: String) {
        val c = OnnxClassifier(runtime)
        c.loadModel(path)
        classifiers[name] = c
    }

    @JavascriptInterface
    fun classify(name: String, input: FloatArray): FloatArray {
        val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
        return c.predict(input)
    }

    @JavascriptInterface
    fun loadDetector(name: String, path: String) {
        val d = OnnxDetector(runtime)
        d.loadModel(path)
        detectors[name] = d
    }

    @JavascriptInterface
    fun detect(name: String, input: FloatArray): List<OnnxDetector.DetectionResult> {
        val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
        return d.detect(input)
    }
}
