// 加载一个分类模型
const classifierName = "my_classifier";
const classifierModelPath = "模型路径，需要绝对路径";
runtime.onnx.loadClassifierAuto(classifierName, classifierModelPath);

const imagePath = images.read("图片路径");//或者直接传入一个截图或裁剪的img对象

const topK = 3;//返回前3个置信度最高的

// 分类
let start = new Date()
const classResults = runtime.onnx.classifyImage(classifierName, imagePath, topK);
cost = (new Date() - start)
console.log("智能分类结果:", JSON.parse(classResults));
toastLog('耗时' + cost + 'ms')
