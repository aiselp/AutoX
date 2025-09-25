// OnnxDetector.kt
import ai.onnxruntime.*
import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer
import kotlin.math.exp

class OnnxDetector {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var inputName: String = ""
    private val imageSize = 640
    private val numClasses = 80
    private val numBoxes = 8400

    fun init(modelPath: String): Boolean {
        try {
            val sessionOptions = OrtSession.SessionOptions()
            session = env.createSession(modelPath, sessionOptions)
            inputName = session!!.inputNames.first()
            Log.i("OnnxDetector", "Model loaded: $modelPath, Input: $inputName")
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

        // 1. 预处理：缩放 + 归一化 + RGB → BGR + HWC → CHW
        val resized = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)
        val tensorBuffer = FloatBuffer.allocate(imageSize * imageSize * 3)
        for (y in 0 until imageSize) {
            for (x in 0 until imageSize) {
                val pixel = resized.getPixel(x, y)
                // 提取 BGR 顺序（YOLOv8 使用 OpenCV BGR）
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

        // 调试：打印输出 shape
        val outputShape = results.values.first().shape
        Log.d("OnnxDetector", "Output shape: ${outputShape.contentToString()}")

        // 3. 后处理（YOLOv8 解码）
        val detections = decodeYOLOv8(output, labels, bitmap.width.toFloat(), bitmap.height.toFloat())

        // 4. NMS
        val finalDetections = nonMaxSuppression(detections, iouThreshold = 0.45f)

        // 释放资源
        inputTensor.close()
        results.values.forEach { it.close() }

        return finalDetections
    }

    private fun decodeYOLOv8(
        output: FloatArray,
        labels: List<String>,
        imgWidth: Float,
        imgHeight: Float
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        for (i in 0 until numBoxes) {
            val offset = i * (4 + 1 + numClasses)
            val boxConfidence = sigmoid(output[offset + 4])
            if (boxConfidence < 0.25) continue

            val classScores = FloatArray(numClasses)
            for (c in 0 until numClasses) {
                classScores[c] = output[offset + 5 + c]
            }
            val maxIndex = classScores.indices.maxByOrNull { classScores[it] } ?: continue
            val classConfidence = softmax(classScores)[maxIndex]
            val confidence = boxConfidence * classConfidence
            if (confidence < 0.25) continue

            // YOLOv8 输出是中心 + 宽高（归一化）
            val cx = output[offset]
            val cy = output[offset + 1]
            val w = output[offset + 2]
            val h = output[offset + 3]

            val left = (cx - w / 2).coerceIn(0f, 1f) * imgWidth
            val top = (cy - h / 2).coerceIn(0f, 1f) * imgHeight
            val right = (cx + w / 2).coerceIn(0f, 1f) * imgWidth
            val bottom = (cy + h / 2).coerceIn(0f, 1f) * imgHeight

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
        return detections
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x)))

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp(it - max).toFloat() }.toFloatArray()
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    private fun nonMaxSuppression(
        detections: List<Detection>,
        iouThreshold: Float
    ): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val result = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val best = sorted[0]
            result.add(best)
            // 过滤与 best IoU > threshold 的
            sorted.removeAll { other ->
                iou(best, other) > iouThreshold
            }
        }
        return result
    }

    private fun iou(box1: Detection, box2: Detection): Float {
        val interLeft = maxOf(box1.left, box2.left)
        val interTop = maxOf(box1.top, box2.top)
        val interRight = minOf(box1.right, box2.right)
        val interBottom = minOf(box1.bottom, box2.bottom)

        if (interRight <= interLeft || interBottom <= interTop) return 0f

        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val area1 = (box1.right - box1.left) * (box1.bottom - box1.top)
        val area2 = (box2.right - box2.left) * (box2.bottom - box2.top)
        return interArea / (area1 + area2 - interArea)
    }

    fun release() {
        session?.close()
        session = null
    }
}
