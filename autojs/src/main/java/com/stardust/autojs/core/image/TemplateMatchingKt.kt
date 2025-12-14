package com.stardust.autojs.core.image

import android.util.Log
import org.opencv.core.CvType
import org.opencv.core.Mat


object TemplateMatchingKt {
    fun processingAlphaChannel(image: Mat): Mat {
        // 检查图像是否为空
        require(!(image.empty())) { "输入图像不能为空" }

        // 获取图像的尺寸
        val rows = image.rows()
        val cols = image.cols()

        // 创建结果矩阵 - 确保与输入图像尺寸相同
        val mask = Mat(rows, cols, CvType.CV_32FC1)

        // 检查图像是否有足够的通道数（至少有4个通道用于透明通道）
        if (image.channels() < 4) {
            // 如果没有透明通道，创建一个全白的掩码（所有位置都参与匹配）
            // 确保填充所有像素值为1.0
            for (i in 0..<rows) {
                for (j in 0..<cols) {
                    mask.put(i, j, 1.0)
                }
            }
            return mask
        }
        Log.d("TemplateMatchingKt", "Processing alpha channel for image with size: ${rows}x${cols}")

        // 有alpha通道，提取并转换
        for (i in 0..<rows) {
            for (j in 0..<cols) {
                try {
                    // 获取像素的4个通道值（B, G, R, A）
                    val pixel = image.get(i, j)
                    if (pixel != null && pixel.size >= 4) {
                        // 第4个通道是alpha（索引3），转换为0-1范围
                        val alphaValue = (pixel[3] / 255.0).toFloat()
                        mask.put(i, j, alphaValue.toDouble())
                    } else {
                        // 如果无法获取像素，设置为完全不透明
                        mask.put(i, j, 1.0)
                    }
                } catch (e: Exception) {
                    // 发生异常时设置为完全不透明
                    mask.put(i, j, 1.0)
                }
            }
        }

        return mask
    }
}
