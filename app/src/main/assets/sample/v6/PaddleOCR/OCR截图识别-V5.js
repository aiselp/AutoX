let currentEngine = engines.myEngine()
let runningEngines = engines.all()
let currentSource = currentEngine.getSource() + ''
if (runningEngines.length > 1) {
  runningEngines.forEach(compareEngine => {
    let compareSource = compareEngine.getSource() + ''
    if (currentEngine.id !== compareEngine.id && compareSource === currentSource) {
      // 强制关闭同名的脚本
      compareEngine.forceStop()
    }
  })
}

sleep(100)

if (!floaty.checkPermission()) {
  toast("需要悬浮窗权限来显示悬浮窗，请在随后的界面中允许并重新运行本脚本。");
  floaty.requestPermission();
  exit()
}

if (!requestScreenCapture()) {
  toastLog('请求截图权限失败')
  exit()
}

// 识别结果和截图信息
let result = []
let running = true
let capturing = true

/**
 * 截图并识别OCR文本信息
 */
function captureAndOcr() {
  capturing = true
  let img = captureScreen()
  if (!img) {
    toastLog('截图失败')
  }
  let start = new Date()
  result = paddle.ocr(img);
  let elapsed = new Date() - start
  log(result);
  toastLog('耗时' + elapsed + 'ms')

  // 根据当前设备和模型记录识别时间
  recognitionTime[currentDevice][currentModel] = elapsed
  updateDeviceTimeDisplay()

  capturing = false
}


// 获取状态栏高度
let offset = -getStatusBarHeightCompat()
//let offset = 0;

// 绘制识别结果
let window = floaty.rawWindow(
  <canvas id="canvas" layout_weight="1" />
);

// 设置悬浮窗位置
ui.post(() => {
  window.setPosition(0, offset)
  window.setSize(device.width, device.height)
  window.setTouchable(false)
})

// 操作按钮 - 在每个设备下方显示快速和精确时间
let clickButtonWindow = floaty.rawWindow(
  <vertical>
    <!-- 拖动栏 -->
    <linear id="dragBar" layout_height="32dp" background="#3a3a3a" gravity="center" orientation="horizontal">
      <text text="⋮⋮" textSize="18sp" textColor="#ffffff" layout_marginRight="8dp" />
      <text text="拖动移动" textSize="12sp" textColor="#aaaaaa" />
      <text text="⋮⋮" textSize="18sp" textColor="#ffffff" layout_marginLeft="8dp" />
    </linear>
    <!-- 时间栏 -->
    <horizontal id="timeRow" marginTop="5dp" background="#3a3a3a" padding="5dp">
      <vertical layout_width="0dp" layout_weight="1" layout_height="32dp">
        <text id="cpuFastTime" text="" textSize="11sp" gravity="center" textColor="#4caf50" />
        <text id="cpuAccurateTime" text="" textSize="11sp" gravity="center" textColor="#ff9800" />
      </vertical>
      <vertical layout_width="0dp" layout_weight="1" layout_height="32dp">
        <text id="gpuFastTime" text="" textSize="11sp" gravity="center" textColor="#4caf50" />
        <text id="gpuAccurateTime" text="" textSize="11sp" gravity="center" textColor="#ff9800" />
      </vertical>
      <vertical layout_width="0dp" layout_weight="1" layout_height="32dp">
        <text id="vulkanFastTime" text="" textSize="11sp" gravity="center" textColor="#4caf50" />
        <text id="vulkanAccurateTime" text="" textSize="11sp" gravity="center" textColor="#ff9800" />
      </vertical>
    </horizontal>
    <horizontal id="deviceRow" marginBottom="2dp">
      <button id="cpuBtn" text="CPU" layout_width="0dp" layout_weight="1" />
      <button id="gpuBtn" text="GPU" layout_width="0dp" layout_weight="1" />
      <button id="vulkanBtn" text="Vulkan" layout_width="0dp" layout_weight="1" />
    </horizontal>
    <horizontal id="modelRow" marginBottom="10dp">
      <button id="fastBtn" text="快速" layout_width="0dp" layout_weight="1" />
      <button id="accurateBtn" text="精确" layout_width="0dp" layout_weight="1" />
    </horizontal>
    <horizontal marginBottom="2dp" marginTop="5dp" gravity="center">
      <text text="精度" textSize="12sp" textColor="#4f4f4f" layout_width="25dp" />
      <seekbar id="sizeSeekBar" layout_width="0dp" layout_weight="1" layout_height="wrap_content" max="12" />
      <text id="sizeValue" text="" textSize="11sp" textColor="#4caf50" layout_width="25dp" gravity="center" />
    </horizontal>
    <text text="精度越高，可识别文字越小，文本越长" textSize="10sp" gravity="center" textColor="#676767" />
    <button id="captureAndOcr" text="截图识别" />
    <horizontal marginTop="5dp">
      <button id="clearBtn" layout_weight="1" text="清除" />
      <button id="closeBtn" layout_weight="1" text="退出" />
    </horizontal>
  </vertical>
);
// 拖动功能 - 限制边界（优化版）
let downX = 0, downY = 0
let originalX = 0, originalY = 0
let windowX = 0, windowY = 0

