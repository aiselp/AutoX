import ai.onnxruntime.*
import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer
import kotlin.math.exp
import java.util.Collections

class OnnxClassifier {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var inputName: String = ""

    private var inputWidth = 224
    private var inputHeight = 224

    /**
     * 初始化 ONNX 模型
     * @param modelPath 模型文件路径
     * @return 是否加载成功
     */
    fun init(modelPath: String): Boolean {
        return try {
            val sessionOptions = OrtSession.SessionOptions()
            session = env.createSession(modelPath, sessionOptions)

            val inputInfo = session!!.inputInfo
            if (inputInfo.isEmpty()) {
                Log.e("OnnxClassifier", "❌ Model has no input")
                return false
            }

            inputName = inputInfo.keys.first()

            val shape = (inputInfo[inputName]!!.info as TensorInfo).shape
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

    /**
     * 分类结果数据类
     */
    data class Classification(
        val classId: Int,
        val className: String,
        val confidence: Float
    )

    /**
     * 执行图像分类
     */
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

        try {
            // ✅ 修复：使用正确的API执行推理
            val results = session.run(Collections.singletonMap(inputName, inputTensor))
            try {
                val outputTensor = results.get(0)
                val output = outputTensor.value as FloatArray

                Log.d("OnnxClassifier", "📊 Output shape: ${outputTensor.info.dims.contentToString()}")

                val probabilities = softmax(output)
                val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
                val confidence = probabilities[maxIndex]
                val className = labels.getOrNull(maxIndex) ?: "unknown"

                return Classification(maxIndex, className, confidence)
            } finally {
                results.close()
            }
        } finally {
            inputTensor.close()
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp(it - max).toFloat() }
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    fun release() {
        session?.close()
        session = null
    }
}
