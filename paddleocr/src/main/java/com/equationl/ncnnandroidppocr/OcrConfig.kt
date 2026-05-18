package com.equationl.ncnnandroidppocr

import com.equationl.ncnnandroidppocr.bean.Device
import com.equationl.ncnnandroidppocr.bean.ImageSize
//import com.equationl.ncnnandroidppocr.bean.ModelType

data class OcrConfig(
    var useSlim: Boolean = true,
    var modelPath: String = "models/ocr_v5_for_cpu",
    var cpuThreadNum: Int = 0,
    var scoreThreshold: Float = 0.5f,
    var device: Device = Device.CPU,
    var imageSize: ImageSize = ImageSize.Size640,
    //var modelType: ModelType = ModelType.Mobile,
    var useFp16: Boolean = true,
    var isDrawTextBox: Boolean = false,
    var detParamFilename: String = "det.ncnn.param",
    var detBinFilename: String = "det.ncnn.bin",
    var recParamFilename: String = "rec.ncnn.param",
    var recBinFilename: String = "rec.ncnn.bin"
)