// 拖动时记录位置
clickButtonWindow.dragBar.setOnTouchListener(function (view, event) {
  let action = event.getAction()
  if (action == android.view.MotionEvent.ACTION_DOWN) {
    downX = event.getRawX()
    downY = event.getRawY()
    originalX = clickButtonWindow.getX()
    originalY = clickButtonWindow.getY()
    return true
  } else if (action == android.view.MotionEvent.ACTION_MOVE) {
    let dx = event.getRawX() - downX
    let dy = event.getRawY() - downY
    let newX = originalX + dx
    let newY = originalY + dy

    // 边界限制
    let maxX = device.width - clickButtonWindow.getWidth()
    let maxY = device.height - clickButtonWindow.getHeight()

    newX = Math.max(0, Math.min(maxX, newX))
    newY = Math.max(0, Math.min(maxY, newY))

    clickButtonWindow.setPosition(newX, newY)
    // 记录位置
    windowX = newX
    windowY = newY
    return true
  }
  return false
})
// 记录每个设备+模型的识别时间
let recognitionTime = {
  CPU: { fast: null, accurate: null },
  GPU: { fast: null, accurate: null },
  Vulkan: { fast: null, accurate: null }
}

// 更新设备按钮下方的时间显示
function updateDeviceTimeDisplay() {
  ui.run(() => {
    // CPU时间
    clickButtonWindow.cpuFastTime.setText(recognitionTime.CPU.fast ? "快:" + recognitionTime.CPU.fast + "ms" : "")
    clickButtonWindow.cpuAccurateTime.setText(recognitionTime.CPU.accurate ? "精:" + recognitionTime.CPU.accurate + "ms" : "")

    // GPU时间
    clickButtonWindow.gpuFastTime.setText(recognitionTime.GPU.fast ? "快:" + recognitionTime.GPU.fast + "ms" : "")
    clickButtonWindow.gpuAccurateTime.setText(recognitionTime.GPU.accurate ? "精:" + recognitionTime.GPU.accurate + "ms" : "")

    // Vulkan时间
    clickButtonWindow.vulkanFastTime.setText(recognitionTime.Vulkan.fast ? "快:" + recognitionTime.Vulkan.fast + "ms" : "")
    clickButtonWindow.vulkanAccurateTime.setText(recognitionTime.Vulkan.accurate ? "精:" + recognitionTime.Vulkan.accurate + "ms" : "")
  })
}

// 当前选中的设备
let currentDevice = "CPU"

function updateButtonStyle() {
  ui.run(function () {
    let defaultColor = "#888888";  // 未选中灰色
    let selectedColor = "#4caf50"; // 选中绿色

    clickButtonWindow.cpuBtn.setBackgroundColor(colors.parseColor(currentDevice === "CPU" ? selectedColor : defaultColor));
    clickButtonWindow.gpuBtn.setBackgroundColor(colors.parseColor(currentDevice === "GPU" ? selectedColor : defaultColor));
    clickButtonWindow.vulkanBtn.setBackgroundColor(colors.parseColor(currentDevice === "Vulkan" ? selectedColor : defaultColor));
  });
}

