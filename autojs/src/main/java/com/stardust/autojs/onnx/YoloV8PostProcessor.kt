// autojs/src/main/java/com/stardust/autojs/onnx/YoloV8PostProcessor.kt
package com.stardust.autojs.onnx

import android.util.Log
import kotlin.math.max
import kotlin.math.min

object YoloV8PostProcessor {

    // 默认 COCO 80 类别（YOLOv8 默认）
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
     * 处理 YOLOv8 ONNX 模型的原始输出 - 修复版本
     */
    fun process(
        outputTensor: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        classNames: List<String>,
        confThreshold: Float = 0.25f,
        iouThreshold: Float = 0.45f
    ): List<OnnxDetector.DetectionResult> {
        
        val numClasses = classNames.size
        val totalElements = outputTensor.size
        
        Log.d("YoloV8PostProcessor", "开始处理输出: totalElements=$totalElements, numClasses=$numClasses")
        
        // 修复：YOLOv8输出格式应该是 [batch, 84, 8400] 转置后的格式
        val (boxDim, numBoxes) = detectYOLOv8OutputFormat(outputTensor, numClasses, totalElements)
        
        Log.d("YoloV8PostProcessor", "检测到格式: boxDim=$boxDim, numBoxes=$numBoxes")

        val boxes = mutableListOf<DetectionBox>()

        // 解析每个检测框
        for (i in 0 until numBoxes) {
            val box = parseYOLOv8DetectionBox(outputTensor, i, boxDim, numClasses, inputWidth, inputHeight, confThreshold)
            box?.let { 
                boxes.add(it)
                if (boxes.size <= 3) { // 只记录前几个用于调试
                    Log.d("YoloV8PostProcessor", "检测到框: class=${it.classId}, conf=${it.confidence}, box=[${it.x1}, ${it.y1}, ${it.x2}, ${it.y2}]")
                }
            }
        }

        Log.d("YoloV8PostProcessor", "过滤后候选框数量: ${boxes.size}")

        // 非极大值抑制 (NMS)
        val finalBoxes = nonMaxSuppression(boxes, iouThreshold)
        
        Log.d("YoloV8PostProcessor", "NMS后最终框数量: ${finalBoxes.size}")

        return finalBoxes.map { box ->
            OnnxDetector.DetectionResult(
                label = if (box.classId < classNames.size) classNames[box.classId] else "unknown_${box.classId}",
                score = box.confidence,
                box = floatArrayOf(box.x1, box.y1, box.x2, box.y2)
            )
        }
    }

    /**
     * 专门检测YOLOv8输出格式
     */
    private fun detectYOLOv8OutputFormat(outputTensor: FloatArray, numClasses: Int, totalElements: Int): Pair<Int, Int> {
        // YOLOv8 标准输出格式: [x, y, w, h] + 类别分数
        val expectedDim = 4 + numClasses
        
        if (totalElements % expectedDim == 0) {
            val numBoxes = totalElements / expectedDim
            Log.d("YoloV8PostProcessor", "使用YOLOv8标准格式: dim=$expectedDim, numBoxes=$numBoxes")
            return Pair(expectedDim, numBoxes)
        }
        
        // 尝试其他可能的格式
        val alternativeDims = listOf(5 + numClasses, 6 + numClasses, 85) // COCO 80类 + 5
        for (dim in alternativeDims) {
            if (totalElements % dim == 0) {
                val numBoxes = totalElements / dim
                Log.w("YoloV8PostProcessor", "使用备选格式: dim=$dim, numBoxes=$numBoxes")
                return Pair(dim, numBoxes)
            }
        }
        
        throw IllegalArgumentException("无法确定YOLOv8输出格式。totalElements=$totalElements, numClasses=$numClasses")
    }

    /**
     * 解析YOLOv8检测框
     */
    private fun parseYOLOv8DetectionBox(
        outputTensor: FloatArray,
        boxIndex: Int,
        boxDim: Int,
        numClasses: Int,
        inputWidth: Int,
        inputHeight: Int,
        confThreshold: Float
    ): DetectionBox? {
        val offset = boxIndex * boxDim
        
        // YOLOv8格式: [x_center, y_center, width, height, class_score_0, class_score_1, ...]
        val xCenter = outputTensor[offset]
        val yCenter = outputTensor[offset + 1]
        val width = outputTensor[offset + 2]
        val height = outputTensor[offset + 3]
        
        // 找到最高类别置信度
        var maxClassConf = 0f
        var maxClassId = 0
        for (c in 0 until numClasses) {
            val classScore = outputTensor[offset + 4 + c]
            if (classScore > maxClassConf) {
                maxClassConf = classScore
                maxClassId = c
            }
        }

        // 调试日志
        if (boxIndex < 5) {
            Log.d("YoloV8PostProcessor", "框$boxIndex: center=($xCenter,$yCenter), size=($width,$height), class=$maxClassId, conf=$maxClassConf")
        }

        if (maxClassConf < confThreshold) return null

        // 转换为边界框坐标 [x1, y1, x2, y2]
        val x1 = (xCenter - width / 2) 
        val y1 = (yCenter - height / 2)
        val x2 = (xCenter + width / 2)
        val y2 = (yCenter + height / 2)

        return DetectionBox(x1, y1, x2, y2, maxClassConf, maxClassId)
    }

    private fun nonMaxSuppression(boxes: List<DetectionBox>, iouThreshold: Float): List<DetectionBox> {
        if (boxes.isEmpty()) return emptyList()
        
        val sortedBoxes = boxes.sortedByDescending { it.confidence }
        val keep = mutableListOf<DetectionBox>()
        val suppressed = BooleanArray(sortedBoxes.size)

        for (i in sortedBoxes.indices) {
            if (suppressed[i]) continue
            keep.add(sortedBoxes[i])
            for (j in i + 1 until sortedBoxes.size) {
                if (suppressed[j]) continue
                val iou = computeIoU(sortedBoxes[i], sortedBoxes[j])
                if (iou > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return keep
    }

    private fun computeIoU(a: DetectionBox, b: DetectionBox): Float {
        val x1 = max(a.x1, b.x1)
        val y1 = max(a.y1, b.y1)
        val x2 = min(a.x2, b.x2)
        val y2 = min(a.y2, b.y2)

        if (x2 <= x1 || y2 <= y1) return 0f

        val interArea = (x2 - x1) * (y2 - y1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        val unionArea = areaA + areaB - interArea
        
        if (unionArea <= 0f) return 0f
        
        return interArea / unionArea
    }
}
