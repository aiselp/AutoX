//autojs/src/main/java/com/stardust/autojs/ocr/OcrConfig.kt
package com.stardust.autojs.ocr

object OcrConfig {
    // 检测模型配置
    const val DET_MODEL_NAME = "ch_PP-OCRv5_det_infer.onnx"
    const val CLS_MODEL_NAME = "ch_ppocr_mobile_v2.0_cls_infer.onnx"
    const val REC_MODEL_NAME = "ch_PP-OCRv5_rec_infer.onnx"
    const val KEYS_FILE_NAME = "ppocr_keys_v1.txt"
    
    // 检测参数
    const val DET_MAX_SIDE_LEN = 960
    const val DET_BOX_THRESH = 0.3f
    const val DET_BOX_SCORE_THRESH = 0.5f
    const val DET_UNCLIP_RATIO = 1.6f
    const val DET_USE_DILATE = true
    
    // 分类参数
    const val CLS_THRESH = 0.9f
    const val CLS_IMAGE_SHAPE_WIDTH = 192
    const val CLS_IMAGE_SHAPE_HEIGHT = 48
    
    // 识别参数
    const val REC_IMAGE_SHAPE_HEIGHT = 48
    const val REC_BATCH_NUM = 6
    
    // 预处理参数
    val DET_MEAN_VALUES = floatArrayOf(0.485f * 255f, 0.456f * 255f, 0.406f * 255f)
    val DET_NORM_VALUES = floatArrayOf(1.0f / 0.229f / 255.0f, 1.0f / 0.224f / 255.0f, 1.0f / 0.225f / 255.0f)
    
    val REC_MEAN_VALUES = floatArrayOf(0.5f * 255f, 0.5f * 255f, 0.5f * 255f)
    val REC_NORM_VALUES = floatArrayOf(1.0f / 0.5f / 255.0f, 1.0f / 0.5f / 255.0f, 1.0f / 0.5f / 255.0f)
    
    val CLS_MEAN_VALUES = floatArrayOf(0.5f * 255f, 0.5f * 255f, 0.5f * 255f)
    val CLS_NORM_VALUES = floatArrayOf(1.0f / 0.5f / 255.0f, 1.0f / 0.5f / 255.0f, 1.0f / 0.5f / 255.0f)
}
