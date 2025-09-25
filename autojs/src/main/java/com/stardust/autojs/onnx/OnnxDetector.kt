// OnnxDetector.kt
import ai.onnxruntime.*
import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer

class OnnxDetector {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var inputName: String = ""

    // ✅ 动态输入尺寸（根据模型自动设置）
    private var inputWidth = 640
    private var inputHeight = 640
    private val numClasses = 80
    private var numBoxes = 8400 // 可根据输出 shape 优化

    /**
     * 初始化 ONNX 模型
     * @param modelPath 模型文件路径
     * @return 是否加载成功
     */
    fun init(modelPath: String): Boolean {
        try {
            val sessionOptions = OrtSession.SessionOptions()
            session = env.createSession(modelPath, sessionOptions)
            inputName = session!!.inputNames.first()

            // ✅ 从模型元数据获取输入尺寸 [1, 3, H, W]
            val inputMetadata = session!!.inputMetadata
            val shape = inputMetadata[inputName]!!.shape

            if (shape.size == 4) {
                inputHeight = shape[2].toInt()
                inputWidth = shape[3].toInt()
                Log.i("OnnxDetector", "✅ Model input size: ${inputWidth}x${inputHeight}")
            } else {
                Log.e("OnnxDetector", "❌ Unexpected input shape: ${shape.contentToString()}")
                return false
            }

            return true
        } catch (e: Exception) {
            Log.e("OnnxDetector", "❌ Failed to load model: $modelPath", e)
            return false
        }
    }

    /**
     * 检测结果数据类
     */
    data class Detection(
        val classId: Int,
        val className: String,
        val confidence: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    /**
     * 执行目标检测
     * @param bitmap 输入图像
     * @param labels 类别标签列表
     * @return 检测结果列表
     */
    fun detect(bitmap: Bitmap, labels: List<String>): List<Detection> {
        val session = this.session ?: throw IllegalStateException("Model not initialized")

        // ✅ 使用模型实际输入尺寸进行缩放
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val tensorBuffer = FloatBuffer.allocate(inputWidth * inputHeight * 3)

        // 将 Bitmap 转换为 BGR 归一化 FloatBuffer
        for (y in 0 until inputHeight) {
            for (x in 0 until inputWidth) {
                val pixel = resized.getPixel(x, y)
                tensorBuffer.put(((pixel shr 16) and 0xFF) / 255f)  // B
                tensorBuffer.put(((pixel shr 8) and 0xFF) / 255f)   // G
                tensorBuffer.put((pixel and 0xFF) / 255f)           // R
            }
        }
        tensorBuffer.rewind()

        // 创建 ONNX 输入张量
        val inputTensor = OnnxTensor.createTensor(
            env,
            tensorBuffer,
            longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        )

        // 执行推理
        val results = session.run(mapOf(inputName to inputTensor))
        val output = results.values.first().value as FloatArray

        // ✅ 输出模型输出 shape，便于调试
        val outputShape = results.values.first().shape
        Log.d("OnnxDetector", "📊 Output shape: ${outputShape.contentToString()}")

        // 解码 YOLOv8 输出
        val detections = decodeYOLOv8(output, labels, bitmap.width.toFloat(), bitmap.height.toFloat())
        // 非极大值抑制
        val finalDetections = nonMaxSuppression(detections, iouThreshold = 0.45f)

        // 释放资源
        inputTensor.close()
        results.values.forEach { it.close() }

        return finalDetections
    }

    /**
     * 解码 YOLOv8 模型输出
     * 输出 shape: [1, numClasses + 4, numBoxes] -> 转置后为 [numBoxes, numClasses + 4]
     */
    private fun decodeYOLOv8(
        output: FloatArray,
        labels: List<String>,
        originalWidth: Float,
        originalHeight: Float
    ): List<Detection> {
        val numOutputChannels = numClasses + 4 // 80 classes + 4 bbox
        val numOutputElements = output.size

        // 推断 numBoxes: total_elements / (numClasses + 4)
        val inferredNumBoxes = numOutputElements / numOutputChannels
        if (inferredNumBoxes != numBoxes) {
            Log.w("OnnxDetector", "⚠️  NumBoxes mismatch: expected $numBoxes, got $inferredNumBoxes")
            numBoxes = inferredNumBoxes
        }

        val detections = mutableListOf<Detection>()

        for (i in 0 until numBoxes) {
            val classScoresStart = 4 // 前4个是 bbox (cx, cy, w, h)
            val classScoresEnd = numOutputChannels
            var maxConf = 0f
            var maxClassId = -1

            // 找到置信度最高的类别
            for (j in classScoresStart until classScoresEnd) {
                val conf = output[j * numBoxes + i] // 注意：YOLOv8 输出是 [84, 8400]
                if (conf > maxConf) {
                    maxConf = conf
                    maxClassId = j - classScoresStart
                }
            }

            // 使用目标检测置信度（YOLOv8 输出中，bbox 置信度已与 class 置信度相乘）
            val confidence = maxConf
            if (confidence < 0.25f) continue // 置信度过滤

            // 提取 bbox (cx, cy, w, h)
            val cx = output[i]           // 中心 x
            val cy = output[i + numBoxes]     // 中心 y
            val w = output[i + 2 * numBoxes]  // 宽度
            val h = output[i + 3 * numBoxes]  // 高度

            // 转换为左上角和右下角坐标 (归一化)
            val left = cx - w / 2f
            val top = cy - h / 2f
            val right = cx + w / 2f
            val bottom = cy + h / 2f

            // 将归一化坐标映射回原始图像尺寸
            val scaledLeft = (left * originalWidth).coerceIn(0f, originalWidth)
            val scaledTop = (top * originalHeight).coerceIn(0f, originalHeight)
            val scaledRight = (right * originalWidth).coerceIn(0f, originalWidth)
            val scaledBottom = (bottom * originalHeight).coerceIn(0f, originalHeight)

            val className = labels.getOrNull(maxClassId) ?: "unknown"

            detections.add(
                Detection(
                    classId = maxClassId,
                    className = className,
                    confidence = confidence,
                    left = scaledLeft,
                    top = scaledTop,
                    right = scaledRight,
                    bottom = scaledBottom
                )
            )
        }

        return detections
    }

    /**
     * 非极大值抑制 (NMS)
     */
    private fun nonMaxSuppression(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val result = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val current = sorted.removeAt(0)
            result.add(current)

            sorted.removeAll { iou(current, it) > iouThreshold }
        }

        return result
    }

    /**
     * 计算两个框的 IoU
     */
    private fun iou(box1: Detection, box2: Detection): Float {
        val interLeft = maxOf(box1.left, box2.left)
        val interTop = maxOf(box1.top, box2.top)
        val interRight = minOf(box1.right, box2.right)
        val interBottom = minOf(box1.bottom, box2.bottom)

        val interArea = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        val area1 = (box1.right - box1.left) * (box1.bottom - box1.top)
        val area2 = (box2.right - box2.left) * (box2.bottom - box2.top)

        return interArea / (area1 + area2 - interArea)
    }

    /**
     * Sigmoid 函数
     */
    private fun sigmoid(x: Float): Float = 1f / (1f + kotlin.math.exp(-x).toFloat())

    /**
     * Softmax 函数（未使用，保留备用）
     */
    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = logits.map { kotlin.math.exp(it - max).toFloat() }
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    /**
     * 释放模型资源
     */
    fun release() {
        session?.close()
        session = null
    }
}
