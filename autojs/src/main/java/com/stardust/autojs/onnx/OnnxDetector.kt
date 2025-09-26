import ai.onnxruntime.*
import android.graphics.Bitmap
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import java.nio.FloatBuffer
import java.util.Collections



class OnnxDetector {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var inputName: String = ""

    private var inputWidth = 640
    private var inputHeight = 640
    private val numClasses = 80
    private var numBoxes = 8400

    fun init(modelPath: String): Boolean {
        return try {
            val sessionOptions = OrtSession.SessionOptions()
            session = env.createSession(modelPath, sessionOptions)

            val inputInfo = session!!.inputInfo
            if (inputInfo.isEmpty()) {
                Log.e("OnnxDetector", "❌ Model has no input")
                return false
            }

            inputName = inputInfo.keys.first()

            val shape = (inputInfo[inputName]!!.info as TensorInfo).shape
            if (shape.size == 4) {
                inputHeight = shape[2].toInt()
                inputWidth = shape[3].toInt()
                Log.i("OnnxDetector", "✅ Model input size: ${inputWidth}x${inputHeight}")
            } else {
                Log.e("OnnxDetector", "❌ Unexpected input shape: ${shape.contentToString()}")
                return false
            }
            true
        } catch (e: Exception) {
            Log.e("OnnxDetector", "❌ Failed to load model: $modelPath", e)
            false
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
            val results = session.run(Collections.singletonMap(inputName, inputTensor))
            try {
                val outputTensor = results.get(0)
                val output = outputTensor.value as FloatArray

                Log.d("OnnxDetector", "📊 Output shape: ${outputTensor.info.dims.contentToString()}")

                val detections = decodeYOLOv8(output, labels, bitmap.width.toFloat(), bitmap.height.toFloat())
                return nonMaxSuppression(detections, 0.45f)
            } finally {
                results.close()
            }
        } finally {
            inputTensor.close()
        }
    }

    private fun decodeYOLOv8(
        output: FloatArray,
        labels: List<String>,
        originalWidth: Float,
        originalHeight: Float
    ): List<Detection> {
        val numOutputChannels = numClasses + 4
        val numOutputElements = output.size

        val inferredNumBoxes = numOutputElements / numOutputChannels
        if (inferredNumBoxes != numBoxes) {
            Log.w("OnnxDetector", "⚠️ NumBoxes mismatch: expected $numBoxes, got $inferredNumBoxes")
            numBoxes = inferredNumBoxes
        }

        val detections = mutableListOf<Detection>()

        for (i in 0 until numBoxes) {
            var maxConf = 0f
            var maxClassId = -1

            for (j in 4 until numOutputChannels) {
                val conf = output[j * numBoxes + i]
                if (conf > maxConf) {
                    maxConf = conf
                    maxClassId = j - 4
                }
            }

            val confidence = maxConf
            if (confidence < 0.25f) continue

            val cx = output[i]
            val cy = output[i + numBoxes]
            val w = output[i + 2 * numBoxes]
            val h = output[i + 3 * numBoxes]

            val left = cx - w / 2f
            val top = cy - h / 2f
            val right = cx + w / 2f
            val bottom = cy + h / 2f

            val scaledLeft = (left * originalWidth).coerceIn(0f, originalWidth)
            val scaledTop = (top * originalHeight).coerceIn(0f, originalHeight)
            val scaledRight = (right * originalWidth).coerceIn(0f, originalWidth)
            val scaledBottom = (bottom * originalHeight).coerceIn(0f, originalHeight)

            val className = if (labels.isNotEmpty() && maxClassId >= 0 && maxClassId < labels.size) {
                labels[maxClassId]
            } else {
                "unknown"
            }

            detections.add(
                Detection(maxClassId, className, confidence, scaledLeft, scaledTop, scaledRight, scaledBottom)
            )
        }
        return detections
    }

    private fun nonMaxSuppression(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val current = sorted.removeAt(0)
            result.add(current)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                if (iou(current, other) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return result
    }

    private fun iou(box1: Detection, box2: Detection): Float {
        val interLeft = max(box1.left, box2.left)
        val interTop = max(box1.top, box2.top)
        val interRight = min(box1.right, box2.right)
        val interBottom = min(box1.bottom, box2.bottom)

        val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        val area1 = (box1.right - box1.left) * (box1.bottom - box1.top)
        val area2 = (box2.right - box2.left) * (box2.bottom - box2.top)

        return if (area1 + area2 - interArea > 0) {
            interArea / (area1 + area2 - interArea)
        } else 0f
    }

    fun release() {
        session?.close()
        session = null
    }
}
