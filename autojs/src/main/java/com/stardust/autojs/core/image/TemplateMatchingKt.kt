package com.stardust.autojs.core.image

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar


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
            mask.setTo(Scalar(1.0))
            return mask
        }
        // 有alpha通道，提取并转换
        val channels: MutableList<Mat> = ArrayList()
        Core.split(image, channels)

        channels[3].convertTo(mask, CvType.CV_32FC1, 1.0 / 255.0)
        // 可把小于某个阈值的 alpha 视为透明
        //Imgproc.threshold(mask, mask, 1.0, 255.0, Imgproc.THRESH_BINARY)
        channels.forEach { it.release() }
        return mask
    }
}
