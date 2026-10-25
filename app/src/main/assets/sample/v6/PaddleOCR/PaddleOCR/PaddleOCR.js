/*
 * 当前 paddle OCR 使用 ncnn 实现，内置模型目录为 models/ocr_v5_for_cpu。
 * useSlim 只控制输入尺寸：true 使用 320（更快），false 使用 512（更准）。
 */
let img = images.read("./0.jpg")
let useSlim = true
let start = new Date()
// 识别图片中的文字，返回完整识别信息（兼容百度OCR格式）。
let result = paddle.ocr(img, useSlim)
log('OCR识别耗时：' + (new Date() - start) + 'ms')
// const result = paddle.ocr(img)
toastLog("完整识别信息: " + JSON.stringify(result))
start = new Date()
// 识别图片中的文字，只返回文本识别信息（字符串列表）。当前版本可能存在文字顺序错乱的问题 建议先使用detect后自行排序
const stringList = paddle.ocrText(img, useSlim)
log('OCR纯文本识别耗时：' + (new Date() - start) + 'ms')
// const stringList = paddle.ocrText(img)
toastLog("文本识别信息: " + JSON.stringify(stringList))

// 回收图片
img.recycle()
// 释放native内存，非必要，供万一出现内存泄露时使用
// paddle.release()

