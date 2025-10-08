//autojs/src/main/java/com/stardust/autojs/ocr/OcrUtils.kt
package com.stardust.autojs.ocr

import androidx.core.math.MathUtils
import org.opencv.core.*
import org.opencv.core.Core.*
import org.opencv.core.CvType.CV_8UC1
import org.opencv.core.Mat.zeros
import org.opencv.imgproc.Imgproc.*
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.sqrt
import android.util.Log


internal fun getScaleParam(src: Mat, targetSize: Int): ScaleParam {
    val srcWidth = src.cols()
    var dstWidth = src.cols()

    val srcHeight = src.rows()
    var dstHeight = src.rows()

    var scale = 1.0F

    // 使用官方策略：按长边缩放
    val maxSide = max(srcWidth, srcHeight)
    scale = targetSize.toFloat() / maxSide.toFloat()
    
    if (maxSide == srcWidth) {
        dstWidth = targetSize
        dstHeight = (srcHeight.toFloat() * scale).toInt()
    } else {
        dstHeight = targetSize
        dstWidth = (srcWidth.toFloat() * scale).toInt()
    }
    
    // 确保尺寸是32的倍数
    if (dstWidth % 32 != 0) {
        dstWidth = (dstWidth + 31) / 32 * 32
    }
    if (dstHeight % 32 != 0) {
        dstHeight = (dstHeight + 31) / 32 * 32
    }
    
    // 确保最小尺寸
    dstWidth = max(dstWidth, 32)
    dstHeight = max(dstHeight, 32)
    
    val scaleWidth = dstWidth.toFloat() / srcWidth.toFloat()
    val scaleHeight = dstHeight.toFloat() / srcHeight.toFloat()
    return ScaleParam(
        srcWidth = srcWidth,
        srcHeight = srcHeight,
        dstWidth = dstWidth,
        dstHeight = dstHeight,
        ratioWidth = scaleWidth,
        ratioHeight = scaleHeight,
    )
}

internal fun makePadding(src: Mat, padding: Int): Mat {
    if (padding <= 0) return src
    val paddingScalar = Scalar(255.0, 255.0, 255.0)
    val paddingSrc = Mat()
    copyMakeBorder(src, paddingSrc, padding, padding, padding, padding, Core.BORDER_ISOLATED, paddingScalar)
    return paddingSrc
}

internal fun getThickness(src: Mat): Int {
    val minSize = Math.min(src.cols(), src.rows())
    return minSize / 1000 + 2
}

internal fun substractMeanNormalize(src: Mat, meanVals: FloatArray, normVals: FloatArray): FloatBuffer {
    val inputTensorSize = src.cols() * src.rows() * src.channels()
    val numChannels = src.channels()
    val numCols = src.cols()
    val numRows = src.rows()
    val imageSize = numCols * numRows
    val imgData = FloatBuffer.allocate(inputTensorSize)
    imgData.rewind()
    src.convertTo(src, CvType.CV_32FC3)
    val srcArray = FloatArray(inputTensorSize)
    src.get(0, 0, srcArray)
    for (pid in 0 until imageSize) {
        for (ch in 0 until numChannels) {
            val data = srcArray[pid * numChannels + ch] * normVals[ch] - meanVals[ch] * normVals[ch]
            imgData.put(ch * imageSize + pid, data)
        }
    }
    imgData.rewind()
    return imgData
}

internal fun getMinBoxes(boxRect: RotatedRect, minBoxes: Array<Point>): Float {
    val maxSideLen = max(boxRect.size.width, boxRect.size.height)

    val boxPoint = getBox(boxRect)

    boxPoint.sortBy { it.x }
    val index1: Int
    val index2: Int
    val index3: Int
    val index4: Int
    if (boxPoint[1].y > boxPoint[0].y) {
        index1 = 0
        index4 = 1
    } else {
        index1 = 1
        index4 = 0
    }
    if (boxPoint[3].y > boxPoint[2].y) {
        index2 = 2
        index3 = 3
    } else {
        index2 = 3
        index3 = 2
    }

    minBoxes[0] = boxPoint[index1]
    minBoxes[1] = boxPoint[index2]
    minBoxes[2] = boxPoint[index3]
    minBoxes[3] = boxPoint[index4]

    return maxSideLen.toFloat()
}

internal fun getBox(boxRect: RotatedRect): Array<Point> {
    val points: Array<Point> = Array<Point>(4) {
        Point()
    }
    boxRect.points(points)
    return points
}

