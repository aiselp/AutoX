# 分类训练
model = YOLO('yolov8n-cls.pt')  # 使用nano版本，适合移动设备

# 训练模型
results = model.train(
    data=r'你的路径',  # 替换为你的数据集根目录绝对路径
    epochs=50,        # 训练轮数，根据数据集调整
    imgsz=128,         # 输入图像大小，平衡速度和精度。训练分类模型时常用64x64或224x224 :cite[1]:cite[5]
    batch=32,         # 批量大小
    workers=0,        # 数据加载的线程数，Windows下建议设为0 :cite[5]
    optimizer='Adam', # 优化器，也可以使用SGD
    lr0=0.001,        # 初始学习率
    name='my_custom_model',
    project=r'你的路径'
)
