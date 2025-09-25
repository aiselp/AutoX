// package: com.autox.onnx
import ai.onnxruntime.*
import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer

class OnnxDetector {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var inputName: String = ""
    private val imageSize = 640

    fun init(modelPath: String): Boolean {
        try {
            val sessionOptions = OrtSession.SessionOptions()
            session = env.createSession(modelPath, sessionOptions)
            inputName = session!!.inputNames.first()
            return true
        } catch (e: Exception) {
            Log.e("OnnxDetector", "Failed to load model: $modelPath", e)
            return false
        }
    }

    data class Detection(
        val classId: Int,
        val className: String,
        val confidence: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    fun detect(bitmap: Bitmap, labels: List<String>): List<Detection> {
        val session = this.session ?: throw IllegalStateException("Model not initialized")

        // 1. 预处理：缩放 + 归一化 + HWC → CHW
        val resized = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)
        val tensorBuffer = FloatBuffer.allocate(imageSize * imageSize * 3)
        for (y in 0 until imageSize) {
            for (x in 0 until imageSize) {
                val pixel = resized.getPixel(x, y)
                // 归一化：/255.0，BGR → RGB
                tensorBuffer.put((pixel and 0xFF) / 255f)           // R
                tensorBuffer.put(((pixel shr 8) and 0xFF) / 255f)   // G
                tensorBuffer.put(((pixel shr 16) and 0xFF) / 255f)  // B
            }
        }
        tensorBuffer.rewind()

        val inputTensor = OnnxTensor.createTensor(env, tensorBuffer, longArrayOf(1, 3, imageSize, imageSize))

        // 2. 推理
        val results = session.run(mapOf(inputName to inputTensor))
        val output = results.values.first().value as FloatArray

        // 3. 后处理（简化版，YOLOv8 输出 shape: [1, 8400, 85]）
        val detections = mutableListOf<Detection>()
        val numBoxes = 8400
        val numClasses = 80
        for (i in 0 until numBoxes) {
            val offset = i * (4 + 1 + numClasses)
            val boxConfidence = output[offset + 4]
            if (boxConfidence < 0.25) continue

            val classScores = output.sliceArray(offset + 5 until offset + 5 + numClasses)
            val maxIndex = classScores.indices.maxByOrNull { classScores[it] } ?: continue
            val classConfidence = classScores[maxIndex]
            val confidence = boxConfidence * classConfidence
            if (confidence < 0.25) continue

            val x = output[offset]
            val y = output[offset + 1]
            val w = output[offset + 2]
            val h = output[offset + 3]

            val left = (x - w / 2).coerceIn(0f, 1f) * bitmap.width
            val top = (y - h / 2).coerceIn(0f, 1f) * bitmap.height
            val right = (x + w / 2).coerceIn(0f, 1f) * bitmap.width
            val bottom = (y + h / 2).coerceIn(0f, 1f) * bitmap.height

            detections.add(
                Detection(
                    classId = maxIndex,
                    className = labels.getOrNull(maxIndex) ?: "unknown",
                    confidence = confidence,
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom
                )
            )
        }

        inputTensor.close()
        results.values.forEach { it.close() }
        return detections
    }

    fun release() {
        session?.close()
        session = null
    }
}
