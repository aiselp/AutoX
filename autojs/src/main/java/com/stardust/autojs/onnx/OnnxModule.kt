package com.stardust.autojs.onnx

import android.webkit.JavascriptInterface
import android.util.Log
import com.stardust.autojs.runtime.ScriptRuntime
import com.stardust.autojs.runtime.api.Threads
import org.opencv.core.Mat

/**
 * JS 可调用的 ONNX 模块
 */
class OnnxModule(private val runtime: ScriptRuntime) {

    private var detector: OnnxDetector? = null
    private var classifier: OnnxClassifier? = null

    @JavascriptInterface
    fun loadDetector(modelPath: String, labels: Array<String>) {
        detector = OnnxDetector(OnnxWrapper(modelPath, labels))
    }

    @JavascriptInterface
    fun loadClassifier(modelPath: String, labels: Array<String>) {
        classifier = OnnxClassifier(OnnxWrapper(modelPath, labels))
    }

    @JavascriptInterface
    fun detect(imgPath: String): String {
        val mat = OnnxUtils.readImage(imgPath)
        val dets = detector?.detect(mat) ?: return "[]"
        return dets.joinToString(
            prefix = "[", postfix = "]"
        ) { """{"label":"${it.label}","score":${it.confidence},"box":[${it.box.joinToString()}]}""" }
    }

    @JavascriptInterface
    fun classify(imgPath: String): String {
        val mat = OnnxUtils.readImage(imgPath)
        val res = classifier?.classify(mat) ?: return "[]"
        return res.joinToString(
            prefix = "[", postfix = "]"
        ) { """{"label":"${it.first}","score":${it.second}}""" }
    }

    @JavascriptInterface
    fun runAsync(code: String) {
        Threads(runtime).start(Runnable {
            try {
                runtime.bridges.evaluate(code) // 使用 ScriptRuntime 执行 JS
            } catch (e: Exception) {
                Log.e("OnnxModule", "runAsync error", e)
            }
        })
    }
}
