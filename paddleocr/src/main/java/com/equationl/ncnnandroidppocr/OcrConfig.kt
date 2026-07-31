package com.equationl.ncnnandroidppocr

import com.equationl.ncnnandroidppocr.bean.Device
//import com.equationl.ncnnandroidppocr.bean.ModelType

data class OcrConfig(
    var useSlim: Boolean = true,
    var modelPath: String = "models/ocr_v5_for_cpu",
    var cpuThreadNum: Int = 0,
    var scoreThreshold: Float = 0.5f,
    var device: Device = Device.CPU,
    var imageSize: Int = IMAGE_SIZE_SLIM,
    //var modelType: ModelType = ModelType.Mobile,
    var useFp16: Boolean = true,
    var isDrawTextBox: Boolean = false,
    var detParamFilename: String = "det.ncnn.param",
    var detBinFilename: String = "det.ncnn.bin",
    var recParamFilename: String = "rec.ncnn.param",
    var recBinFilename: String = "rec.ncnn.bin"
) {
    companion object {
        const val IMAGE_SIZE_SLIM = 320
        const val IMAGE_SIZE_FULL = 512
    }
}