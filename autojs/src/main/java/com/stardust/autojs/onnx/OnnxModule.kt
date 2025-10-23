// autojs/src/main/java/com/stardust/autojs/onnx/OnnxModule.kt
package com.stardust.autojs.onnx

import android.graphics.Bitmap
import android.util.Log
import com.stardust.autojs.core.image.ImageWrapper
import com.stardust.autojs.runtime.ScriptRuntime
import org.json.JSONArray
import org.json.JSONObject

class OnnxModule(private val runtime: ScriptRuntime) {

    private val classifiers = mutableMapOf<String, OnnxClassifier>()
    private val detectors = mutableMapOf<String, OnnxDetector>()

    // === 目标检测 - 全自动接口 ===

    /**
     * 完全自动化的检测器加载 - 推荐使用
     */
    @android.webkit.JavascriptInterface
    fun loadDetectorAuto(name: String, path: String): String {
        return try {
            val detector = OnnxDetector(runtime)
            detector.loadModelAuto(path)
            detectors[name] = detector
            
            val initInfo = detector.getInitializationInfo()
            Log.d("OnnxModule", "检测器 '$name' 全自动加载完成: $initInfo")
            
            JSONObject().apply {
                put("success", true)
                put("message", "检测器加载成功")
                put("name", name)
                put("initInfo", JSONObject(initInfo))
            }.toString()
        } catch (e: Exception) {
            Log.e("OnnxModule", "检测器加载失败", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "未知错误")
            }.toString()
        }
    }

    /**
     * 智能检测 - 接收ImageWrapper对象
     */
    @android.webkit.JavascriptInterface  
    fun detectImageAuto(name: String, imageWrapper: ImageWrapper, confThreshold: Float = 0.25f, iouThreshold: Float = 0.45f): String {
        return try {
            val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
            
            Log.d("OnnxModule", "开始智能检测 - 模型: $name, 置信度阈值: $confThreshold, IOU阈值: $iouThreshold")
            Log.d("OnnxModule", "输入图像尺寸: ${imageWrapper.width}x${imageWrapper.height}")
            
            val bitmap = imageWrapper.bitmap
                ?: throw IllegalArgumentException("ImageWrapper bitmap is null")
            
            val results = d.detectAuto(bitmap, confThreshold, iouThreshold)
            
            Log.d("OnnxModule", "智能检测完成 - 检测到 ${results.size} 个目标")
            
            val resultsArray = JSONArray()
            results.forEach { r ->
                JSONObject().apply {
                    put("label", r.label)
                    put("score", r.score.toDouble())
                    put("box", JSONArray().apply {
                        put(r.box[0].toDouble())
                        put(r.box[1].toDouble())
                        put(r.box[2].toDouble())
                        put(r.box[3].toDouble())
                    })
                }.let { resultsArray.put(it) }
            }
            
            JSONObject().apply {
                put("success", true)
                put("detections", resultsArray)
                put("count", results.size)
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            Log.e("OnnxModule", "检测失败", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "检测失败")
            }.toString()
        }
    }

    /**
     * 智能检测 - 接收Bitmap对象（备用方法）
     */
    @android.webkit.JavascriptInterface  
    fun detectImageAutoWithBitmap(name: String, bitmap: Bitmap, confThreshold: Float = 0.25f, iouThreshold: Float = 0.45f): String {
        return try {
            val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
            
            Log.d("OnnxModule", "开始智能检测(Bitmap) - 模型: $name, 图像尺寸: ${bitmap.width}x${bitmap.height}")
            
            val results = d.detectAuto(bitmap, confThreshold, iouThreshold)
            
            Log.d("OnnxModule", "智能检测完成 - 检测到 ${results.size} 个目标")
            
            val resultsArray = JSONArray()
            results.forEach { r ->
                JSONObject().apply {
                    put("label", r.label)
                    put("score", r.score.toDouble())
                    put("box", JSONArray().apply {
                        put(r.box[0].toDouble())
                        put(r.box[1].toDouble())
                        put(r.box[2].toDouble())
                        put(r.box[3].toDouble())
                    })
                }.let { resultsArray.put(it) }
            }
            
            JSONObject().apply {
                put("success", true)
                put("detections", resultsArray)
                put("count", results.size)
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            Log.e("OnnxModule", "检测失败", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "检测失败")
            }.toString()
        }
    }

    /**
     * 获取检测器预处理配置信息
     */
    @android.webkit.JavascriptInterface
    fun getDetectorPreprocessConfig(name: String): String {
        return try {
            val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
            val configInfo = d.getPreprocessConfigInfo()
            
            JSONObject().apply {
                put("success", true)
                put("config", JSONObject(configInfo))
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "获取配置失败")
            }.toString()
        }
    }

    /**
     * 获取检测器初始化信息
     */
    @android.webkit.JavascriptInterface
    fun getDetectorInitInfo(name: String): String {
        return try {
            val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
            val info = d.getInitializationInfo()
            
            JSONObject().apply {
                put("success", true)
                put("initInfo", JSONObject(info))
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "获取初始化信息失败")
            }.toString()
        }
    }

    /**
     * 获取检测器输入尺寸信息
     */
    @android.webkit.JavascriptInterface
    fun getDetectorInputSizeInfo(name: String): String {
        return try {
            val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
            val info = d.getInputSizeInfo()
            
            JSONObject().apply {
                put("success", true)
                put("inputSizeInfo", JSONObject(info))
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "获取输入尺寸信息失败")
            }.toString()
        }
    }

    /**
     * 调试检测输出 - 接收ImageWrapper对象
     */
    @android.webkit.JavascriptInterface
    fun debugDetection(name: String, imageWrapper: ImageWrapper): String {
        return try {
            val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
            
            val bitmap = imageWrapper.bitmap
                ?: throw IllegalArgumentException("ImageWrapper bitmap is null")
            
            val debugInfo = d.debugDetection(bitmap)
            
            JSONObject().apply {
                put("success", true)
                put("debugInfo", JSONObject(debugInfo))
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            Log.e("OnnxModule", "调试检测失败", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "调试检测失败")
            }.toString()
        }
    }

    // === 分类器 - 全自动接口 ===

    /**
     * 完全自动化的分类器加载 - 推荐使用
     */
    @android.webkit.JavascriptInterface
    fun loadClassifierAuto(name: String, path: String): String {
        return try {
            val classifier = OnnxClassifier(runtime)
            classifier.loadModelAuto(path)
            classifiers[name] = classifier
            
            val initInfo = classifier.getInitializationInfo()
            Log.d("OnnxModule", "分类器 '$name' 全自动加载完成: $initInfo")
            
            JSONObject().apply {
                put("success", true)
                put("message", "分类器加载成功")
                put("name", name)
                put("initInfo", JSONObject(initInfo))
            }.toString()
        } catch (e: Exception) {
            Log.e("OnnxModule", "分类器加载失败", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "未知错误")
            }.toString()
        }
    }

    /**
     * 智能分类 - 接收ImageWrapper对象
     */
    @android.webkit.JavascriptInterface  
    fun classifyImageAuto(name: String, imageWrapper: ImageWrapper, topK: Int): String {
        return try {
            val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
            
            Log.d("OnnxModule", "开始智能分类 - 模型: $name, 图像尺寸: ${imageWrapper.width}x${imageWrapper.height}")
            
            val bitmap = imageWrapper.bitmap
                ?: throw IllegalArgumentException("ImageWrapper bitmap is null")
            
            val results = c.classifyAuto(bitmap, topK)
            
            Log.d("OnnxModule", "智能分类完成 - 结果数量: ${results.size}")
            
            val resultsArray = JSONArray()
            results.forEach { r ->
                JSONObject().apply {
                    put("label", r.label)
                    put("score", r.score.toDouble())
                }.let { resultsArray.put(it) }
            }
            
            JSONObject().apply {
                put("success", true)
                put("classifications", resultsArray)
                put("count", results.size)
                put("model", name)
                put("topK", topK)
            }.toString()
        } catch (e: Exception) {
            Log.e("OnnxModule", "分类失败", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "分类失败")
            }.toString()
        }
    }

    /**
     * 智能分类 - 接收Bitmap对象（备用方法）
     */
    @android.webkit.JavascriptInterface  
    fun classifyImageAutoWithBitmap(name: String, bitmap: Bitmap, topK: Int): String {
        return try {
            val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
            
            Log.d("OnnxModule", "开始智能分类(Bitmap) - 模型: $name, 图像尺寸: ${bitmap.width}x${bitmap.height}")
            
            val results = c.classifyAuto(bitmap, topK)
            
            Log.d("OnnxModule", "智能分类完成 - 结果数量: ${results.size}")
            
            val resultsArray = JSONArray()
            results.forEach { r ->
                JSONObject().apply {
                    put("label", r.label)
                    put("score", r.score.toDouble())
                }.let { resultsArray.put(it) }
            }
            
            JSONObject().apply {
                put("success", true)
                put("classifications", resultsArray)
                put("count", results.size)
                put("model", name)
                put("topK", topK)
            }.toString()
        } catch (e: Exception) {
            Log.e("OnnxModule", "分类失败", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "分类失败")
            }.toString()
        }
    }

    /**
     * 获取分类器预处理配置信息
     */
    @android.webkit.JavascriptInterface
    fun getPreprocessConfig(name: String): String {
        return try {
            val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
            val configInfo = c.getPreprocessConfigInfo()
            
            JSONObject().apply {
                put("success", true)
                put("config", JSONObject(configInfo))
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "获取配置失败")
            }.toString()
        }
    }

    // 原有的加载方法（保持兼容）- 返回JSON格式
    @android.webkit.JavascriptInterface
    fun loadClassifier(name: String, path: String, classNamesJson: String? = null, inputSize: Int = 0): String {
        return try {
            val classifier = OnnxClassifier(runtime)
            classifier.loadModel(path)
            
            if (inputSize > 0) {
                classifier.setInputSize(inputSize)
            }
            
            if (classNamesJson != null) {
                try {
                    val arr = JSONArray(classNamesJson)
                    classifier.setClassNames((0 until arr.length()).map { arr.getString(it) })
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid classNames JSON", e)
                }
            }
            classifiers[name] = classifier
            
            Log.d("OnnxModule", "分类器 '$name' 加载完成，最终输入尺寸: ${classifier.effectiveInputSize}")
            
            JSONObject().apply {
                put("success", true)
                put("message", "分类器加载成功")
                put("name", name)
                put("effectiveInputSize", classifier.effectiveInputSize)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "分类器加载失败")
            }.toString()
        }
    }

    // 两个参数的方法（保持兼容）- 返回JSON格式
    @android.webkit.JavascriptInterface
    fun loadClassifier(name: String, path: String): String {
        return loadClassifier(name, path, null, 0)
    }

    // 三个参数的方法（保持兼容）- 返回JSON格式
    @android.webkit.JavascriptInterface
    fun loadClassifier(name: String, path: String, classNamesJson: String?): String {
        return loadClassifier(name, path, classNamesJson, 0)
    }

    // 专门的尺寸设置方法 - 返回JSON格式
    @android.webkit.JavascriptInterface
    fun loadClassifierWithSize(name: String, path: String, inputSize: Int): String {
        return loadClassifier(name, path, null, inputSize)
    }

    @android.webkit.JavascriptInterface
    fun loadClassifierWithSizeAndNames(name: String, path: String, inputSize: Int, classNamesJson: String): String {
        return loadClassifier(name, path, classNamesJson, inputSize)
    }

    // 检测器兼容接口 - 返回JSON格式
    @android.webkit.JavascriptInterface
    fun loadDetector(name: String, path: String, width: Int, height: Int, classNamesJson: String? = null): String {
        return try {
            val detector = OnnxDetector(runtime, width, height)
            detector.loadModel(path)
            if (classNamesJson != null) {
                try {
                    val arr = JSONArray(classNamesJson)
                    detector.setClassNames((0 until arr.length()).map { arr.getString(it) })
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid classNames JSON", e)
                }
            }
            detectors[name] = detector
            
            Log.d("OnnxModule", "检测器 '$name' 加载完成，用户设置: ${width}x${height}, 自动检测: ${detector.effectiveInputSize}")
            
            JSONObject().apply {
                put("success", true)
                put("message", "检测器加载成功")
                put("name", name)
                put("userInputSize", "$width x $height")
                put("effectiveInputSize", detector.effectiveInputSize)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "检测器加载失败")
            }.toString()
        }
    }

    @android.webkit.JavascriptInterface
    fun loadDetector(name: String, path: String, width: Int, height: Int): String {
        return loadDetector(name, path, width, height, null)
    }

    // 获取分类器初始化信息 - 返回JSON格式
    @android.webkit.JavascriptInterface
    fun getClassifierInitInfo(name: String): String {
        return try {
            val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
            val info = c.getInitializationInfo()
            
            JSONObject().apply {
                put("success", true)
                put("initInfo", JSONObject(info))
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "获取初始化信息失败")
            }.toString()
        }
    }

    // 获取分类器输入尺寸信息 - 返回JSON格式
    @android.webkit.JavascriptInterface
    fun getClassifierInputSizeInfo(name: String): String {
        return try {
            val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
            val info = c.getInputSizeInfo()
            
            JSONObject().apply {
                put("success", true)
                put("inputSizeInfo", JSONObject(info))
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "获取输入尺寸信息失败")
            }.toString()
        }
    }

    // 获取分类器元数据信息 - 返回JSON格式
    @android.webkit.JavascriptInterface
    fun getClassifierMetadata(name: String): String {
        return try {
            val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
            val wrapper = c.getWrapperForDebug()
            val metadataInfo = wrapper?.getMetadataInfo() ?: mapOf("error" to "No wrapper")
            
            JSONObject().apply {
                put("success", true)
                put("metadata", JSONObject(metadataInfo))
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "获取元数据失败")
            }.toString()
        }
    }

    // 调试方法：获取详细的分类器状态 - 返回JSON格式
    @android.webkit.JavascriptInterface
    fun debugClassifier(name: String): String {
        return try {
            val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
            
            val debugInfo = mutableMapOf<String, Any>()
            
            // 基本信息
            debugInfo["effective_input_size"] = c.effectiveInputSize
            debugInfo["effective_class_names"] = c.effectiveClassNames
            debugInfo["effective_class_count"] = c.effectiveClassNames.size
            
            // 预处理配置
            val config = c.effectivePreprocessConfig
            debugInfo["preprocess_config"] = config.toString()
            
            // 元数据信息
            val wrapper = c.getWrapperForDebug()
            debugInfo["metadata_keys"] = wrapper?.metadata?.keys ?: emptySet<String>()
            
            wrapper?.metadata?.get("imgsz")?.let { debugInfo["metadata_imgsz"] = it }
            wrapper?.metadata?.get("names")?.let { debugInfo["metadata_names"] = it }
            
            debugInfo["parsed_input_size"] = wrapper?.metadataInputSize?.toString() ?: "null"
            debugInfo["parsed_class_names"] = wrapper?.metadataClassNames?.toString() ?: "null"
            debugInfo["input_shape"] = wrapper?.inputShape?.contentToString() ?: "null"
            
            JSONObject().apply {
                put("success", true)
                put("debugInfo", JSONObject(debugInfo as Map<*, *>))
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "调试分类器失败")
            }.toString()
        }
    }

    // 智能分类 - 接收ImageWrapper对象 - 返回JSON格式
    @android.webkit.JavascriptInterface
    fun classifyImage(name: String, imageWrapper: ImageWrapper, topK: Int): String {
        return try {
            val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
            
            val inputSize = c.effectiveInputSize
            Log.d("OnnxModule", "智能分类，使用检测到的输入尺寸: $inputSize, 图像尺寸: ${imageWrapper.width}x${imageWrapper.height}")
            
            val bitmap = imageWrapper.bitmap
                ?: throw IllegalArgumentException("ImageWrapper bitmap is null")
            
            val results = c.classifyAuto(bitmap, topK)
            
            val resultsArray = JSONArray()
            results.forEach { r ->
                JSONObject().apply {
                    put("label", r.label)
                    put("score", r.score.toDouble())
                }.let { resultsArray.put(it) }
            }
            
            JSONObject().apply {
                put("success", true)
                put("classifications", resultsArray)
                put("count", results.size)
                put("model", name)
                put("topK", topK)
            }.toString()
        } catch (e: Exception) {
            Log.e("OnnxModule", "分类失败", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "分类失败")
            }.toString()
        }
    }

    // 原有的 classifyImage 方法（保持兼容性）- 返回JSON格式
    @android.webkit.JavascriptInterface
    fun classifyImageWithSize(name: String, imageWrapper: ImageWrapper, inputSize: Int, topK: Int): String {
        return try {
            val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
            
            Log.d("OnnxModule", "分类图像，指定输入尺寸: $inputSize, 图像尺寸: ${imageWrapper.width}x${imageWrapper.height}")
            
            val bitmap = imageWrapper.bitmap
                ?: throw IllegalArgumentException("ImageWrapper bitmap is null")
            
            val results = c.classifyAuto(bitmap, topK)
            
            val resultsArray = JSONArray()
            results.forEach { r ->
                JSONObject().apply {
                    put("label", r.label)
                    put("score", r.score.toDouble())
                }.let { resultsArray.put(it) }
            }
            
            JSONObject().apply {
                put("success", true)
                put("classifications", resultsArray)
                put("count", results.size)
                put("model", name)
                put("topK", topK)
            }.toString()
        } catch (e: Exception) {
            Log.e("OnnxModule", "分类失败", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "分类失败")
            }.toString()
        }
    }

    // 检测方法（保持兼容）- 返回JSON格式
    @android.webkit.JavascriptInterface
    fun detect(name: String, input: FloatArray): String {
        return try {
            val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
            val results = d.detect(input)
            
            val resultsArray = JSONArray()
            results.forEach { r ->
                JSONObject().apply {
                    put("label", r.label)
                    put("score", r.score.toDouble())
                    put("box", JSONArray().apply {
                        put(r.box[0].toDouble())
                        put(r.box[1].toDouble())
                        put(r.box[2].toDouble())
                        put(r.box[3].toDouble())
                    })
                }.let { resultsArray.put(it) }
            }
            
            JSONObject().apply {
                put("success", true)
                put("detections", resultsArray)
                put("count", results.size)
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "检测失败")
            }.toString()
        }
    }

    // 检测方法 - 接收ImageWrapper对象 - 返回JSON格式
    @android.webkit.JavascriptInterface
    fun detectImage(name: String, imageWrapper: ImageWrapper): String {
        return try {
            val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
            
            Log.d("OnnxModule", "检测图像，图像尺寸: ${imageWrapper.width}x${imageWrapper.height}")
            
            val bitmap = imageWrapper.bitmap
                ?: throw IllegalArgumentException("ImageWrapper bitmap is null")
            
            val results = d.detectAuto(bitmap)
            
            val resultsArray = JSONArray()
            results.forEach { r ->
                JSONObject().apply {
                    put("label", r.label)
                    put("score", r.score.toDouble())
                    put("box", JSONArray().apply {
                        put(r.box[0].toDouble())
                        put(r.box[1].toDouble())
                        put(r.box[2].toDouble())
                        put(r.box[3].toDouble())
                    })
                }.let { resultsArray.put(it) }
            }
            
            JSONObject().apply {
                put("success", true)
                put("detections", resultsArray)
                put("count", results.size)
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            Log.e("OnnxModule", "检测失败", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "检测失败")
            }.toString()
        }
    }

    // 改进的 getOutputInfo 方法 - 接收ImageWrapper对象 - 返回JSON格式
    @android.webkit.JavascriptInterface
    fun getClassifierOutputInfo(name: String, imageWrapper: ImageWrapper): String {
        return try {
            val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
            
            val bitmap = imageWrapper.bitmap
                ?: throw IllegalArgumentException("ImageWrapper bitmap is null")
            
            val outputInfo = c.getOutputInfo(bitmap)
            
            JSONObject().apply {
                put("success", true)
                put("outputInfo", JSONObject(outputInfo))
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            Log.e("OnnxModule", "获取输出信息失败", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "获取输出信息失败")
            }.toString()
        }
    }

    // 原有的 classifyImage32x32 方法（保持兼容性）- 返回JSON格式
    @android.webkit.JavascriptInterface
    fun classifyImage32x32(name: String, imageWrapper: ImageWrapper, topK: Int): String {
        return classifyImageWithSize(name, imageWrapper, 32, topK)
    }

    // 原有的 getClassifierOutputInfo32x32 方法（保持兼容性）- 返回JSON格式
    @android.webkit.JavascriptInterface
    fun getClassifierOutputInfo32x32(name: String, imageWrapper: ImageWrapper): String {
        return getClassifierOutputInfo(name, imageWrapper)
    }

    // === 调试方法 ===

    @android.webkit.JavascriptInterface
    fun setClassifierOutputType(name: String, type: String): String {
        return try {
            val c = classifiers[name] ?: throw IllegalArgumentException("Classifier $name not loaded")
            when (type.toLowerCase()) {
                "logits" -> c.setOutputType(OnnxClassifier.OutputType.LOGITS)
                "probabilities" -> c.setOutputType(OnnxClassifier.OutputType.PROBABILITIES)
                "auto" -> c.setOutputType(OnnxClassifier.OutputType.AUTO_DETECT)
                else -> throw IllegalArgumentException("Unknown output type: $type")
            }
            
            JSONObject().apply {
                put("success", true)
                put("message", "输出类型设置成功")
                put("outputType", type)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "设置输出类型失败")
            }.toString()
        }
    }

    /**
     * 获取检测器元数据信息 - 返回JSON格式
     */
    @android.webkit.JavascriptInterface
    fun getDetectorMetadata(name: String): String {
        return try {
            val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
            val metadataInfo = d.getMetadataInfo()
            
            JSONObject().apply {
                put("success", true)
                put("metadata", JSONObject(metadataInfo))
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "获取元数据失败")
            }.toString()
        }
    }
    
    @android.webkit.JavascriptInterface
    fun debugDetectorOutput(name: String, imageWrapper: ImageWrapper): String {
        return try {
            val d = detectors[name] ?: throw IllegalArgumentException("Detector $name not loaded")
            
            val bitmap = imageWrapper.bitmap
                ?: throw IllegalArgumentException("ImageWrapper bitmap is null")
            
            val input = ImagePreprocessor.preprocessYoloV8(bitmap, d.inputWidth, d.inputHeight)
            val outputInfo = d.debugOutput(input)
            
            JSONObject().apply {
                put("success", true)
                put("debugOutput", JSONObject(outputInfo))
                put("model", name)
            }.toString()
        } catch (e: Exception) {
            Log.e("OnnxModule", "调试检测输出失败", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "调试检测输出失败")
            }.toString()
        }
    }

    @android.webkit.JavascriptInterface
    fun unloadClassifier(name: String): String {
        return try {
            classifiers[name]?.close()
            classifiers.remove(name)
            
            JSONObject().apply {
                put("success", true)
                put("message", "分类器卸载成功")
                put("name", name)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "卸载分类器失败")
            }.toString()
        }
    }

    @android.webkit.JavascriptInterface
    fun unloadDetector(name: String): String {
        return try {
            detectors[name]?.close()
            detectors.remove(name)
            
            JSONObject().apply {
                put("success", true)
                put("message", "检测器卸载成功")
                put("name", name)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "卸载检测器失败")
            }.toString()
        }
    }

    @android.webkit.JavascriptInterface
    fun cleanup(): String {
        return try {
            classifiers.values.forEach { it.close() }
            detectors.values.forEach { it.close() }
            classifiers.clear()
            detectors.clear()
            
            JSONObject().apply {
                put("success", true)
                put("message", "清理完成")
                put("classifiersCleared", classifiers.size)
                put("detectorsCleared", detectors.size)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "清理失败")
            }.toString()
        }
    }
}
