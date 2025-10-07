//autojs/src/main/java/com/stardust/autojs/ocr/OcrEngine.kt
package com.stardust.autojs.ocr

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import com.stardust.autojs.ocr.OcrResult
import com.stardust.autojs.ocr.ScaleParam
import com.orhanobut.logger.Logger
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.android.Utils.matToBitmap
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.TickMeter
import org.opencv.imgproc.Imgproc.*
import java.io.Closeable
import java.lang.Integer.max
import com.stardust.autojs.runtime.ScriptRuntime


@android.webkit.JavascriptInterface
class OcrEngine(context: Context) : Closeable {

    private val assetManager: AssetManager = context.assets

    private val ortEnv by lazy { OrtEnvironment.getEnvironment() }

    private val det by lazy { Det(ortEnv, assetManager, DET_NAME) }

    private val cls by lazy { Cls(ortEnv, assetManager, CLS_NAME) }

    private val rec by lazy { Rec(ortEnv, assetManager, REC_NAME, KEYS_NAME) }

    init {
        if (OpenCVLoader.initDebug()) {
            Logger.i("OpenCV library found inside package.")
        } else {
            Logger.e("Internal OpenCV library not found.")
            throw UnsatisfiedLinkError("Internal OpenCV library not found.")
        }
    }

    override fun close() {
        ortEnv.close()
    }

    /**
     * 进行一次完整识别.
     * 一次完整识别包括3个步骤：1检测(det)，2方向分类(cls)，3识别(rec)
     * @param bmp 输入的图片
     * @param scaleUp 放大使能；禁用时只进行图片缩小，不进行放大；启用时，原图长边小于maxSideLen时会放大，原图长边大于maxSideLen时会缩小
     * @param maxSideLen 长边缩放(单位像素)，把原始图片以长边为基准等比例缩放，用于减少检测(det)耗时，0代表不缩放，如果原始图片长边小于32，则缩放到32
     * @param padding 增加白边(单位像素)，太靠近边缘的文字检测(det)效果不佳，通过增加此值来提升识别率
     * @param boxScoreThresh 文字框置信度门限，检测(det)没有正确框出所有文字时，减小此值
     * @param boxThresh 用于过滤检测过程中的噪点
     * @param unClipRatio 文字框大小倍率，越大时单个文字框越大
     * @param doCls 文字方向分类，只有图片倒置的情况下(旋转90~270度的图片)，才需要启用此项
     * @param mostCls 文字方向投票(关闭时每行方向独立，开启时以最大概率作为全文方向)，当禁用文字方向分类时，此项也不起作用
     */
    @android.webkit.JavascriptInterface
    fun detect(
        bmp: Bitmap,
        scaleUp: Boolean = true,
        maxSideLen: Int = 1920,
        padding: Int = 50,
        boxScoreThresh: Float = 0.3f,
        boxThresh: Float = 0.6f,
        unClipRatio: Float = 1.5f,
        doCls: Boolean = true,
        mostCls: Boolean = true
    ): OcrResult {
        Logger.i("=====Prepare=====")
        Logger.i("Parameter: scaleUp($scaleUp), maxSideLen($maxSideLen), padding($padding),boxScoreThresh($boxScoreThresh),boxThresh($boxThresh),unClipRatio($unClipRatio),doCls($doCls),mostCls($mostCls)")

        Logger.i("---------- step: input Bitmap -> Mat(RGBA) -> Mat(BGR) ----------")
        val inputRGBA = Mat(bmp.width, bmp.height, CvType.CV_8UC4)
        Utils.bitmapToMat(bmp, inputRGBA)
        val inputBGR = Mat()
        cvtColor(inputRGBA, inputBGR, COLOR_RGBA2BGR)

        Logger.i("---------- step: Resize ----------")
        val originMaxSide = max(inputBGR.cols(), inputBGR.rows())
        var resize = if (scaleUp) {
            //支持放大和缩小
            if (maxSideLen <= 0) originMaxSide else maxSideLen
        } else {
            //仅支持缩小
            if (maxSideLen <= 0 || originMaxSide < maxSideLen) originMaxSide else maxSideLen
        }
        resize += 2 * padding
        Logger.i("resize=$resize")
        val paddingRect = Rect(padding, padding, inputBGR.cols(), inputBGR.rows())
        val paddingSrc = makePadding(inputBGR, padding)
        val s = getScaleParam(paddingSrc, resize)
        Logger.i("$s")

        val ocrResult = fullDetect(paddingSrc, paddingRect, s, boxScoreThresh, boxThresh, unClipRatio, doCls, mostCls)
        Logger.i(ocrResult.toString())

        return ocrResult
    }

