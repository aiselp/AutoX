package com.stardust.autojs.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer

/**
 * OpenCV + ONNX 辅助工具
 */
object OnnxUtils {

    fun readImage(path: String): Mat {
        val mat = Imgcodecs.imread(path)
        if (mat.empty()) {
            throw RuntimeException("无法读取图片: $path")
        }
        return mat
    }

    fun matToFloatTensor(mat: Mat, shape: LongArray): OnnxTensor {
        val h = shape[2].toInt()
        val w = shape[3].toInt()
        val c = shape[1].toInt()

        val resized = Mat()
        Imgproc.resize(mat, resized, org.opencv.core.Size(w.toDouble(), h.toDouble()))

        val data = FloatArray(c * h * w)
        val pixels = IntArray(w * h * c)
        // OpenCV 默认 BGR，需要转 RGB
        var idx = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val b = resized.get(y, x)[0].toFloat()
                val g = resized.get(y, x)[1].toFloat()
                val r = resized.get(y, x)[2].toFloat()
                data[idx++] = r / 255f
                data[idx++] = g / 255f
                data[idx++] = b / 255f
            }
        }

        val fb = FloatBuffer.wrap(data)
        val env = OrtEnvironment.getEnvironment()
        return OnnxTensor.createTensor(env, fb, shape)
    }
}
