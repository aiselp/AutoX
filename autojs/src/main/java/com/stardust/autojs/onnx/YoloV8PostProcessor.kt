// autojs/src/main/java/com/stardust/autojs/onnx/YoloV8PostProcessor.kt
package com.stardust.autojs.onnx

import android.util.Log
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

object YoloV8PostProcessor {

    val defaultClassNames = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
        "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
        "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
        "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
        "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
        "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
        "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
        "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
        "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    )

    data class DetectionBox(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val confidence: Float,
        val classId: Int
    )

    /**
     * 处理 YOLOv8 输出 - 支持多种输出格式
     */
    fun process(
        outputTensor: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        classNames: List<String>,
        confThreshold: Float = 0.25f,
        iouThreshold: Float = 0.45f
    ): List<OnnxDetector.DetectionResult> {
        
        Log.d("YoloV8PostProcessor", "开始处理YOLOv8输出，长度: ${outputTensor.size}, 输入尺寸: ${inputWidth}x${inputHeight}")
        
        val numClasses = classNames.size
        Log.d("YoloV8PostProcessor", "类别数量: $numClasses")
        
        // 尝试不同的输出格式
        val boxes = try {
            detectOutputFormatAndParse(outputTensor, inputWidth, inputHeight, numClasses, confThreshold)
        } catch (e: Exception) {
            Log.e("YoloV8PostProcessor", "解析输出失败: ${e.message}")
            emptyList()
        }

        Log.d("YoloV8PostProcessor", "解析到的有效框数量: ${boxes.size}")

        // 应用NMS
        val finalBoxes = nonMaxSuppression(boxes, iouThreshold)
        
        Log.d("YoloV8PostProcessor", "NMS后剩余框数量: ${finalBoxes.size}")

        // 转换为 DetectionResult
        return finalBoxes.map { box ->
            OnnxDetector.DetectionResult(
                label = classNames.getOrElse(box.classId) { "class_${box.classId}" },
                score = box.confidence,
                box = floatArrayOf(box.x1, box.y1, box.x2, box.y2)
            )
        }
    }

    /**
     * 检测输出格式并解析
     */
    private fun detectOutputFormatAndParse(
        outputTensor: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        numClasses: Int,
        confThreshold: Float
    ): List<DetectionBox> {
        
        val boxes = mutableListOf<DetectionBox>()
        
        // 格式1: [num_boxes, 4 + num_classes] - 标准YOLOv8格式
        val standardDim = 4 + numClasses
        if (outputTensor.size % standardDim == 0) {
            Log.d("YoloV8PostProcessor", "检测到标准YOLOv8格式，维度: $standardDim")
            return parseStandardFormat(outputTensor, inputWidth, inputHeight, numClasses, confThreshold)
        }
        
        // 格式2: [1, 4 + num_classes, num_boxes] - 转置格式
        val transposedDim = outputTensor.size / (4 + numClasses)
        if (transposedDim > 0 && transposedDim * (4 + numClasses) == outputTensor.size) {
            Log.d("YoloV8PostProcessor", "检测到转置格式，维度: $transposedDim")
            return parseTransposedFormat(outputTensor, inputWidth, inputHeight, numClasses, confThreshold)
        }
        
        // 格式3: 尝试自动探测
        Log.d("YoloV8PostProcessor", "尝试自动探测格式")
        return autoDetectFormat(outputTensor, inputWidth, inputHeight, numClasses, confThreshold)
    }

    /**
     * 解析标准格式: [num_boxes, 4 + num_classes]
     */
    private fun parseStandardFormat(
        outputTensor: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        numClasses: Int,
        confThreshold: Float
    ): List<DetectionBox> {
        val boxes = mutableListOf<DetectionBox>()
        val boxDim = 4 + numClasses
        val numBoxes = outputTensor.size / boxDim
        
        for (i in 0 until numBoxes) {
            val offset = i * boxDim
            
            // 解析边界框 [x_center, y_center, width, height]
            val xCenter = outputTensor[offset]
            val yCenter = outputTensor[offset + 1]
            val width = outputTensor[offset + 2]
            val height = outputTensor[offset + 3]
            
            // 找到最大类别分数
            var maxClassScore = 0f
            var maxClassId = 0
            for (c in 0 until numClasses) {
                val score = outputTensor[offset + 4 + c]
                if (score > maxClassScore) {
                    maxClassScore = score
                    maxClassId = c
                }
            }
            
            val confidence = sigmoid(maxClassScore)
            if (confidence < confThreshold) continue
            
            // 转换坐标
            val x1 = xCenter - width / 2
            val y1 = yCenter - height / 2
            val x2 = xCenter + width / 2
            val y2 = yCenter + height / 2
            
            // 裁剪到图像范围内
            val clampedX1 = max(0f, min(x1, inputWidth.toFloat()))
            val clampedY1 = max(0f, min(y1, inputHeight.toFloat()))
            val clampedX2 = max(0f, min(x2, inputWidth.toFloat()))
            val clampedY2 = max(0f, min(y2, inputHeight.toFloat()))
            
            // 检查框是否有效
            val boxWidth = clampedX2 - clampedX1
            val boxHeight = clampedY2 - clampedY1
            if (boxWidth <= 0 || boxHeight <= 0) continue
            
            boxes.add(DetectionBox(clampedX1, clampedY1, clampedX2, clampedY2, confidence, maxClassId))
        }
        
        return boxes
    }