internal fun boxScoreFast(boxes: Array<Point>, pred: Mat): Float {
    val width = pred.cols()
    val height = pred.rows()

    val arrayX: Array<Double> = Array(4) { i ->
        boxes[i].x
    }
    val arrayY: Array<Double> = Array(4) { i ->
        boxes[i].y
    }

    val minX = MathUtils.clamp(Math.floor(arrayX.min()).toFloat(), 0.0F, width - 1.0F).toInt()
    val maxX = MathUtils.clamp(Math.ceil(arrayX.max()).toFloat(), 0.0F, width - 1.0F).toInt()
    val minY = MathUtils.clamp(Math.floor(arrayY.min()).toFloat(), 0.0F, height - 1.0F).toInt()
    val maxY = MathUtils.clamp(Math.ceil(arrayY.max()).toFloat(), 0.0F, height - 1.0F).toInt()

    val mask = zeros(maxY - minY + 1, maxX - minX + 1, CV_8UC1)

    val box = arrayOf(
        Point((boxes[0].x.toInt() - minX).toDouble(), (boxes[0].y.toInt() - minY).toDouble()),
        Point((boxes[1].x.toInt() - minX).toDouble(), (boxes[1].y.toInt() - minY).toDouble()),
        Point((boxes[2].x.toInt() - minX).toDouble(), (boxes[2].y.toInt() - minY).toDouble()),
        Point((boxes[3].x.toInt() - minX).toDouble(), (boxes[3].y.toInt() - minY).toDouble())
    )

    val pts = listOf(MatOfPoint(*box))

    fillPoly(mask, pts, Scalar(1.0))

    val croppedImg = pred.submat(Rect(minX, minY, maxX - minX + 1, maxY - minY + 1))

    val score = mean(croppedImg, mask)

    return score.`val`[0].toFloat()
}

internal fun unClip(box: Array<Point>, unClipRatio: Float): RotatedRect {
    // 简化的多边形扩展实现，不使用 Clipper 库
    val points = box.toList()
    val area = polygonArea(points)
    val length = polygonPerimeter(points)
    
    val distance = area * unClipRatio / length
    
    // 简单的向外扩展每个点
    val expandedPoints = points.map { point ->
        val center = Point(
            points.map { it.x }.average(),
            points.map { it.y }.average()
        )
        val direction = Point(point.x - center.x, point.y - center.y)
        val norm = sqrt(direction.x * direction.x + direction.y * direction.y)
        if (norm > 0) {
            Point(
                point.x + direction.x / norm * distance,
                point.y + direction.y / norm * distance
            )
        } else {
            point
        }
    }.toTypedArray()
    
    return minAreaRect(MatOfPoint2f(*expandedPoints))
}

// 添加多边形面积计算
private fun polygonArea(points: List<Point>): Double {
    var area = 0.0
    val n = points.size
    for (i in 0 until n) {
        val j = (i + 1) % n
        area += points[i].x * points[j].y
        area -= points[j].x * points[i].y
    }
    return abs(area) / 2.0
}

// 添加多边形周长计算
private fun polygonPerimeter(points: List<Point>): Double {
    var perimeter = 0.0
    val n = points.size
    for (i in 0 until n) {
        val j = (i + 1) % n
        perimeter += sqrt(
            (points[i].x - points[j].x) * (points[i].x - points[j].x) +
            (points[i].y - points[j].y) * (points[i].y - points[j].y)
        )
    }
    return perimeter
}

internal fun drawTextBoxes(boxImg: Mat, textBoxes: List<DetResult>, thickness: Int) {
    val color = Scalar(0.0, 0.0, 255.0)// B(0) G(0) R(255)
    textBoxes.filter { it.points.size == 4 }.forEach { box ->
        line(boxImg, box.points[0].toCvPoint(), box.points[1].toCvPoint(), color, thickness)
        line(boxImg, box.points[1].toCvPoint(), box.points[2].toCvPoint(), color, thickness)
        line(boxImg, box.points[2].toCvPoint(), box.points[3].toCvPoint(), color, thickness)
        line(boxImg, box.points[3].toCvPoint(), box.points[0].toCvPoint(), color, thickness)
    }
}

