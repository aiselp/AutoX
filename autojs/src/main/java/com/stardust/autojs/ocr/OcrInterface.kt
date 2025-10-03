// autojs/src/main/java/com/stardust/autojs/ocr/OcrInterface.kt
package com.stardust.autojs.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

class OcrInterface {
    private var ocr: OCR? = null
    private var context: Context? = null
    private var useCache = true // 默认使用缓存
    
    fun setContext(context: Context) {
        this.context = context
    }
    
    // 设置是否使用缓存（默认为true）
    fun setUseCache(useCache: Boolean) {
        this.useCache = useCache
    }
    
    // 初始化
    fun init(): Boolean {
        return try {
            if (context == null) {
                throw IllegalStateException("Context not set. Call setContext() first.")
            }
            
            val detModelPath: String
            val recModelPath: String
            
            if (useCache) {
                // 复制到缓存
                val cacheDir = context!!.cacheDir
                detModelPath = copyAssetToCache("models/det.onnx", cacheDir)
                recModelPath = copyAssetToCache("models/rec.onnx", cacheDir)
                println("使用缓存模式：模型已复制到缓存目录")
            } else {
                // 直接使用assets路径（如果ONNX支持）
                detModelPath = "asset://models/det.onnx"
                recModelPath = "asset://models/rec.onnx"
                println("使用直接模式：尝试从assets直接读取")
            }
            
            ocr = OCR(detModelPath, recModelPath)
            println("OCR初始化成功，词汇表大小: ${ocr!!.getVocabSize()}")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果直接模式失败，回退到缓存模式
            if (!useCache) {
                println("直接模式失败，尝试缓存模式")
                return initWithCache()
            }
            false
        }
    }
    
    // 使用缓存模式初始化
    private fun initWithCache(): Boolean {
        return try {
            useCache = true
            val cacheDir = context!!.cacheDir
            val detModelPath = copyAssetToCache("models/det.onnx", cacheDir)
            val recModelPath = copyAssetToCache("models/rec.onnx", cacheDir)
            
            ocr = OCR(detModelPath, recModelPath)
            println("缓存模式初始化成功")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun copyAssetToCache(assetPath: String, cacheDir: File): String {
        return try {
            val outputFile = File(cacheDir, "ocr_${File(assetPath).name}")
            
            // 检查文件是否已存在且是最新的
            if (outputFile.exists() && isAssetNewer(assetPath, outputFile)) {
                println("模型文件已存在且是最新的: ${outputFile.name}")
                return outputFile.absolutePath
            }
            
            val inputStream = context!!.assets.open(assetPath)
            FileOutputStream(outputFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            
            println("模型文件已复制到缓存: ${outputFile.name}")
            outputFile.absolutePath
        } catch (e: Exception) {
            throw RuntimeException("复制模型文件失败: $assetPath", e)
        }
    }
    
    // 检查assets中的文件是否比缓存文件新
    private fun isAssetNewer(assetPath: String, cacheFile: File): Boolean {
        // 这里简化处理，实际可以根据文件修改时间判断
        // 对于assets，我们假设APK安装后不会改变，所以缓存文件总是有效的
        return true
    }
    
    // 清理缓存
    fun clearCache(): Boolean {
        return try {
            val cacheDir = context!!.cacheDir
            val modelFiles = cacheDir.listFiles { file -> 
                file.name.startsWith("ocr_") && file.name.endsWith(".onnx")
            }
            
            modelFiles?.forEach { it.delete() }
            println("已清理 ${modelFiles?.size ?: 0} 个模型缓存文件")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // 获取缓存信息
    fun getCacheInfo(): String {
        val cacheDir = context!!.cacheDir
        val modelFiles = cacheDir.listFiles { file -> 
            file.name.startsWith("ocr_") && file.name.endsWith(".onnx")
        }
        
        return "缓存模式: $useCache, 缓存文件数: ${modelFiles?.size ?: 0}"
    }
    
    // 其他方法保持不变...
    fun recognize(bitmap: Bitmap): Array<Array<Any>> {
        val results = ocr?.ocr(bitmap) ?: return emptyArray()
        return results.map { result ->
            arrayOf(result.box.toTypedArray(), result.text, result.confidence)
        }.toTypedArray()
    }
    
    fun recognizeFile(imagePath: String): Array<Array<Any>> {
        val bitmap = BitmapFactory.decodeFile(imagePath)
        return if (bitmap != null) {
            val results = recognize(bitmap)
            bitmap.recycle()
            results
        } else {
            emptyArray()
        }
    }
    
    fun recognizeBase64(base64Data: String): Array<Array<Any>> {
        return try {
            val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            val results = recognize(bitmap)
            bitmap?.recycle()
            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyArray()
        }
    }
    
    fun release() {
        ocr?.close()
    }
}
