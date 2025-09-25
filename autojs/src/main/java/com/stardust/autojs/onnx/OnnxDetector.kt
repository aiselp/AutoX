// 文件: OnnxDetector.kt
import ai.onnxruntime.*
import android.graphics.Bitmap
import android.util.Log
import kotlin.math.maxOf
import kotlin.math.minOf
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
        return try {
            val sessionOptions = OrtSession.SessionOptions()
            session = env.createSession(modelPath, sessionOptions)

            // ✅ 修复 1: 使用 getInputInfo() 替代已移除的 inputMetadata
            val inputInfo = session!!.getInputInfo()
            if (inputInfo.isEmpty()) {
                Log.e("OnnxDetector", "❌ Model has no input")
                return false
            }

            inputName = inputInfo.keys.first()

            // ✅ 修复 2: 从 TensorInfo 获取 shape
            val shape = (inputInfo[inputName]!!.info as TensorInfo).shape
            if (shape.size == 4) {
                inputHeight = shape[2].toInt() // NCHW: [B, C, H, W]
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

        // 将 Bitmap 转换为 BGR 归一化 FloatBuffer（注意：YOLO 通常用 BGR）
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
        try {
            val outputTensor = results.values.first()
            val output = outputTensor.value as FloatArray

            // ✅ 输出模型输出 shape，便于调试
            Log.d("OnnxDetector", "📊 Output shape: ${outputTensor.shape?.contentToString() ?: "unknown"}")

            // 解码 YOLOv8 输出
            val detections = decodeYOLOv8(output, labels, bitmap.width.toFloat(), bitmap.height.toFloat())
            // 非极大值抑制
            return nonMaxSuppression(detections, iouThreshold = 0.45f)
        } finally {
            // ✅ 修复 3: 确保资源释放（即使解码出错）
            inputTensor.close()
            results.values.forEach { it.close() }
        }
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
            Log.w("OnnxDetector", "⚠️ NumBoxes mismatch: expected $numBoxes, got $inferredNumBoxes")
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
            val cx = output[i]                     // 中心 x
            val cy = output[i + numBoxes]          // 中心 y
            val w = output[i + 2 * numBoxes]       // 宽度
            val h = output[i + 3 * numBoxes]       // 高度

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

        var i = 0
        while (i < sorted.size) {
            val current = sorted[i]
            result.add(current)
            i++

            // 使用索引过滤，避免 ConcurrentModificationException
            val toRemove = mutableListOf<Int>()
            for (j in i until sorted.size) {
                if (iou(current, sorted[j]) > iouThreshold) {
                    toRemove.add(j)
                }
            }
            // 逆序删除
            for (index in toRemove.reversed()) {
                sorted.removeAt(index)
            }
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
