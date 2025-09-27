package com.stardust.autojs.onnx

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
     * 处理 YOLOv8 ONNX 模型的原始输出
     *
     * @param outputTensor 模型输出的 FloatArray，形状应为 [numOutputs]，通常是 [84, 8400] 展平后
     * @param inputWidth 模型输入宽度（如 640）
     * @param inputHeight 模型输入高度（如 640）
     * @param classNames 类别名称列表
     * @param confThreshold 置信度阈值（默认 0.25）
     * @param iouThreshold NMS 的 IoU 阈值（默认 0.45）
     * @return 检测结果列表
     */
    fun process(
        outputTensor: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        classNames: List<String>,
        confThreshold: Float = 0.25f,
        iouThreshold: Float = 0.45f
    ): List<OnnxDetector.DetectionResult> {
        // YOLOv8 输出形状: [1, 84, 8400] → 展平为 84 * 8400 = 705600
        // 其中 84 = 4 (box) + 1 (obj) + 80 (classes)
        val numOutputs = outputTensor.size
        val numClasses = classNames.size
        val boxDim = 4 + 1 + numClasses // 85 for COCO
        val numBoxes = numOutputs / boxDim

        if (numBoxes * boxDim != numOutputs) {
            throw IllegalArgumentException("Output tensor size $numOutputs is not divisible by $boxDim")
        }

        val boxes = mutableListOf<DetectionBox>()

        // 转置：从 [84, 8400] → 按每个 box 解析
        for (i in 0 until numBoxes) {
            val offset = i
            val x = outputTensor[0 * numBoxes + offset]
            val y = outputTensor[1 * numBoxes + offset]
            val w = outputTensor[2 * numBoxes + offset]
            val h = outputTensor[3 * numBoxes + offset]
            val objConf = outputTensor[4 * numBoxes + offset]

            if (objConf < confThreshold) continue

            // 找到最高类别置信度
            var maxClassConf = 0f
            var maxClassId = 0
            for (c in 0 until numClasses) {
                val clsConf = outputTensor[(5 + c) * numBoxes + offset]
                val totalConf = objConf * clsConf
                if (totalConf > maxClassConf) {
                    maxClassConf = totalConf
                    maxClassId = c
                }
            }

            if (maxClassConf < confThreshold) continue

            // 转换为 [x1, y1, x2, y2]（归一化坐标 → 像素坐标）
            val x1 = (x - w / 2f) * inputWidth
            val y1 = (y - h / 2f) * inputHeight
            val x2 = (x + w / 2f) * inputWidth
            val y2 = (y + h / 2f) * inputHeight

            boxes.add(DetectionBox(x1, y1, x2, y2, maxClassConf, maxClassId))
        }

        // 非极大值抑制 (NMS)
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

        return keep.map { box ->
            OnnxDetector.DetectionResult(
                label = if (box.classId < classNames.size) classNames[box.classId] else "unknown",
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

    private fun computeIoU(a: DetectionBox, b: DetectionBox): Float {
        val x1 = max(a.x1, b.x1)
        val y1 = max(a.y1, b.y1)
        val x2 = min(a.x2, b.x2)
        val y2 = min(a.y2, b.y2)

        if (x2 <= x1 || y2 <= y1) return 0f

        val interArea = (x2 - x1) * (y2 - y1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        return interArea / (areaA + areaB - interArea)
    }
}
