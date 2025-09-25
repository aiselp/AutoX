// package: com.autox.onnx
import android.graphics.BitmapFactory
import android.util.Log
import com.stardust.autojs.runtime.api.ScriptRuntime
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@JSThread
class OnnxModule(private val runtime: ScriptRuntime) {

    private val detector = OnnxDetector()
    private val classifier = OnnxClassifier()
    private var labels: List<String> = emptyList()

    @JavascriptInterface
    fun init(options: String): Boolean {
        try {
            val json = JSONObject(options)
            val detectModel = json.optString("detectModel", "")
            val classifyModel = json.optString("classifyModel", "")
            val labelsFile = json.optString("labels", "")

            // 读取标签
            if (labelsFile.isNotEmpty()) {
                val file = File(labelsFile)
                if (file.exists()) {
                    labels = file.readLines()
                }
            }

            // 初始化检测器
            if (detectModel.isNotEmpty()) {
                if (!File(detectModel).exists()) {
                    Log.e("OnnxModule", "Detect model not found: $detectModel")
                    return false
                }
                if (!detector.init(detectModel)) return false
            }

            // 初始化分类器
            if (classifyModel.isNotEmpty()) {
                if (!File(classifyModel).exists()) {
                    Log.e("OnnxModule", "Classify model not found: $classifyModel")
                    return false
                }
                if (!classifier.init(classifyModel)) return false
            }

            return true
        } catch (e: Exception) {
            Log.e("OnnxModule", "Init failed", e)
            return false
        }
    }

    @JavascriptInterface
    fun detect(imagePath: String): String {
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return "[]"
        val results = detector.detect(bitmap, labels)
        val array = JSONArray()
        for (r in results) {
            val obj = JSONObject()
            obj.put("classId", r.classId)
            obj.put("className", r.className)
            obj.put("confidence", r.confidence)
            obj.put("left", r.left)
            obj.put("top", r.top)
            obj.put("right", r.right)
            obj.put("bottom", r.bottom)
            array.put(obj)
        }
        return array.toString()
    }

    @JavascriptInterface
    fun classify(imagePath: String): String {
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return "{}"
        val result = classifier.classify(bitmap, labels)
        return if (result != null) {
            val obj = JSONObject()
            obj.put("classId", result.classId)
            obj.put("className", result.className)
            obj.put("confidence", result.confidence)
            obj.toString()
        } else "{}"
    }

    @JavascriptInterface
    fun release() {
        detector.release()
        classifier.release()
    }
}
