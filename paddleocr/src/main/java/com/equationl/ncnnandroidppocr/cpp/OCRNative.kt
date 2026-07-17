package com.equationl.ncnnandroidppocr.cpp

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.equationl.ncnnandroidppocr.bean.NcnnOcrResult

class OCRNative {
    /**
     * 从本地文件路径加载模型
     *
     * @param detParamPath 检测模型参数文件路径 (如 /sdcard/models/PP_OCRv5_mobile_det.ncnn.param)
     * @param detModelPath 检测模型权重文件路径 (如 /sdcard/models/PP_OCRv5_mobile_det.ncnn.bin)
     * @param recParamPath 识别模型参数文件路径 (如 /sdcard/models/PP_OCRv5_mobile_rec.ncnn.param)
     * @param recModelPath 识别模型权重文件路径 (如 /sdcard/models/PP_OCRv5_mobile_rec.ncnn.bin)
     * @param sizeid 目标尺寸 ID (0-6)，对应 320, 400, 480, 560, 640, 720, 1080
     * @param cpugpu 计算设备选择：0=CPU, 1=GPU, 2=Turnip Vulkan
     * @param useFp16 是否使用 fp16 精度推理，默认为 true。注意：server 模型使用 fp16 可能产生 nan 结果
     *
     * @return 加载是否成功
     */
    external fun loadModelByPath(
        detParamPath: String,
        detModelPath: String,
        recParamPath: String,
        recModelPath: String,
        targetSize: Int,
        cpugpu: Int,
        useFp16: Boolean = true,
        numThreads: Int = Runtime.getRuntime().availableProcessors()
    ): Boolean

    /**
     * 释放模型资源
     *
     * 在不再需要 OCR 功能时调用此方法释放内存
     */
    external fun release()

    /**
     * 识别 Bitmap 图片
     * @param bitmap 要识别的图片
     * @return OCR 识别结果
     */
    external fun detectBitmap(bitmap: Bitmap): NcnnOcrResult?

    /**
     * 识别本地图片文件
     * @param imagePath 图片文件路径
     * @return OCR 识别结果
     */
    external fun detectImagePath(imagePath: String): NcnnOcrResult?

    companion object {
        init {
            System.loadLibrary("ppocrv5ncnn")
        }
    }
}
