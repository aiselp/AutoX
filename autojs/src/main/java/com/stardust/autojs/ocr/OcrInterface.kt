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
    
    fun setContext(context: Context) {
        this.context = context
    }
    
    // 初始化 - 始终使用缓存模式
    fun init(): Boolean {
        return try {
            if (context == null) {
                throw IllegalStateException("Context not set. Call setContext() first.")
            }
            
            val cacheDir = context!!.cacheDir
            val detModelPath = copyAssetToCache("models/det.onnx", cacheDir)
            val recModelPath = copyAssetToCache("models/rec.onnx", cacheDir)
            
            ocr = OCR(detModelPath, recModelPath)
            
            println("OCR初始化成功，词汇表大小: ${ocr!!.getVocabSize()}")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // 复制assets文件到缓存
    private fun copyAssetToCache(assetPath: String, cacheDir: File): String {
        return try {
            val outputFile = File(cacheDir, "ocr_${File(assetPath).name}")
            
            // 如果文件已存在，直接使用（避免重复复制）
            if (outputFile.exists()) {
                println("使用已缓存的模型文件: ${outputFile.name}")
                return outputFile.absolutePath
            }
            
            // 复制文件到缓存
            val inputStream = context!!.assets.open(assetPath)
            FileOutputStream(outputFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            
            println("模型文件已复制到缓存: ${outputFile.name} (${outputFile.length() / 1024 / 1024}MB)")
            outputFile.absolutePath
        } catch (e: Exception) {
            throw RuntimeException("复制模型文件失败: $assetPath", e)
        }
    }
    
    // OCR识别方法
    fun recognize(bitmap: Bitmap): Array<Array<Any>> {
        val results = ocr?.ocr(bitmap) ?: return emptyArray()
        
        return results.map { result ->
            arrayOf(
                result.box.toTypedArray(),
                result.text,
                result.confidence
            )
        }.toTypedArray()
    }
    
    fun recognizeFile(imagePath: String): Array<Array<Any>> {
        val bitmap = BitmapFactory.decodeFile(imagePath)
        return if (bitmap != null) {
            val results = recognize(bitmap)
            bitmap.recycle()
            results
        } else {
            console.error("无法加载图片文件: $imagePath")
            emptyArray()
        }
    }
    
    fun recognizeBase64(base64Data: String): Array<Array<Any>> {
        return try {
            val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap != null) {
                val results = recognize(bitmap)
                bitmap.recycle()
                results
            } else {
                console.error("Base64数据解码失败")
                emptyArray()
            }
        } catch (e: Exception) {
            console.error("Base64识别失败: ${e.message}")
            emptyArray()
        }
    }
    
    // 获取OCR状态信息
    fun getStatus(): String {
        return if (ocr != null) {
            "OCR已初始化，词汇表大小: ${ocr!!.getVocabSize()}"
        } else {
            "OCR未初始化"
        }
    }
    
    // 清理缓存（可选）
    fun clearCache(): Boolean {
        return try {
            val cacheDir = context!!.cacheDir
            val modelFiles = cacheDir.listFiles { file -> 
                file.name.startsWith("ocr_") && file.name.endsWith(".onnx")
            }
            
            var deletedCount = 0
            modelFiles?.forEach { 
                if (it.delete()) {
                    deletedCount++
                }
            }
            
            println("已清理 $deletedCount 个模型缓存文件")
            true
        } catch (e: Exception) {
            console.error("清理缓存失败: ${e.message}")
            false
        }
    }
    
    // 释放资源
    fun release() {
        ocr?.close()
        ocr = null
        println("OCR资源已释放")
    }
}
