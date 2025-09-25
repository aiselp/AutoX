// OnnxClassifier.kt
import ai.onnxruntime.*
import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer

class OnnxClassifier {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var inputName: String = ""

    // ✅ 动态输入尺寸（根据模型自动设置）
    private var inputWidth = 224
    private var inputHeight = 224

    /**
     * 初始化 ONNX 分类模型
     * @param modelPath 模型文件路径 (.onnx)
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
                Log.i("OnnxClassifier", "✅ Model input size: ${inputWidth}x${inputHeight}")
            } else {
                Log.e("OnnxClassifier", "❌ Unexpected input shape: ${shape.contentToString()}")
                return false
            }

            return true
        } catch (e: Exception) {
            Log.e("OnnxClassifier", "❌ Failed to load model: $modelPath", e)
            return false
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
     * @param bitmap 输入图像
     * @param labels 类别标签列表 (如 ["cat", "dog", ...])
     * @return 最高置信度的分类结果，或 null
     */
    fun classify(bitmap: Bitmap, labels: List<String>): Classification? {
        val session = this.session ?: throw IllegalStateException("Model not initialized")

        // ✅ 使用模型实际输入尺寸进行缩放
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val tensorBuffer = FloatBuffer.allocate(inputWidth * inputHeight * 3)

        // 将 Bitmap 转换为 BGR 归一化 FloatBuffer
        for (y in 0 until inputHeight) {
            for (x in 0 until inputWidth) {
                val pixel = resized.getPixel(x, y)
                tensorBuffer.put(((pixel shr 16) and 0xFF) / 255f)  // Blue
                tensorBuffer.put(((pixel shr 8) and 0xFF) / 255f)   // Green
                tensorBuffer.put((pixel and 0xFF) / 255f)           // Red
            }
        }
        tensorBuffer.rewind()

        // 创建 ONNX 输入张量 [1, 3, H, W]
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
        Log.d("OnnxClassifier", "📊 Output shape: ${outputShape.contentToString()}")

        // 应用 Softmax 获取概率
        val probabilities = softmax(output)

        // 找到最大概率的类别
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
        val confidence = probabilities[maxIndex]

        // 安全获取类别名
        val className = labels.getOrNull(maxIndex) ?: "unknown (id=$maxIndex)"

        val result = Classification(
            classId = maxIndex,
            className = className,
            confidence = confidence
        )

        // 释放资源
        inputTensor.close()
        results.values.forEach { it.close() }

        return result
    }

    /**
     * Softmax 函数：将 logits 转换为概率分布
     */
    private fun softmax(logits: FloatArray): FloatArray {
        // 数值稳定化：减去最大值
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
