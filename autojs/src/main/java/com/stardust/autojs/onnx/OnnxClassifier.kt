// OnnxClassifier.kt
import ai.onnxruntime.*
import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer

class OnnxClassifier {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var inputName: String = ""
    private val imageSize = 224

    fun init(modelPath: String): Boolean {
        try {
            val sessionOptions = OrtSession.SessionOptions()
            session = env.createSession(modelPath, sessionOptions)
            inputName = session!!.inputNames.first()
            Log.i("OnnxClassifier", "Model loaded: $modelPath, Input: $inputName")
            return true
        } catch (e: Exception) {
            Log.e("OnnxClassifier", "Failed to load model: $modelPath", e)
            return false
        }
    }

    data class Classification(
        val classId: Int,
        val className: String,
        val confidence: Float
    )

    fun classify(bitmap: Bitmap, labels: List<String>): Classification? {
        val session = this.session ?: throw IllegalStateException("Model not initialized")

        // 1. 预处理：缩放 + 归一化 + RGB → BGR
        val resized = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)
        val tensorBuffer = FloatBuffer.allocate(imageSize * imageSize * 3)
        for (y in 0 until imageSize) {
            for (x in 0 until imageSize) {
                val pixel = resized.getPixel(x, y)
                tensorBuffer.put(((pixel shr 16) and 0xFF) / 255f)  // B
                tensorBuffer.put(((pixel shr 8) and 0xFF) / 255f)   // G
                tensorBuffer.put((pixel and 0xFF) / 255f)           // R
            }
        }
        tensorBuffer.rewind()

        val inputTensor = OnnxTensor.createTensor(env, tensorBuffer, longArrayOf(1, 3, imageSize, imageSize))

        // 2. 推理
        val results = session.run(mapOf(inputName to inputTensor))
        val output = results.values.first().value as FloatArray

        // 3. Softmax + 找最大
        val probabilities = softmax(output)
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
        val confidence = probabilities[maxIndex]

        val result = Classification(
            classId = maxIndex,
            className = labels.getOrNull(maxIndex) ?: "unknown",
            confidence = confidence
        )

        // 释放资源
        inputTensor.close()
        results.values.forEach { it.close() }

        return result
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp(it - max).toFloat() }.toFloatArray()
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    private fun exp(x: Float): Float = kotlin.math.exp(x.toDouble()).toFloat()

    fun release() {
        session?.close()
        session = null
    }
}
