// autojs/src/main/java/com/stardust/autojs/ocr/OcrInterface.kt
package com.stardust.autojs.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

class RapidOCRInterface {
    private var rapidOCR: RapidOCR? = null
    private var context: Context? = null
    
    fun setContext(context: Context) {
        this.context = context
    }
    
    // 初始化 - 只需要模型文件，不需要词汇表文件
    fun init(): Boolean {
        return try {
            if (context == null) {
                throw IllegalStateException("Context not set. Call setContext() first.")
            }
            
            val cacheDir = context!!.cacheDir
            val detModelPath = copyAssetToCache("models/det.onnx", cacheDir)
            val recModelPath = copyAssetToCache("models/rec.onnx", cacheDir)
            
            rapidOCR = RapidOCR(detModelPath, recModelPath)
            
            println("OCR初始化成功，词汇表大小: ${rapidOCR!!.getVocabSize()}")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun copyAssetToCache(assetPath: String, cacheDir: File): String {
        return try {
            val inputStream = context!!.assets.open(assetPath)
            val outputFile = File(cacheDir, File(assetPath).name)
            
            FileOutputStream(outputFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            
            outputFile.absolutePath
        } catch (e: Exception) {
            throw RuntimeException("复制模型文件失败: $assetPath", e)
        }
    }
    
    fun recognize(bitmap: Bitmap): Array<Array<Any>> {
        val results = rapidOCR?.ocr(bitmap) ?: return emptyArray()
        
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
        rapidOCR?.close()
    }
}
