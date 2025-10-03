// autojs/src/main/java/com/stardust/autojs/ocr/Ocr.kt
package com.stardust.autojs.ocr

import android.graphics.Bitmap

class OCR(
    private val detModelPath: String,
    private val recModelPath: String
) {
    private var detector: DBDetector? = null
    private var recognizer: TextRecognizer? = null
    
    init {
        loadModels()
    }
    
    private fun loadModels() {
        detector = DBDetector(detModelPath)
        recognizer = TextRecognizer(recModelPath)
        println("模型加载完成 - 检测器: ${detector != null}, 识别器: ${recognizer != null}")
    }
    
    fun ocr(bitmap: Bitmap): List<OCRResult> {
        val results = mutableListOf<OCRResult>()
        
        try {
            // 1. 文本检测
            val boxes = detector!!.detect(bitmap)
            println("检测到 ${boxes.size} 个文本区域")
            
            for ((index, box) in boxes.withIndex()) {
                // 2. 文本区域裁剪
                val textBitmap = cropTextBox(bitmap, box)
                
                // 3. 文本识别
                val text = recognizer!!.recognize(textBitmap)
                
                if (text.isNotEmpty()) {
                    results.add(OCRResult(box, text, 0.9f))
                    println("区域 ${index + 1}: $text")
                }
                
                textBitmap.recycle()
            }
        } catch (e: Exception) {
            console.error("OCR处理失败: ${e.message}")
        }
        
        return results
    }
    
    private fun cropTextBox(bitmap: Bitmap, box: FloatArray): Bitmap {
        val x = maxOf(0, box[0].toInt())
        val y = maxOf(0, box[1].toInt())
        val width = minOf(bitmap.width - x, (box[2] - box[0]).toInt())
        val height = minOf(bitmap.height - y, (box[3] - box[1]).toInt())
        
        return if (width > 0 && height > 0) {
            Bitmap.createBitmap(bitmap, x, y, width, height)
        } else {
            // 如果裁剪区域无效，返回原图（避免崩溃）
            bitmap
        }
    }
    
    fun getVocabSize(): Int {
        return recognizer?.getVocabSize() ?: 0
    }
    
    fun close() {
        detector?.close()
        recognizer?.close()
        println("OCR模型已关闭")
    }
    
    data class OCRResult(
        val box: FloatArray,
        val text: String,
        val confidence: Float
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as OCRResult
            if (!box.contentEquals(other.box)) return false
            if (text != other.text) return false
            if (confidence != other.confidence) return false
            return true
        }
        
        override fun hashCode(): Int {
            var result = box.contentHashCode()
            result = 31 * result + text.hashCode()
            result = 31 * result + confidence.hashCode()
            return result
        }
    }
}
