// OnnxModule.kt
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
                    Log.i("OnnxModule", "Loaded ${labels.size} labels from $labelsFile")
                } else {
                    Log.w("OnnxModule", "Labels file not found: $labelsFile")
                }
            }

            var success = true
            if (detectModel.isNotEmpty()) {
                if (!File(detectModel).exists()) {
                    Log.e("OnnxModule", "Detect model not found: $detectModel")
                    success = false
                } else if (!detector.init(detectModel)) {
                    Log.e("OnnxModule", "Failed to init detector")
                    success = false
                }
            }

            if (classifyModel.isNotEmpty()) {
                if (!File(classifyModel).exists()) {
                    Log.e("OnnxModule", "Classify model not found: $classifyModel")
                    success = false
                } else if (!classifier.init(classifyModel)) {
                    Log.e("OnnxModule", "Failed to init classifier")
                    success = false
                }
            }

            return success
        } catch (e: Exception) {
            Log.e("OnnxModule", "Init failed", e)
            return false
        }
    }

    // detect / classify / release 方法无需修改，保持原样
}
