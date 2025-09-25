import ai.onnxruntime.*
import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer

class OnnxClassifier {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var inputName: String = ""

    private var inputWidth = 224
    private var inputHeight = 224

    fun init(modelPath: String): Boolean {
        return try {
            val sessionOptions = OrtSession.SessionOptions()
            session = env.createSession(modelPath, sessionOptions)
            inputName = session!!.inputNames.first()

            val shape = session!!.inputMetadata[inputName]!!.shape
            if (shape.size == 4) {
                inputHeight = shape[2].toInt()
                inputWidth = shape[3].toInt()
                Log.i("OnnxClassifier", "✅ Model input size: ${inputWidth}x${inputHeight}")
            } else {
                Log.e("OnnxClassifier", "❌ Unexpected input shape: ${shape.contentToString()}")
                return false
            }
            true
        } catch (e: Exception) {
            Log.e("OnnxClassifier", "❌ Failed to load model", e)
            false
        }
    }

    data class Classification(
        val classId: Int,
        val className: String,
        val confidence: Float
    )

    fun classify(bitmap: Bitmap, labels: List<String>): Classification? {
        val session = this.session ?: throw IllegalStateException("Model not initialized")

        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val tensorBuffer = FloatBuffer.allocate(inputWidth * inputHeight * 3)

        for (y in 0 until inputHeight) {
            for (x in 0 until inputWidth) {
                val pixel = resized.getPixel(x, y)
                tensorBuffer.put(((pixel shr 16) and 0xFF) / 255f)
                tensorBuffer.put(((pixel shr 8) and 0xFF) / 255f)
                tensorBuffer.put((pixel and 0xFF) / 255f)
            }
        }
        tensorBuffer.rewind()

        val inputTensor = OnnxTensor.createTensor(
            env,
            tensorBuffer,
            longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        )

        val results = session.run(mapOf(inputName to inputTensor))
        val output = results.values.first().value as FloatArray

        Log.d("OnnxClassifier", "📊 Output shape: ${results.values.first().shape.contentToString()}")

        val probabilities = softmax(output)
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
        val confidence = probabilities[maxIndex]
        val className = labels.getOrNull(maxIndex) ?: "unknown"

        val result = Classification(maxIndex, className, confidence)

        // ✅ 修复资源释放
        inputTensor.close()
        for (value in results.values) {
            value.close()
        }

        return result
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = logits.map { kotlin.math.exp(it - max).toFloat() }
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    fun release() {
        session?.close()
        session = null
    }
}
