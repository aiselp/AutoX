// autojs/src/main/java/com/stardust/autojs/ocr/Rec.kt
package com.stardust.autojs.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap

class TextRecognizer(private val modelPath: String) {
    private var session: OrtSession? = null
    private var environment: OrtEnvironment? = null
    private var vocab: List<String> = listOf()
    
    private val inputSize = intArrayOf(1, 3, 48, 320)
    private val mean = floatArrayOf(0.5f, 0.5f, 0.5f)
    private val std = floatArrayOf(0.5f, 0.5f, 0.5f)
    
    init {
        try {
            environment = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPTS)
            session = environment!!.createSession(modelPath, sessionOptions)
            
            // 从模型元数据读取词汇表
            loadVocabFromMetadata()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadVocabFromMetadata() {
        try {
            val metadata = session!!.metadata
            val customMetadata = metadata.customMetadata
            
            // 从character字段读取词汇表
            val vocabStr = customMetadata["character"] ?: ""
            
            if (vocabStr.isNotEmpty()) {
                // 按换行符分割词汇表
                vocab = vocabStr.split('\n', '\r')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                println("从模型元数据加载词汇表成功，大小: ${vocab.size}")
            } else {
                throw RuntimeException("模型元数据中没有找到词汇表")
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            // 使用精简备选词汇表
            vocab = getFallbackVocab()
            println("使用备选词汇表，大小: ${vocab.size}")
        }
    }
    
    private fun getFallbackVocab(): List<String> {
        // 极简备选词汇表
        return listOf("blank") + 
               ('!'..'~').map { it.toString() } +
               listOf("的", "一", "是", "在", "不", "了", "有", "和", "人", "这")
    }
    
    fun getVocab(): List<String> = vocab
    fun getVocabSize(): Int = vocab.size
    
    fun recognize(bitmap: Bitmap): String {
        val input = preprocess(bitmap)
        return runRecognition(input)
    }
    
    private fun preprocess(bitmap: Bitmap): FloatArray {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize[3], inputSize[2], true)
        val inputData = FloatArray(inputSize[1] * inputSize[2] * inputSize[3])
        
        val intValues = IntArray(resizedBitmap.width * resizedBitmap.height)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, 
                               resizedBitmap.width, resizedBitmap.height)
        
        var pixel = 0
        for (y in 0 until resizedBitmap.height) {
            for (x in 0 until resizedBitmap.width) {
                val value = intValues[pixel++]
                
                inputData[y * resizedBitmap.width + x] = 
                    ((value and 0xFF) / 255.0f - mean[2]) / std[2]
                inputData[resizedBitmap.width * resizedBitmap.height + y * resizedBitmap.width + x] = 
                    ((value shr 8 and 0xFF) / 255.0f - mean[1]) / std[1]
                inputData[2 * resizedBitmap.width * resizedBitmap.height + y * resizedBitmap.width + x] = 
                    ((value shr 16 and 0xFF) / 255.0f - mean[0]) / std[0]
            }
        }
        
        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle()
        }
        
        return inputData
    }
    
    private fun runRecognition(inputData: FloatArray): String {
        val inputName = session!!.inputNames.iterator().next()
        
        val inputTensor = OnnxTensor.createTensor(
            environment!!,
            inputData,
            longArrayOf(inputSize[0], inputSize[1].toLong(), inputSize[2].toLong(), inputSize[3].toLong())
        )
        
        val results = session!!.run(mapOf(inputName to inputTensor))
        val outputTensor = results[0].value as Array<Array<FloatArray>>
        
        val sequence = outputTensor[0]
        val text = decodeText(sequence)
        
        inputTensor.close()
        results.close()
        
        return text
    }
    
    private fun decodeText(sequence: Array<FloatArray>): String {
        val text = StringBuilder()
        var lastIndex = -1
        
        for (frame in sequence) {
            val maxIndex = frame.indices.maxByOrNull { frame[it] } ?: 0
            
            if (maxIndex != 0 && maxIndex != lastIndex) {
                if (maxIndex < vocab.size) {
                    text.append(vocab[maxIndex])
                }
            }
            lastIndex = maxIndex
        }
        
        return text.toString()
    }
    
    fun close() {
        session?.close()
        environment?.close()
    }
}
