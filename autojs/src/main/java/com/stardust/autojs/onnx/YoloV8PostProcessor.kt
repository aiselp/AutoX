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
     * 处理 YOLOv8 ONNX 模型的原始输出 - 自适应版本
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
        
        // 自动检测输出格式
        val (boxDim, numBoxes) = detectOutputFormat(outputTensor, numClasses, totalElements)
        
        Log.d("YoloV8PostProcessor", "检测到格式: boxDim=$boxDim, numBoxes=$numBoxes")

        val boxes = mutableListOf<DetectionBox>()

        // 解析每个检测框
        for (i in 0 until numBoxes) {
            val box = parseDetectionBox(outputTensor, i, boxDim, numClasses, inputWidth, inputHeight, confThreshold)
            box?.let { boxes.add(it) }
        }

        Log.d("YoloV8PostProcessor", "过滤后候选框数量: ${boxes.size}")

        // 非极大值抑制 (NMS)
        val finalBoxes = nonMaxSuppression(boxes, iouThreshold)
        
        Log.d("YoloV8PostProcessor", "NMS后最终框数量: ${finalBoxes.size}")

        return finalBoxes.map { box ->
            OnnxDetector.DetectionResult(
                label = if (box.classId < classNames.size) classNames[box.classId] else "unknown_${box.classId}",
                score = box.confidence,
                box = floatArrayOf(
                    max(0f, box.x1),
                    max(0f, box.y1),
                    min(inputWidth.toFloat(), box.x2),
                    min(inputHeight.toFloat(), box.y2)
                )
            )
        }
    }

    /**
     * 自动检测输出格式
     */
    private fun detectOutputFormat(outputTensor: FloatArray, numClasses: Int, totalElements: Int): Pair<Int, Int> {
        Log.d("YoloV8PostProcessor", "检测输出格式: totalElements=$totalElements, numClasses=$numClasses")
        
        // 直接计算可能的维度
        val candidates = listOf(
            4 + numClasses,  // YOLOv8标准格式
            5 + numClasses,  // 带obj_conf格式  
            4 + 1 + numClasses // 旧版YOLO格式
        )
        
        for (dim in candidates) {
            if (totalElements % dim == 0) {
                val numBoxes = totalElements / dim
                Log.d("YoloV8PostProcessor", "找到匹配格式: dim=$dim, numBoxes=$numBoxes")
                return Pair(dim, numBoxes)
            }
        }
        
        // 如果标准格式都不匹配，强制使用YOLOv8格式
        val forcedDim = 4 + numClasses
        val numBoxes = totalElements / forcedDim
        if (numBoxes * forcedDim == totalElements) {
            Log.w("YoloV8PostProcessor", "强制使用YOLOv8格式: dim=$forcedDim, numBoxes=$numBoxes")
            return Pair(forcedDim, numBoxes)
        }
        
        throw IllegalArgumentException("无法确定输出格式。totalElements=$totalElements, numClasses=$numClasses. " +
                "尝试维度: ${candidates.joinToString()}")
    }

    /**
     * 解析单个检测框
     */
    private fun parseDetectionBox(
        outputTensor: FloatArray,
        boxIndex: Int,
        boxDim: Int,
        numClasses: Int,
        inputWidth: Int,
        inputHeight: Int,
        confThreshold: Float
    ): DetectionBox? {
        val offset = boxIndex * boxDim
        
        // 根据不同的格式解析
        return when (boxDim) {
            // YOLOv8 标准格式: [x, y, w, h, class_scores...]
            4 + numClasses -> parseYOLOv8Format(outputTensor, offset, numClasses, inputWidth, inputHeight, confThreshold)
            // 带obj_conf的格式: [x, y, w, h, obj_conf, class_scores...]
            5 + numClasses -> parseWithObjConfFormat(outputTensor, offset, numClasses, inputWidth, inputHeight, confThreshold)
            // 旧版YOLO格式: [x, y, w, h, obj_conf, class_scores...] 但处理方式不同
            4 + 1 + numClasses -> parseLegacyYOLOFormat(outputTensor, offset, numClasses, inputWidth, inputHeight, confThreshold)
            // 通用格式处理
            else -> parseGenericFormat(outputTensor, offset, boxDim, numClasses, inputWidth, inputHeight, confThreshold)
        }
    }

    private fun parseYOLOv8Format(
        outputTensor: FloatArray,
        offset: Int,
        numClasses: Int,
        inputWidth: Int,
        inputHeight: Int,
        confThreshold: Float
    ): DetectionBox? {
        val x = outputTensor[offset]
        val y = outputTensor[offset + 1]
        val w = outputTensor[offset + 2]
        val h = outputTensor[offset + 3]
        
        // 找到最高类别置信度
        var maxClassConf = 0f
        var maxClassId = 0
        for (c in 0 until numClasses) {
            val clsConf = outputTensor[offset + 4 + c]
            if (clsConf > maxClassConf) {
                maxClassConf = clsConf
                maxClassId = c
            }
        }

        if (maxClassConf < confThreshold) return null

        return createBox(x, y, w, h, maxClassConf, maxClassId, inputWidth, inputHeight)
    }

    private fun parseWithObjConfFormat(
        outputTensor: FloatArray,
        offset: Int,
        numClasses: Int,
        inputWidth: Int,
        inputHeight: Int,
        confThreshold: Float
    ): DetectionBox? {
        val x = outputTensor[offset]
        val y = outputTensor[offset + 1]
        val w = outputTensor[offset + 2]
        val h = outputTensor[offset + 3]
        val objConf = outputTensor[offset + 4]
        
        // 找到最高类别置信度
        var maxClassConf = 0f
        var maxClassId = 0
        for (c in 0 until numClasses) {
            val clsConf = outputTensor[offset + 5 + c]
            val totalConf = objConf * clsConf
            if (totalConf > maxClassConf) {
                maxClassConf = totalConf
                maxClassId = c
            }
        }

        if (maxClassConf < confThreshold) return null

        return createBox(x, y, w, h, maxClassConf, maxClassId, inputWidth, inputHeight)
    }

    private fun parseLegacyYOLOFormat(
        outputTensor: FloatArray,
        offset: Int,
        numClasses: Int,
        inputWidth: Int,
        inputHeight: Int,
        confThreshold: Float
    ): DetectionBox? {
        val x = outputTensor[offset]
        val y = outputTensor[offset + 1]
        val w = outputTensor[offset + 2]
        val h = outputTensor[offset + 3]
        val objConf = outputTensor[offset + 4]

        if (objConf < confThreshold) return null

        // 找到最高类别置信度
        var maxClassConf = 0f
        var maxClassId = 0
        for (c in 0 until numClasses) {
            val clsConf = outputTensor[offset + 5 + c]
            val totalConf = objConf * clsConf
            if (totalConf > maxClassConf) {
                maxClassConf = totalConf
                maxClassId = c
            }
        }

        if (maxClassConf < confThreshold) return null

        return createBox(x, y, w, h, maxClassConf, maxClassId, inputWidth, inputHeight)
    }

    private fun parseGenericFormat(
        outputTensor: FloatArray,
        offset: Int,
        boxDim: Int,
        numClasses: Int,
        inputWidth: Int,
        inputHeight: Int,
        confThreshold: Float
    ): DetectionBox? {
        // 假设前4个是bbox坐标
        val x = outputTensor[offset]
        val y = outputTensor[offset + 1]
        val w = outputTensor[offset + 2]
        val h = outputTensor[offset + 3]
        
        // 在剩余的元素中找到最高的类别分数
        var maxClassConf = 0f
        var maxClassId = 0
        val classStart = boxDim - numClasses
        for (c in 0 until numClasses) {
            if (offset + classStart + c >= outputTensor.size) break
            val clsConf = outputTensor[offset + classStart + c]
            if (clsConf > maxClassConf) {
                maxClassConf = clsConf
                maxClassId = c
            }
        }

        if (maxClassConf < confThreshold) return null

        return createBox(x, y, w, h, maxClassConf, maxClassId, inputWidth, inputHeight)
    }

    private fun createBox(
        x: Float, y: Float, w: Float, h: Float,
        confidence: Float, classId: Int,
        inputWidth: Int, inputHeight: Int
    ): DetectionBox {
        // 转换为像素坐标 [x1, y1, x2, y2]
        val x1 = (x - w / 2f) * inputWidth
        val y1 = (y - h / 2f) * inputHeight
        val x2 = (x + w / 2f) * inputWidth
        val y2 = (y + h / 2f) * inputHeight

        return DetectionBox(x1, y1, x2, y2, confidence, classId)
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
