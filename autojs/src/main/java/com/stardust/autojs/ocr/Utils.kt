//autojs/src/main/java/com/stardust/autojs/ocr/Utils.kt
package com.stardust.autojs.ocr

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

object OcrUtils {
    
    /**
     * 计算两点之间的距离
     */
    fun distance(point1: Point, point2: Point): Double {
        return sqrt((point1.x - point2.x).pow(2) + (point1.y - point2.y).pow(2))
    }
    
    /**
     * 计算多边形的面积
     */
    fun polygonArea(points: List<Point>): Double {
        var area = 0.0
        val n = points.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }
        return abs(area) / 2.0
    }
    
    /**
     * 计算多边形的周长
     */
    fun polygonPerimeter(points: List<Point>): Double {
        var perimeter = 0.0
        val n = points.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            perimeter += distance(points[i], points[j])
        }
        return perimeter
    }
    
    /**
     * 限制数值在指定范围内
     */
    fun clamp(value: Int, min: Int, max: Int): Int {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }
    
    fun clamp(value: Float, min: Float, max: Float): Float {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }
    
    fun clamp(value: Double, min: Double, max: Double): Double {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }
    
    /**
     * 对图像进行透视变换矫正
     */
    fun perspectiveTransform(src: Mat, points: List<Point>, dstWidth: Int, dstHeight: Int): Mat {
        val srcPoints = MatOfPoint2f(
            points[0], points[1], points[2], points[3]
        )
        
        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(dstWidth.toDouble(), 0.0),
            Point(dstWidth.toDouble(), dstHeight.toDouble()),
            Point(0.0, dstHeight.toDouble())
        )
        
        val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        val dst = Mat()
        Imgproc.warpPerspective(src, dst, transform, Size(dstWidth.toDouble(), dstHeight.toDouble()))
        
        return dst
    }
    
    /**
     * 计算文本行的倾斜角度
     */
    fun computeTextLineAngle(points: List<Point>): Double {
        val leftPoints = listOf(points[0], points[3]).sortedBy { it.x }
        val rightPoints = listOf(points[1], points[2]).sortedBy { it.x }
        
        val leftCenter = Point(
            (leftPoints[0].x + leftPoints[1].x) / 2,
            (leftPoints[0].y + leftPoints[1].y) / 2
        )
        val rightCenter = Point(
            (rightPoints[0].x + rightPoints[1].x) / 2,
            (rightPoints[0].y + rightPoints[1].y) / 2
        )
        
        return atan2(rightCenter.y - leftCenter.y, rightCenter.x - leftCenter.x) * 180 / PI
    }
}