    /**
     * 解析转置格式: [1, 4 + num_classes, num_boxes]
     */
    private fun parseTransposedFormat(
        outputTensor: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        numClasses: Int,
        confThreshold: Float
    ): List<DetectionBox> {
        val boxes = mutableListOf<DetectionBox>()
        val numBoxes = outputTensor.size / (4 + numClasses)
        
        for (i in 0 until numBoxes) {
            // 解析边界框
            val xCenter = outputTensor[i]
            val yCenter = outputTensor[i + numBoxes]
            val width = outputTensor[i + 2 * numBoxes]
            val height = outputTensor[i + 3 * numBoxes]
            
            // 找到最大类别分数
            var maxClassScore = 0f
            var maxClassId = 0
            for (c in 0 until numClasses) {
                val score = outputTensor[i + (4 + c) * numBoxes]
                if (score > maxClassScore) {
                    maxClassScore = score
                    maxClassId = c
                }
            }
            
            val confidence = sigmoid(maxClassScore)
            if (confidence < confThreshold) continue
            
            // 转换坐标
            val x1 = xCenter - width / 2
            val y1 = yCenter - height / 2
            val x2 = xCenter + width / 2
            val y2 = yCenter + height / 2
            
            // 裁剪到图像范围内
            val clampedX1 = max(0f, min(x1, inputWidth.toFloat()))
            val clampedY1 = max(0f, min(y1, inputHeight.toFloat()))
            val clampedX2 = max(0f, min(x2, inputWidth.toFloat()))
            val clampedY2 = max(0f, min(y2, inputHeight.toFloat()))
            
            boxes.add(DetectionBox(clampedX1, clampedY1, clampedX2, clampedY2, confidence, maxClassId))
        }
        
        return boxes
    }

    /**
     * 自动探测格式
     */
    private fun autoDetectFormat(
        outputTensor: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        numClasses: Int,
        confThreshold: Float
    ): List<DetectionBox> {
        val boxes = mutableListOf<DetectionBox>()
        
        // 尝试探测输出形状
        val totalElements = outputTensor.size
        Log.d("YoloV8PostProcessor", "自动探测格式，总元素: $totalElements")
        
        // 常见的输出形状
        val possibleShapes = listOf(
            intArrayOf(1, 4 + numClasses, 8400),  // YOLOv8常见形状
            intArrayOf(8400, 4 + numClasses),     // 标准形状
            intArrayOf(1, 4 + numClasses, 25200), // 高分辨率
            intArrayOf(25200, 4 + numClasses)     // 高分辨率标准形状
        )
        
        for (shape in possibleShapes) {
            val expectedSize = shape[0] * shape[1] * shape[2]
            if (totalElements == expectedSize) {
                Log.d("YoloV8PostProcessor", "探测到可能形状: ${shape.contentToString()}")
                // 根据形状调用相应的解析方法
                if (shape[0] == 1 && shape[1] == 4 + numClasses) {
                    return parseTransposedFormat(outputTensor, inputWidth, inputHeight, numClasses, confThreshold)
                } else if (shape[1] == 4 + numClasses) {
                    return parseStandardFormat(outputTensor, inputWidth, inputHeight, numClasses, confThreshold)
                }
            }
        }
        
        Log.w("YoloV8PostProcessor", "无法自动探测输出格式")
        return emptyList()
    }

    /**
     * Sigmoid函数
     */
    private fun sigmoid(x: Float): Float {
        return (1.0f / (1.0f + exp(-x)))
    }

    private fun nonMaxSuppression(boxes: List<DetectionBox>, iouThreshold: Float): List<DetectionBox> {
        if (boxes.isEmpty()) return emptyList()
        
        val sortedBoxes = boxes.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<DetectionBox>()
        
        while (sortedBoxes.isNotEmpty()) {
            val current = sortedBoxes.removeAt(0)
            selected.add(current)
            
            val remaining = mutableListOf<DetectionBox>()
            for (box in sortedBoxes) {
                val iou = calculateIoU(current, box)
                if (iou < iouThreshold) {
                    remaining.add(box)
                }
            }
            sortedBoxes.clear()
            sortedBoxes.addAll(remaining)
        }
        
        return selected
    }

    private fun calculateIoU(box1: DetectionBox, box2: DetectionBox): Float {
        val x1 = max(box1.x1, box2.x1)
        val y1 = max(box1.y1, box2.y1)
        val x2 = min(box1.x2, box2.x2)
        val y2 = min(box1.y2, box2.y2)
        
        val intersection = max(0f, x2 - x1) * max(0f, y2 - y1)
        val area1 = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val area2 = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
        val union = area1 + area2 - intersection
        
        return if (union > 0) intersection / union else 0f
    }
}
