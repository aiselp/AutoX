//autojs/src/main/java/com/stardust/autojs/ocr/Cls.kt
package com.stardust.autojs.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.content.res.AssetManager
import com.stardust.autojs.ocr.ClsResult
import org.opencv.core.Mat
import java.util.*

class Cls(private val ortEnv: OrtEnvironment, assetManager: AssetManager, modelName: String) {

    private val session by lazy {
        val model = assetManager.open(modelName, AssetManager.ACCESS_UNKNOWN).readBytes()
        ortEnv.createSession(model)
    }

    private fun getClsResult(src: Mat): ClsResult {
        val srcResize = adjustToDst(src, DST_WIDTH, DST_HEIGHT)
        val inputTensorValues = substractMeanNormalize(srcResize, meanValues, normValues)
        val inputShape = longArrayOf(1, srcResize.channels().toLong(), srcResize.rows().toLong(), srcResize.cols().toLong())
        val inputName = session.inputNames.iterator().next()
        OnnxTensor.createTensor(ortEnv, inputTensorValues, inputShape).use { inputTensor ->
            session.run(Collections.singletonMap(inputName, inputTensor)).use { output ->
                val onnxValue = output.first().value
                /*val tensorInfo = onnxValue.info as TensorInfo
                val type = onnxValue.type
                Logger.i("info=${tensorInfo},type=$type")*/
                val values = onnxValue.value as Array<FloatArray>
                val outputData = values.flatMap { a ->
                    a.flatMap { listOf(it) }
                }
                val max = outputData.withIndex().maxBy { it.value }
                return ClsResult(max.index, max.value)
            }
        }
    }

    fun getClsResults(partMats: List<Mat>): List<ClsResult> = partMats.map {
        getClsResult(it)
    }

    companion object {
        private const val DST_WIDTH = 192.0
        private const val DST_HEIGHT = 48.0
        private val meanValues = floatArrayOf(127.5F, 127.5F, 127.5F)
        private val normValues = floatArrayOf(1.0F / 127.5F, 1.0F / 127.5F, 1.0F / 127.5F)
    }

}
