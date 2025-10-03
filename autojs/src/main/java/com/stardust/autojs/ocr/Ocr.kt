// autojs/src/main/java/com/stardust/autojs/ocr/Ocr.kt
package com.stardust.autojs.ocr

import android.graphics.Bitmap

class RapidOCR(
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
    }
    
    fun ocr(bitmap: Bitmap): List<OCRResult> {
        val results = mutableListOf<OCRResult>()
        
        try {
            // 1. 文本检测
            val boxes = detector!!.detect(bitmap)
            
            for (box in boxes) {
                // 2. 文本区域裁剪
                val textBitmap = cropTextBox(bitmap, box)
                
                // 3. 文本识别
                val text = recognizer!!.recognize(textBitmap)
                
                if (text.isNotEmpty()) {
                    results.add(OCRResult(box, text, 0.9f))
                }
                
                textBitmap.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            bitmap
        }
    }
    
    fun getVocabSize(): Int {
        return recognizer?.getVocabSize() ?: 0
    }
    
    fun close() {
        detector?.close()
        recognizer?.close()
    }
    
    data class OCRResult(
        val box: FloatArray,
        val text: String,
        val confidence: Float
    )
}