// 当前选中的模型
let currentModel = "fast" // fast 或 accurate

function updateModelButtonStyle() {
  ui.run(function () {
    let defaultColor = "#888888";  // 未选中灰色
    let selectedColor = "#4caf50"; // 选中绿色

    clickButtonWindow.fastBtn.setBackgroundColor(colors.parseColor(currentModel === "fast" ? selectedColor : defaultColor));
    clickButtonWindow.accurateBtn.setBackgroundColor(colors.parseColor(currentModel === "accurate" ? selectedColor : defaultColor));
  });
}

// imageSize 选项
let currentImageSize = "256"

// 更新滑条位置和显示
function updateSizeSeekBar() {
  let index = currentImageSize / 32 - 4
  if (index >= 0) {
    ui.run(() => {
      clickButtonWindow.sizeSeekBar.setProgress(index)
      clickButtonWindow.sizeValue.setText(currentImageSize.toString())
    })
  }
}

// 滑条变化事件（带800ms防抖）
let sizeChangeTimer = null
clickButtonWindow.sizeSeekBar.setOnSeekBarChangeListener({
  onProgressChanged: function (seekbar, progress, fromUser) {
    if (fromUser) {
      currentImageSize = progress * 32 + 128
      clickButtonWindow.sizeValue.setText(currentImageSize.toString())

      // 清除之前的定时器
      if (sizeChangeTimer) {
        clearTimeout(sizeChangeTimer)
      }

      // 设置新的定时器，800ms后执行
      sizeChangeTimer = setTimeout(() => {
        threads.start(() => {
          reinitOcr()
          toastLog('精度已更改为: ' + currentImageSize)
        })
        sizeChangeTimer = null
      }, 800)
    }
  },
  onStartTrackingTouch: function (seekbar) { },
  onStopTrackingTouch: function (seekbar) { }
})

ui.run(function () {
  clickButtonWindow.setPosition(device.width / 2 - ~~(clickButtonWindow.getWidth() / 2), device.height * 0.4)
  updateButtonStyle()
  updateModelButtonStyle()
  updateSizeSeekBar()
})

// 重新初始化OCR配置（包含设备和模型）
function reinitOcr() {
  let config = {
    device: currentDevice,
    useSlim: currentModel === "fast",
    scoreThreshold: 0.5,
    imageSize: currentImageSize
  }
  console.log(config)
  paddle.initOcrWithConfig(config)
}

reinitOcr()
//captureAndOcr()

// 设备切换函数
function switchDevice(device) {
  if (currentDevice == device) return

  // 禁用设备按钮
  ui.run(() => {
    clickButtonWindow.cpuBtn.setEnabled(false)
    clickButtonWindow.gpuBtn.setEnabled(false)
    clickButtonWindow.vulkanBtn.setEnabled(false)
  })

  threads.start(() => {
    try {
      currentDevice = device
      reinitOcr()
      ui.run(() => updateButtonStyle())
      toastLog('识别设备已更改为: ' + device)
    } catch (e) {
      toastLog('切换设备失败: ' + e)
    } finally {
      ui.run(() => {
        clickButtonWindow.cpuBtn.setEnabled(true)
        clickButtonWindow.gpuBtn.setEnabled(true)
        clickButtonWindow.vulkanBtn.setEnabled(true)
      })
    }
  })
}

// 模型切换函数
function switchModel(model) {
  if (currentModel == model) return

  threads.start(() => {
    try {
      currentModel = model
      currentImageSize = model === "fast" ? 256 : 512
      reinitOcr()
      updateModelButtonStyle()
      updateSizeSeekBar()
      toastLog('识别模型已更改为: ' + (model === "fast" ? "快速" : "精确"))
    } catch (e) {
      toastLog('切换模型失败: ' + e)
    }
  })
}

// 绑定点击事件
clickButtonWindow.cpuBtn.click(() => switchDevice("CPU"))
clickButtonWindow.gpuBtn.click(() => switchDevice("GPU"))
clickButtonWindow.vulkanBtn.click(() => switchDevice("Vulkan"))

