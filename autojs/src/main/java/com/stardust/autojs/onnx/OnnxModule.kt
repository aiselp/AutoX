package com.stardust.autojs.onnx

import android.util.Log
import android.webkit.JavascriptInterface
import com.stardust.autojs.runtime.ScriptRuntime
import com.stardust.autojs.runtime.api.Threads
import com.stardust.autojs.runtime.ScriptRuntimeV2
import org.opencv.core.Mat
import java.io.File

/**
 * AutoX 的 ONNX 模块
 * 可供 JS 调用
 */
class OnnxModule(private val runtime: ScriptRuntime) {

    private var detector: OnnxDetector? = null
    private var classifier: OnnxClassifier? = null
    private var labels: List<String> = emptyList()

    /**
     * 加载分类器
     */
    @JavascriptInterface
    fun loadClassifier(modelPath: String, labelPath: String) {
        val f = File(modelPath)
        if (!f.exists()) {
            throw RuntimeException("分类模型文件不存在: $modelPath")
        }
        classifier = OnnxClassifier(modelPath)

        val lf = File(labelPath)
        if (lf.exists()) {
            labels = lf.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            labels = emptyList()
        }

        Log.d("OnnxModule", "分类模型加载成功: $modelPath, labels=${labels.size}")
    }

    /**
     * 加载检测器
     */
    @JavascriptInterface
    fun loadDetector(modelPath: String) {
        val f = File(modelPath)
        if (!f.exists()) {
            throw RuntimeException("检测模型文件不存在: $modelPath")
        }
        detector = OnnxDetector(modelPath)
        Log.d("OnnxModule", "检测模型加载成功: $modelPath")
    }

    /**
     * 分类预测
     */
    @JavascriptInterface
    fun classify(imagePath: String): String {
        val c = classifier ?: throw RuntimeException("请先调用 loadClassifier()")
        val mat = OnnxUtils.loadImage(imagePath)
        val result = c.predict(mat)

        val label = if (result.labelIndex in labels.indices) {
            labels[result.labelIndex]
        } else {
            "unknown"
        }

        return """{"label":"$label","confidence":${result.confidence}}"""
    }

    /**
     * 目标检测预测
     */
    @JavascriptInterface
    fun detect(imagePath: String): String {
        val d = detector ?: throw RuntimeException("请先调用 loadDetector()")
        val mat = OnnxUtils.loadImage(imagePath)
        val results = d.predict(mat)

        val sb = StringBuilder()
        sb.append("[")
        for ((i, r) in results.withIndex()) {
            val label = if (r.labelIndex in labels.indices) labels[r.labelIndex] else "unknown"
            sb.append("{")
            sb.append("\"label\":\"$label\",")
            sb.append("\"confidence\":${r.confidence},")
            sb.append("\"x\":${r.x},\"y\":${r.y},\"w\":${r.w},\"h\":${r.h}")
            sb.append("}")
            if (i != results.lastIndex) sb.append(",")
        }
        sb.append("]")
        return sb.toString()
    }

    /**
     * 异步执行任务
     */
    @JavascriptInterface
    fun runAsync(code: String) {
        Threads.start {
            try {
                runtime.execute(code)
            } catch (e: Exception) {
                Log.e("OnnxModule", "runAsync error", e)
            }
        }
    }
}
