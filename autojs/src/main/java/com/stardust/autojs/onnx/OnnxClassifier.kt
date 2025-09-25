// package: com.autox.onnx
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

        // 1. 预处理
        val resized = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)
        val tensorBuffer = FloatBuffer.allocate(imageSize * imageSize * 3)
        for (y in 0 until imageSize) {
            for (x in 0 until imageSize) {
                val pixel = resized.getPixel(x, y)
                tensorBuffer.put((pixel and 0xFF) / 255f)
                tensorBuffer.put(((pixel shr 8) and 0xFF) / 255f)
                tensorBuffer.put(((pixel shr 16) and 0xFF) / 255f)
            }
        }
        tensorBuffer.rewind()

        val inputTensor = OnnxTensor.createTensor(env, tensorBuffer, longArrayOf(1, 3, imageSize, imageSize))

        // 2. 推理
        val results = session.run(mapOf(inputName to inputTensor))
        val output = results.values.first().value as FloatArray

        // 3. 找最大概率
        val maxIndex = output.indices.maxByOrNull { output[it] } ?: return null
        val confidence = output[maxIndex]

        val result = Classification(
            classId = maxIndex,
            className = labels.getOrNull(maxIndex) ?: "unknown",
            confidence = confidence
        )

        inputTensor.close()
        results.values.forEach { it.close() }
        return result
    }

    fun release() {
        session?.close()
        session = null
    }
}
