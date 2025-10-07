//autojs/src/main/java/com/stardust/autojs/ocr/OcrUtils.kt
package com.stardust.autojs.ocr

import androidx.core.math.MathUtils
import com.stardust.autojs.ocr.DetPoint
import com.stardust.autojs.ocr.DetResult
import com.stardust.autojs.ocr.ScaleParam
import de.lighti.clipper.Clipper
import de.lighti.clipper.ClipperOffset
import de.lighti.clipper.Path
import de.lighti.clipper.Paths
import org.opencv.core.*
import org.opencv.core.Core.*
import org.opencv.core.CvType.CV_8UC1
import org.opencv.core.Mat.zeros
import org.opencv.imgproc.Imgproc.*
import java.nio.FloatBuffer
import kotlin.math.max


internal fun getScaleParam(src: Mat, targetSize: Int): ScaleParam {
    val srcWidth = src.cols()
    var dstWidth = src.cols()

    val srcHeight = src.rows()
    var dstHeight = src.rows()

    var scale = 1.0F

    if (dstWidth > dstHeight) {
        scale = targetSize.toFloat() / dstWidth.toFloat()
        dstWidth = targetSize
        dstHeight = (dstHeight.toFloat() * scale).toInt()
    } else {
        scale = targetSize.toFloat() / dstHeight.toFloat()
        dstHeight = targetSize
        dstWidth = (dstWidth.toFloat() * scale).toInt()
    }
    if (dstWidth % 32 != 0) {
        dstWidth = (dstWidth / 32 - 1) * 32
        dstWidth = max(dstWidth, 32)
    }
    if (dstHeight % 32 != 0) {
        dstHeight = (dstHeight / 32 - 1) * 32
        dstHeight = max(dstHeight, 32)
    }
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

internal fun unClip(box: Array<org.opencv.core.Point>, unClipRatio: Float): org.opencv.core.RotatedRect {
    // 简化的多边形扩展实现，不使用 Clipper 库
    val points = box.toList()
    val area = polygonArea(points)
    val length = polygonPerimeter(points)
    
    val distance = area * unClipRatio / length
    
    // 简单的向外扩展每个点
    val expandedPoints = points.map { point ->
        val center = org.opencv.core.Point(
            points.map { it.x }.average(),
            points.map { it.y }.average()
        )
        val direction = org.opencv.core.Point(point.x - center.x, point.y - center.y)
        val norm = Math.sqrt(direction.x * direction.x + direction.y * direction.y)
        if (norm > 0) {
            org.opencv.core.Point(
                point.x + direction.x / norm * distance,
                point.y + direction.y / norm * distance
            )
        } else {
            point
        }
    }.toTypedArray()
    
    return org.opencv.imgproc.Imgproc.minAreaRect(org.opencv.core.MatOfPoint2f(*expandedPoints))
}

// 添加多边形面积计算
private fun polygonArea(points: List<org.opencv.core.Point>): Double {
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
private fun polygonPerimeter(points: List<org.opencv.core.Point>): Double {
    var perimeter = 0.0
    val n = points.size
    for (i in 0 until n) {
        val j = (i + 1) % n
        perimeter += Math.sqrt(
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
    val points = box.map { it.toCvPoint() }

    val collectX = arrayOf(box[0].x, box[1].x, box[2].x, box[3].x)
    val collectY = arrayOf(box[0].y, box[1].y, box[2].y, box[3].y)
    val left = collectX.min()
    val right = collectX.max()
    val top = collectY.min()
    val bottom = collectY.max()

    val imgCrop = src.submat(Rect(left, top, right - left, bottom - top))

    for (i in points.indices) {
        points[i].x -= left
        points[i].y -= top
    }

    val imgCropWidth = Math.sqrt(
        Math.pow(points[0].x - points[1].x, 2.0) + Math.pow(points[0].y - points[1].y, 2.0)
    )

    val imgCropHeight = Math.sqrt(
        Math.pow(points[0].x - points[3].x, 2.0) + Math.pow(points[0].y - points[3].y, 2.0)
    )

    val ptsDst = arrayOf(
        Point(0.0, 0.0),
        Point(imgCropWidth, 0.0),
        Point(imgCropWidth, imgCropHeight),
        Point(0.0, imgCropHeight)
    )

    val ptsSrc = arrayOf(
        Point(points[0].x, points[0].y),
        Point(points[1].x, points[1].y),
        Point(points[2].x, points[2].y),
        Point(points[3].x, points[3].y),
    )
    val transformation = getPerspectiveTransform(MatOfPoint2f(*ptsSrc), MatOfPoint2f(*ptsDst))

    val partImg = Mat()
    warpPerspective(
        imgCrop, partImg, transformation,
        Size(imgCropWidth, imgCropHeight),
        BORDER_REPLICATE
    );

    return if (partImg.rows() >= partImg.cols() * 1.5) {
        val srcCopy = Mat(partImg.rows(), partImg.cols(), partImg.depth())
        transpose(partImg, srcCopy)
        flip(srcCopy, srcCopy, 0)
        srcCopy
    } else {
        partImg
    }
}

internal fun getPartMats(src: Mat, detResults: List<DetResult>): List<Mat> {
    return detResults.map { getRotateCropImage(src, it.points) }
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
