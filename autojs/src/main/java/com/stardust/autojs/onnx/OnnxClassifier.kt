package com.stardust.autojs.onnx

import ai.onnxruntime.*
import org.opencv.core.Mat

/**
 * 简单的 ONNX 分类器
 */
class OnnxClassifier(private val wrapper: OnnxWrapper) {

    fun classify(image: Mat): List<Pair<String, Float>> {
        val input = OnnxUtils.matToFloatTensor(image, wrapper.inputShape)
        val results = wrapper.run(input)

        // 假设只有一个输出 (softmax 概率)
        val scores = results[wrapper.outputName] as FloatArray
        val classes = wrapper.labels

        return classes.zip(scores.toList())
    }
}
