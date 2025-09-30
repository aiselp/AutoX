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
     * 处理 YOLOv8 输出
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
        
        val numClasses = classNames.size
        val boxDim = 4 + numClasses
        
        // 验证输出格式
        if (outputTensor.size % boxDim != 0) {
            Log.e("YoloV8PostProcessor", "输出格式不匹配: ${outputTensor.size} % $boxDim != 0")
            return emptyList()
        }
        
        val numBoxes = outputTensor.size / boxDim
        Log.d("YoloV8PostProcessor", "检测框数量: $numBoxes, 维度: $boxDim")

        val boxes = mutableListOf<DetectionBox>()
        var validBoxCount = 0

        for (i in 0 until numBoxes) {
            val offset = i * boxDim
            
            try {
                // 解析边界框 [x_center, y_center, width, height]
                val xCenter = outputTensor[offset]
                val yCenter = outputTensor[offset + 1]
                val width = outputTensor[offset + 2]
                val height = outputTensor[offset + 3]
                
                Log.d("YoloV8PostProcessor", "框 $i: center=($xCenter, $yCenter), size=($width, $height)")
                
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
                
                Log.d("YoloV8PostProcessor", "框 $i: 最大分数=$maxClassScore, 类别ID=$maxClassId")
                
                // 应用sigmoid获取置信度
                val confidence = sigmoid(maxClassScore)
                Log.d("YoloV8PostProcessor", "框 $i: 置信度=$confidence, 阈值=$confThreshold")
                
                if (confidence < confThreshold) {
                    continue
                }
                
                validBoxCount++
                
                // 转换为中心坐标到角点坐标
                val x1 = xCenter - width / 2
                val y1 = yCenter - height / 2
                val x2 = xCenter + width / 2
                val y2 = yCenter + height / 2
                
                Log.d("YoloV8PostProcessor", "框 $i: 角点坐标=($x1, $y1, $x2, $y2)")
                
                // 裁剪到图像范围内
                val clampedX1 = max(0f, min(x1, inputWidth.toFloat()))
                val clampedY1 = max(0f, min(y1, inputHeight.toFloat()))
                val clampedX2 = max(0f, min(x2, inputWidth.toFloat()))
                val clampedY2 = max(0f, min(y2, inputHeight.toFloat()))
                
                // 检查框是否有效
                val boxWidth = clampedX2 - clampedX1
                val boxHeight = clampedY2 - clampedY1
                if (boxWidth <= 0 || boxHeight <= 0) {
                    Log.d("YoloV8PostProcessor", "框 $i: 无效尺寸")
                    continue
                }
                
                if (validBoxCount <= 3) {
                    Log.d("YoloV8PostProcessor", "有效框$validBoxCount: class=${classNames.getOrElse(maxClassId){"unknown"}}, conf=$confidence, box=[$clampedX1, $clampedY1, $clampedX2, $clampedY2]")
                }
                
                boxes.add(DetectionBox(clampedX1, clampedY1, clampedX2, clampedY2, confidence, maxClassId))
                
            } catch (e: Exception) {
                Log.e("YoloV8PostProcessor", "解析框 $i 时出错: ${e.message}")
                continue
            }
        }

        Log.d("YoloV8PostProcessor", "有效检测框数量: ${boxes.size}")

        // 应用NMS
        val finalBoxes = nonMaxSuppression(boxes, iouThreshold)
        
        Log.d("YoloV8PostProcessor", "NMS后剩余框数量: ${finalBoxes.size}")

        // 转换为 DetectionResult
        val results = finalBoxes.map { box ->
            val label = classNames.getOrElse(box.classId) { "class_${box.classId}" }
            Log.d("YoloV8PostProcessor", "最终结果: $label - ${box.confidence}")
            OnnxDetector.DetectionResult(
                label = label,
                score = box.confidence,
                box = floatArrayOf(box.x1, box.y1, box.x2, box.y2)
            )
        }
        
        Log.d("YoloV8PostProcessor", "处理完成，返回 ${results.size} 个结果")
        return results
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
                } else {
                    Log.d("YoloV8PostProcessor", "NMS移除重叠框: IoU=$iou")
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
