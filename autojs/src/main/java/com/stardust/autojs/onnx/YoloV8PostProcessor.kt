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
        
        Log.d("YoloV8PostProcessor", "开始处理YOLOv8输出，长度: ${outputTensor.size}")
        Log.d("YoloV8PostProcessor", "输入尺寸: ${inputWidth}x${inputHeight}, 类别数: ${classNames.size}")
        Log.d("YoloV8PostProcessor", "类别名称: $classNames")
        
        // 分析输出形状
        val boxes = analyzeAndParseOutput(outputTensor, inputWidth, inputHeight, classNames.size, confThreshold)
        
        Log.d("YoloV8PostProcessor", "解析到的有效框数量: ${boxes.size}")

        // 应用NMS
        val finalBoxes = nonMaxSuppression(boxes, iouThreshold)
        
        Log.d("YoloV8PostProcessor", "NMS后剩余框数量: ${finalBoxes.size}")

        // 转换为 DetectionResult
        return finalBoxes.map { box ->
            val label = classNames.getOrElse(box.classId) { "class_${box.classId}" }
            Log.d("YoloV8PostProcessor", "最终结果: $label - ${"%.3f".format(box.confidence)}")
            OnnxDetector.DetectionResult(
                label = label,
                score = box.confidence,
                box = floatArrayOf(box.x1, box.y1, box.x2, box.y2)
            )
        }
    }

    /**
     * 分析并解析输出
     */
    private fun analyzeAndParseOutput(
        outputTensor: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        numClasses: Int,
        confThreshold: Float
    ): List<DetectionBox> {
        
        val totalElements = outputTensor.size
        Log.d("YoloV8PostProcessor", "总元素: $totalElements, 类别数: $numClasses")
        
        // YOLOv8 常见输出形状
        // 格式1: [1, 4 + num_classes, num_boxes] - 最常见
        // 格式2: [1, 84, num_boxes] - 80个类别 + 4个坐标
        // 格式3: [num_boxes, 4 + num_classes] - 转置格式
        
        val boxes = mutableListOf<DetectionBox>()
        
        // 尝试格式1: [1, 4 + num_classes, num_boxes]
        val expectedDim1 = 4 + numClasses
        if (totalElements % expectedDim1 == 0) {
            val numBoxes = totalElements / expectedDim1
            Log.d("YoloV8PostProcessor", "尝试格式1: [1, $expectedDim1, $numBoxes]")
            return parseTransposedFormat(outputTensor, inputWidth, inputHeight, numClasses, confThreshold)
        }
        
        // 尝试格式2: [1, 84, num_boxes] - 固定84维度
        if (totalElements % 84 == 0) {
            val numBoxes = totalElements / 84
            Log.d("YoloV8PostProcessor", "尝试格式2: [1, 84, $numBoxes]")
            return parse84DimFormat(outputTensor, inputWidth, inputHeight, numClasses, confThreshold)
        }
        
        // 尝试格式3: [num_boxes, 4 + num_classes]
        val numBoxes3 = totalElements / (4 + numClasses)
        if (numBoxes3 * (4 + numClasses) == totalElements) {
            Log.d("YoloV8PostProcessor", "尝试格式3: [$numBoxes3, ${4 + numClasses}]")
            return parseStandardFormat(outputTensor, inputWidth, inputHeight, numClasses, confThreshold)
        }
        
        Log.e("YoloV8PostProcessor", "无法识别输出格式")
        return emptyList()
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
        
        Log.d("YoloV8PostProcessor", "解析转置格式，检测框数量: $numBoxes")
        
        for (i in 0 until numBoxes) {
            // 在转置格式中，数据是按列存储的
            val xCenter = outputTensor[i]
            val yCenter = outputTensor[i + numBoxes]
            val width = outputTensor[i + 2 * numBoxes]
            val height = outputTensor[i + 3 * numBoxes]
            
            // 找到最大类别分数
            var maxClassScore = -Float.MAX_VALUE
            var maxClassId = -1
            for (c in 0 until numClasses) {
                val score = outputTensor[i + (4 + c) * numBoxes]
                if (score > maxClassScore) {
                    maxClassScore = score
                    maxClassId = c
                }
            }
            
            // 关键修复：确保类别ID在有效范围内
            if (maxClassId < 0 || maxClassId >= numClasses) {
                Log.w("YoloV8PostProcessor", "无效的类别ID: $maxClassId, 最大类别数: $numClasses")
                continue
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
     * 解析84维度格式: [1, 84, num_boxes] - YOLOv8标准格式
     */
    private fun parse84DimFormat(
        outputTensor: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        numClasses: Int,
        confThreshold: Float
    ): List<DetectionBox> {
        val boxes = mutableListOf<DetectionBox>()
        val numBoxes = outputTensor.size / 84
        
        Log.d("YoloV8PostProcessor", "解析84维格式，检测框数量: $numBoxes")
        
        for (i in 0 until numBoxes) {
            val offset = i * 84
            
            // 解析边界框 [x_center, y_center, width, height]
            val xCenter = outputTensor[offset]
            val yCenter = outputTensor[offset + 1]
            val width = outputTensor[offset + 2]
            val height = outputTensor[offset + 3]
            
            // 找到最大类别分数
            var maxClassScore = -Float.MAX_VALUE
            var maxClassId = -1
            for (c in 0 until numClasses) {
                val score = outputTensor[offset + 4 + c]
                if (score > maxClassScore) {
                    maxClassScore = score
                    maxClassId = c
                }
            }
            
            // 关键修复：确保类别ID在有效范围内
            if (maxClassId < 0 || maxClassId >= numClasses) {
                Log.w("YoloV8PostProcessor", "无效的类别ID: $maxClassId, 最大类别数: $numClasses")
                continue
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
        
        Log.d("YoloV8PostProcessor", "解析标准格式，检测框数量: $numBoxes, 类别数: $numClasses")
        
        for (i in 0 until numBoxes) {
            val offset = i * boxDim
            
            // 解析边界框 [x_center, y_center, width, height]
            val xCenter = outputTensor[offset]
            val yCenter = outputTensor[offset + 1]
            val width = outputTensor[offset + 2]
            val height = outputTensor[offset + 3]
            
            // 找到最大类别分数和对应的类别ID
            var maxClassScore = -Float.MAX_VALUE
            var maxClassId = -1
            for (c in 0 until numClasses) {
                val score = outputTensor[offset + 4 + c]
                if (score > maxClassScore) {
                    maxClassScore = score
                    maxClassId = c
                }
            }
            
            // 关键修复：确保类别ID在有效范围内
            if (maxClassId < 0 || maxClassId >= numClasses) {
                Log.w("YoloV8PostProcessor", "无效的类别ID: $maxClassId, 最大类别数: $numClasses")
                continue
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
            
            if (boxes.size < 3) {
                Log.d("YoloV8PostProcessor", "有效框${boxes.size + 1}: classId=$maxClassId, conf=${"%.3f".format(confidence)}, box=[${"%.1f".format(clampedX1)}, ${"%.1f".format(clampedY1)}, ${"%.1f".format(clampedX2)}, ${"%.1f".format(clampedY2)}]")
            }
            
            boxes.add(DetectionBox(clampedX1, clampedY1, clampedX2, clampedY2, confidence, maxClassId))
        }
        
        return boxes
    }

    /**
     * Sigmoid函数
     */
    private fun sigmoid(x: Float): Float {
        return (1.0f / (1.0f + exp(-x)))
    }

    private fun nonMaxSuppression(boxes: List<DetectionBox>, iouThreshold: Float): List<DetectionBox> {
        if (boxes.isEmpty()) return emptyList()
        
        Log.d("YoloV8PostProcessor", "开始NMS，输入框数量: ${boxes.size}")
        
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
        
        Log.d("YoloV8PostProcessor", "NMS完成，输出框数量: ${selected.size}")
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
