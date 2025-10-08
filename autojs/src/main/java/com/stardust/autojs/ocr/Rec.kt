//autojs/src/main/java/com/stardust/autojs/ocr/Rec.kt
package com.stardust.autojs.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.content.res.AssetManager
import com.stardust.autojs.ocr.RecResult
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc.resize
import java.util.*
import org.opencv.core.Core
import org.opencv.core.Scalar
import org.opencv.core.Rect

class Rec(private val ortEnv: OrtEnvironment, assetManager: AssetManager, modelName: String, keysName: String) {

    private val session by lazy {
        val model = assetManager.open(modelName, AssetManager.ACCESS_UNKNOWN).readBytes()
        ortEnv.createSession(model)
    }

    private val keys by lazy {
        val reader = assetManager.open(keysName, AssetManager.ACCESS_UNKNOWN).bufferedReader()
        reader.lineSequence().toMutableList().apply {
            add(0, "#")
            add(" ")
        }.toList()
    }

    private fun scoreToTextLine(outputData: List<FloatArray>): RecResult {
        val sb = StringBuilder()
        val scores: MutableList<Float> = mutableListOf()
        var lastIndex = 0
        outputData.forEach {
            val max = it.withIndex().maxBy { it.value }
            if (max.index in 1 until keys.size && max.index != lastIndex) {
                sb.append(keys[max.index])
                scores.add(max.value)
            }
            lastIndex = max.index
        }
        return RecResult(sb.toString(), scores)
    }

    fun getRecResult(src: Mat): RecResult {
        // 使用官方推荐的图像尺寸 3x48x320
        val targetHeight = 48.0
        val targetWidth = 320.0
        
        val scale = targetHeight / src.rows()
        val dstWidth = (src.cols() * scale).toInt().toDouble()
        
        val srcResize = Mat()
        resize(src, srcResize, Size(dstWidth, targetHeight))
        
        // 如果宽度超过320，进行裁剪；如果不足，进行填充
        val finalWidth = min(dstWidth.toInt(), targetWidth.toInt())
        val finalMat = Mat(targetHeight.toInt(), targetWidth.toInt(), srcResize.type(), Scalar(0.0, 0.0, 0.0))
        
        if (dstWidth <= targetWidth) {
            // 宽度不足，居中放置
            val xOffset = ((targetWidth - dstWidth) / 2).toInt()
            val roi = Rect(xOffset, 0, finalWidth, targetHeight.toInt())
            srcResize.copyTo(finalMat.submat(roi))
        } else {
            // 宽度超过，裁剪中间部分
            val xOffset = ((dstWidth - targetWidth) / 2).toInt()
            val roi = Rect(xOffset, 0, finalWidth, targetHeight.toInt())
            srcResize.submat(roi).copyTo(finalMat)
        }
        
        // 使用官方预处理参数
        val inputTensorValues = substractMeanNormalize(finalMat, meanValues, normValues)
        val inputShape = longArrayOf(1, finalMat.channels().toLong(), finalMat.rows().toLong(), finalMat.cols().toLong())
        val inputName = session.inputNames.iterator().next()
        
        OnnxTensor.createTensor(ortEnv, inputTensorValues, inputShape).use { inputTensor ->
            session.run(Collections.singletonMap(inputName, inputTensor)).use { output ->
                val onnxValue = output.first().value
                val values = onnxValue.value as Array<Array<FloatArray>>
                val outputData = values.flatMap { a -> a.flatMap { b -> listOf(b) } }
                return scoreToTextLine(outputData)
            }
        }
    }

    fun getRecResults(mats: List<Mat>): List<RecResult> = mats.map { getRecResult(it) }

    companion object {
        // 使用官方预处理参数
        // 识别模型通常使用不同的归一化参数
        private val meanValues = floatArrayOf(0.5F * 255F, 0.5F * 255F, 0.5F * 255F)
        private val normValues = floatArrayOf(1.0F / 0.5F / 255.0F, 1.0F / 0.5F / 255.0F, 1.0F / 0.5F / 255.0F)
    }
}