    private fun fullDetect(
        src: Mat,
        paddingRect: Rect,
        s: ScaleParam,
        boxScoreThresh: Float,
        boxThresh: Float,
        unClipRatio: Float,
        doCls: Boolean,
        mostCls: Boolean
    ): OcrResult {
        val fullTickMeter = TickMeter().apply { start() }
        val textBoxPaddingImg = src.clone()
        val thickness = getThickness(src)
        Logger.i("=====Start detect=====")

        val detTickMeter = TickMeter().apply { start() }
        Logger.i("---------- step: Get DetResults ----------")
        val detResults = det.getDetResults(src, s, boxScoreThresh, boxThresh, unClipRatio)
        detTickMeter.stop()

        Logger.i("---------- step: Draw TextBoxes ----------")
        drawTextBoxes(textBoxPaddingImg, detResults, thickness)

        Logger.i("---------- step: Get PartMats ----------")
        val partMats = getPartMats(src, detResults)

        val clsTickMeter = TickMeter().apply { start() }
        val clsResults = if (doCls) {
            Logger.i("---------- step: Get ClsResults ----------")
            val results = cls.getClsResults(partMats)
            if (mostCls) {
                results.map {
                    val sum = results.map { it.index }.sum().toFloat()
                    val halfPercent = results.size.toFloat() / 2.0F
                    //Logger.i("sum=$sum,halfPercent=$halfPercent")
                    val mostAngleIndex = if (sum < halfPercent) 0 else 1
                    it.copy(index = mostAngleIndex)
                }
            } else results
        } else emptyList()
        clsTickMeter.stop()

        val clsPartMats = if (doCls) {
            Logger.i("---------- step: Rotate partImages ----------")
            partMats.mapIndexed { index, mat ->
                if (clsResults[index].index == 1) {
                    matRotateClockWise180(mat)
                } else mat
            }
        } else partMats

        val recTickMeter = TickMeter().apply { start() }
        Logger.i("---------- step: Get RecResults ----------")
        val recResults = rec.getRecResults(clsPartMats)
        recTickMeter.stop()

        Logger.i("---------- step: output box Mat(BGR) -> Mat(RGBA) -> Bitmap ----------")
        val outRGBA = Mat()
        cvtColor(textBoxPaddingImg.submat(paddingRect), outRGBA, COLOR_BGR2RGBA)
        val boxImage = Bitmap.createBitmap(
            outRGBA.cols(), outRGBA.rows(), Bitmap.Config.ARGB_8888
        )
        matToBitmap(outRGBA, boxImage)

        val text = recResults.joinToString(separator = "\n") { it.text }
        fullTickMeter.stop()
        return OcrResult(
            detResults = detResults,
            detTime = detTickMeter.timeMilli,
            clsResults = clsResults,
            clsTime = clsTickMeter.timeMilli,
            recResults = recResults,
            recTime = recTickMeter.timeMilli,
            boxImage = boxImage,
            fullTime = fullTickMeter.timeMilli,
            text = text,
        )
    }


    companion object {
        // PP-OCRv5 模型文件
        private const val DET_NAME = "ch_PP-OCRv5_det_infer.onnx"
        private const val CLS_NAME = "ch_ppocr_mobile_v2.0_cls_infer.onnx"  // 分类器可以继续使用v2版本
        private const val REC_NAME = "ch_PP-OCRv5_rec_infer.onnx"
        private const val KEYS_NAME = "ppocr_keys_v1.txt"
    }
}
