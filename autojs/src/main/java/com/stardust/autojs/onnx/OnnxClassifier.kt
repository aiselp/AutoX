// OnnxClassifier.kt
class OnnxClassifier {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var inputName: String = ""
    
    // 动态尺寸
    private var inputWidth = 224
    private var inputHeight = 224

    fun init(modelPath: String): Boolean {
        try {
            val sessionOptions = OrtSession.SessionOptions()
            session = env.createSession(modelPath, sessionOptions)
            inputName = session!!.inputNames.first()

            val inputMetadata = session!!.inputMetadata
            val shape = inputMetadata[inputName]!!.shape

            if (shape.size == 4) {
                inputHeight = shape[2].toInt()
                inputWidth = shape[3].toInt()
                Log.i("OnnxClassifier", "Model input size: ${inputWidth}x${inputHeight}")
            } else {
                Log.e("OnnxClassifier", "Unexpected input shape: ${shape.contentToString()}")
                return false
            }

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

        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val tensorBuffer = FloatBuffer.allocate(inputWidth * inputHeight * 3)

        for (y in 0 until inputHeight) {
            for (x in 0 until inputWidth) {
                val pixel = resized.getPixel(x, y)
                tensorBuffer.put(((pixel shr 16) and 0xFF) / 255f)  // B
                tensorBuffer.put(((pixel shr 8) and 0xFF) / 255f)   // G
                tensorBuffer.put((pixel and 0xFF) / 255f)           // R
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

        val probabilities = softmax(output)
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
        val confidence = probabilities[maxIndex]

        val result = Classification(
            classId = maxIndex,
            className = labels.getOrNull(maxIndex) ?: "unknown",
            confidence = confidence
        )

        inputTensor.close()
        results.values.forEach { it.close() }

        return result
    }

    // softmax, exp 等保持不变
    // ...

    fun release() {
        session?.close()
        session = null
    }
}
