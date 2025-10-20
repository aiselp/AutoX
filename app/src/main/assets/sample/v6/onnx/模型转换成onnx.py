#训练完成后转换成onnx模型

from ultralytics import YOLO

model = YOLO(r'best.pt')  # 加载训练好的最佳模型

# 指定导出模型的保存目录
model.save_dir = r'你的路径'  # 请替换为你想要的目录

model.export(format='onnx', opset=18, simplify=True)
