package com.equationl.ncnnandroidppocr.bean

enum class ModelType {
    /**
     * 使用移动端模型
     *
     * 需要将模型文件严格命名为下述名称后置于项目的 assets 目录下：
     *
     * - PP_OCRv5_mobile_det.ncnn.bin
     * - PP_OCRv5_mobile_det.ncnn.param
     * - PP_OCRv5_mobile_rec.ncnn.bin
     * - PP_OCRv5_mobile_rec.ncnn.param
     *
     * */
    Mobile,
    /**
     * 使用服务器模型
     *
     * 需要将模型文件严格命名为下述名称后置于项目的 assets 目录下：
     *
     * - PP_OCRv5_server_det.ncnn.bin
     * - PP_OCRv5_server_det.ncnn.param
     * - PP_OCRv5_server_rec.ncnn.bin
     * - PP_OCRv5_server_rec.ncnn.param
     *
     * */
    Server
}