// 绑定模型点击事件
clickButtonWindow.fastBtn.click(() => switchModel("fast"))
clickButtonWindow.accurateBtn.click(() => switchModel("accurate"))

// 点击识别
clickButtonWindow.captureAndOcr.click(function () {
  if (result.length) clearPaint();
  result = []

  ui.run(function () {
    clickButtonWindow.setPosition(device.width, device.height)
  })
  setTimeout(() => {
    threads.start(() => {
      captureAndOcr()
      if (window.canvas.updateCanvas) {
        window.canvas.updateCanvas()
      }
      ui.run(function () {
        if (windowX === 0 && windowY === 0) {
          // 默认居中
          clickButtonWindow.setPosition(device.width / 2 - ~~(clickButtonWindow.getWidth() / 2), device.height * 0.4)
        } else {
          clickButtonWindow.setPosition(windowX, windowY)
        }
      })
    })
  }, 500)
})

function clearPaint() {
  ui.run(() => {
    // 清空识别结果
    result = []
    // 重置 paint 到初始状态
    paint.reset()
    // 重新设置基本属性（如果需要保留基本样式）
    paint.setStrokeWidth(1)
    paint.setTypeface(Typeface.DEFAULT_BOLD)
    paint.setTextAlign(Paint.Align.LEFT)
    paint.setAntiAlias(true)
    // 刷新画布
    if (window.canvas.updateCanvas) {
      window.canvas.updateCanvas()
    }
  })
}

clickButtonWindow.clearBtn.click(clearPaint)

// 点击关闭
clickButtonWindow.closeBtn.click(function () {
  exit()
})

let Typeface = android.graphics.Typeface
let paint = new Paint()
paint.setStrokeWidth(1)
paint.setTypeface(Typeface.DEFAULT_BOLD)
paint.setTextAlign(Paint.Align.LEFT)
paint.setAntiAlias(true)
paint.setStrokeJoin(Paint.Join.ROUND)
paint.setDither(true)
window.canvas.on('draw', function (canvas) {
  if (!running || capturing) {
    return
  }
  // 清空内容
  canvas.drawColor(0xFFFFFF, android.graphics.PorterDuff.Mode.CLEAR)
  if (result && result.length > 0) {
    for (let i = 0; i < result.length; i++) {
      let ocrResult = result[i]
      drawRectAndText(ocrResult.words + ' #信心:' + ocrResult.confidence.toFixed(2), ocrResult.bounds, '#60bb60', canvas, paint);
    }
  }
})

setInterval(() => { }, 10000)
events.on('exit', () => {
  // 标记停止 避免canvas导致闪退
  running = false
  // 撤销监听
  window.canvas.removeAllListeners()

})

/**
 * 绘制文本和方框
 *
 * @param {*} desc
 * @param {*} rect
 * @param {*} colorStr
 * @param {*} canvas
 * @param {*} paint
 */
function drawRectAndText(desc, rect, colorStr, canvas, paint) {
  let color = colors.parseColor(colorStr)

  paint.setStrokeWidth(1)
  paint.setStyle(Paint.Style.STROKE)
  // 反色
  paint.setARGB(255, 255 - (color >> 16 & 0xff), 255 - (color >> 8 & 0xff), 255 - (color & 0xff))
  canvas.drawRect(rect, paint)
  paint.setARGB(255, color >> 16 & 0xff, color >> 8 & 0xff, color & 0xff)
  paint.setStrokeWidth(1)
  paint.setTextSize(20)
  paint.setStyle(Paint.Style.FILL)
  canvas.drawText(desc, rect.left, rect.top, paint)
  paint.setTextSize(10)
  paint.setStrokeWidth(1)
  paint.setARGB(255, 0, 0, 0)
}

/**
 * 获取状态栏高度
 *
 * @returns
 */
function getStatusBarHeightCompat() {
  let result = 0
  let resId = context.getResources().getIdentifier("status_bar_height", "dimen", "android")
  if (resId > 0) {
    result = context.getResources().getDimensionPixelOffset(resId)
  }
  if (result <= 0) {
    result = context.getResources().getDimensionPixelOffset(R.dimen.dimen_25dp)
  }
  return result
}
