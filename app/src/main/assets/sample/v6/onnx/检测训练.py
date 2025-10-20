#目标检测训练
from ultralytics import YOLO

model = YOLO('yolov8n.pt') # 加载一个预训练模型

results = model.train(
    data=r'yolo.yaml',
    imgsz=640,
    epochs=100,
    batch=16,
    cache=True,
    name='my_custom_model',
    project=r'yolodet'
)