internal fun getRotateCropImage(src: Mat, box: List<DetPoint>): Mat {
    if (box.size != 4) {
        Log.e("OCR", "Invalid box points: ${box.size}")
        return Mat()
    }
    
    // 记录原始信息用于调试
    Log.d("OCR", "Original box points: ${box.map { "(${it.x},${it.y})" }}")
    Log.d("OCR", "Source image size: ${src.cols()}x${src.rows()}")
    
    // 第一步：安全地计算裁剪区域
    val points = box.map { it.toCvPoint() }

    // 计算边界框，添加安全边界
    val collectX = box.map { it.x }
    val collectY = box.map { it.y }
    
    var left = collectX.min().toDouble()
    var right = collectX.max().toDouble()
    var top = collectY.min().toDouble()
    var bottom = collectY.max().toDouble()
    
    // 添加安全边界，防止裁切
    val safeMargin = 2 // 2像素的安全边界
    left = max(0.0, left - safeMargin)
    top = max(0.0, top - safeMargin)
    right = minOf(right + safeMargin, src.cols() - 1.0)  // 修复：使用 minOf 而不是 min
    bottom = minOf(bottom + safeMargin, src.rows() - 1.0) // 修复：使用 minOf 而不是 min
    
    val width = (right - left).toInt()
    val height = (bottom - top).toInt()
    
    // 严格的边界检查
    if (width <= 0 || height <= 0) {
        Log.e("OCR", "Invalid crop dimensions: width=$width, height=$height")
        return Mat()
    }
    
    if (left < 0 || top < 0 || left + width > src.cols() || top + height > src.rows()) {
        Log.e("OCR", "Crop region out of bounds: left=$left, top=$top, width=$width, height=$height")
        // 安全调整
        val adjustedLeft = left.coerceIn(0.0, src.cols() - 1.0)
        val adjustedTop = top.coerceIn(0.0, src.rows() - 1.0)
        val adjustedWidth = minOf(width, src.cols() - adjustedLeft.toInt())  // 修复：使用 minOf
        val adjustedHeight = minOf(height, src.rows() - adjustedTop.toInt()) // 修复：使用 minOf
        
        if (adjustedWidth <= 0 || adjustedHeight <= 0) {
            return Mat()
        }
        
        return try {
            src.submat(Rect(adjustedLeft.toInt(), adjustedTop.toInt(), adjustedWidth, adjustedHeight))
        } catch (e: Exception) {
            Log.e("OCR", "Safe crop failed: ${e.message}")
            Mat()
        }
    }
    
    try {
        // 直接裁剪，不进行透视变换（避免复杂的坐标计算错误）
        val imgCrop = src.submat(Rect(left.toInt(), top.toInt(), width, height))
        
        if (imgCrop.empty()) {
            Log.e("OCR", "Cropped image is empty")
            return Mat()
        }
        
        Log.d("OCR", "Successfully cropped: ${imgCrop.cols()}x${imgCrop.rows()}")
        return imgCrop
        
    } catch (e: Exception) {
        Log.e("OCR", "Error in simple crop: ${e.message}")
        return Mat()
    }
}

// 计算两点间距离
private fun distance(p1: Point, p2: Point): Double {
    return sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y))
}

internal fun getPartMats(src: Mat, detResults: List<DetResult>): List<Mat> {
    return detResults.mapIndexed { index, detResult ->
        try {
            Log.d("OCR", "Processing detResult $index: points=${detResult.points.map { "(${it.x},${it.y})" }}, score=${detResult.score}")
            
            val cropImage = getRotateCropImage(src, detResult.points)
            if (cropImage.empty()) {
                Log.w("OCR", "Empty crop image for detResult $index")
                null
            } else {
                Log.d("OCR", "Successfully cropped image $index: ${cropImage.cols()}x${cropImage.rows()}")
                cropImage
            }
        } catch (e: Exception) {
            Log.e("OCR", "Error processing detResult $index: ${e.message}")
            null
        }
    }
}

internal fun matRotateClockWise180(src: Mat): Mat {
    flip(src, src, 0)
    flip(src, src, 1)
    return src
}

internal fun adjustToDst(src: Mat, dstWidth: Double, dstHeight: Double): Mat {
    val srcResize = Mat()
    val scale = dstHeight / src.rows()
    val srcWidth = src.cols() * scale
    resize(src, srcResize, Size(srcWidth, dstHeight))
    val srcFit = Mat(dstHeight.toInt(), dstWidth.toInt(), CvType.CV_8UC3, Scalar(255.0, 255.0, 255.0))
    if (srcWidth < dstWidth) {
        val rect = Rect(0, 0, srcResize.cols(), srcResize.rows())
        srcResize.copyTo(srcFit.submat(rect))
    } else {
        val rect = Rect(0, 0, dstWidth.toInt(), dstHeight.toInt())
        srcResize.submat(rect).copyTo(srcFit)
    }
    return srcFit;
}
