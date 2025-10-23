// 加载一个目标检测模型
const detectorName = "my_detector";
const detectorModelPath = "模型路径，需要绝对路径";
runtime.onnx.loadDetectorAuto(detectorName, detectorModelPath);

const imagePath = images.read("图片路径");//或者直接传入一个截图或裁剪的img对象

// 目标检测
let startTime = Date.now();
const results = runtime.onnx.detectImageAuto(detectorName, imagePath,0.7,0.25);//数字可调，类似置信度
let endTime = Date.now();
toastLog(JSON.parse(results))
