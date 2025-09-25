import ai.onnxruntime.*
import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import java.util.Collections
import com.stardust.autojs.runtime.ScriptRuntime
import com.stardust.autojs.runtime.ScriptRuntimeV2
import com.stardust.autojs.annotation.ScriptInterface
import com.stardust.autojs.runtime.api.Threads

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

            // ✅ 修复 1: 使用 getInputInfo() 替代已移除的 inputMetadata
            val inputInfo = session!!.inputInfo
            if (inputInfo.isEmpty()) {
                Log.e("OnnxClassifier", "❌ Model has no input")
                return false
            }

            inputName = inputInfo.keys.first()

            // ✅ 修复 2: 从 TensorInfo 获取 shape
            val shape = (inputInfo[inputName]!!.info as TensorInfo).shape
            if (shape.size == 4) {
                inputHeight = shape[2].toInt() // NCHW: [B, C, H, W]
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
     * @param bitmap 输入图像
     * @param labels 标签列表
     * @return 分类结果，失败返回 null
     */
    fun classify(bitmap: Bitmap, labels: List<String>): Classification? {
        val session = this.session ?: throw IllegalStateException("Model not initialized")

        // 缩放图像
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val tensorBuffer = FloatBuffer.allocate(inputWidth * inputHeight * 3)

        // ✅ 像素归一化：0-255 -> 0.0-1.0 (RGB)
        for (y in 0 until inputHeight) {
            for (x in 0 until inputWidth) {
                val pixel = resized.getPixel(x, y)
                tensorBuffer.put(((pixel shr 16) and 0xFF) / 255f) // R
                tensorBuffer.put(((pixel shr 8) and 0xFF) / 255f)  // G
                tensorBuffer.put((pixel and 0xFF) / 255f)          // B
            }
        }
        tensorBuffer.rewind()

        // ✅ 创建输入 Tensor（注意：NCHW 格式）
        val inputTensor = OnnxTensor.createTensor(
            env,
            tensorBuffer,
            longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        )

        try {
            // ✅ 修复 3: 使用正确的API执行推理
            val results = session.run(Collections.singletonMap(inputName, inputTensor))
            try {
                val outputTensor = results.get(0) // 获取第一个输出
                val output = outputTensor.value as FloatArray

                Log.d("OnnxClassifier", "📊 Output shape: ${outputTensor.info.dims.contentToString()}")

                // ✅ 后处理：Softmax + 取最大概率
                val probabilities = softmax(output)
                val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
                val confidence = probabilities[maxIndex]
                val className = labels.getOrNull(maxIndex) ?: "unknown"

                return Classification(maxIndex, className, confidence)
            } finally {
                // ✅ 修复 4: 正确释放结果资源
                results.close()
            }
        } finally {
            // ✅ 修复 5: 正确释放输入资源
            inputTensor.close()
        }
    }

    /**
     * Softmax 激活函数
     */
    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp(it - max).toFloat() }
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
