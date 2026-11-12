package com.stardust.autojs.runtime.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Base64
import android.util.Log
import android.view.Gravity
import androidx.core.graphics.createBitmap
import com.stardust.autojs.annotation.ScriptVariable
import com.stardust.autojs.core.image.ColorFinder
import com.stardust.autojs.core.image.ImageWrapper
import com.stardust.autojs.core.image.TemplateMatching
import com.stardust.autojs.core.image.TemplateMatching.MATCHING_METHOD_DEFAULT
import com.stardust.autojs.core.image.capture.ScreenCapturer
import com.stardust.autojs.core.image.capture.ScreenCaptureRequester
import com.stardust.autojs.core.opencv.Mat
import com.stardust.autojs.core.opencv.OpenCVHelper
import com.stardust.autojs.core.ui.inflater.util.Drawables
import com.stardust.autojs.core.util.ScriptPromiseAdapter
import com.stardust.autojs.runtime.ScriptRuntime
import com.stardust.pio.UncheckedIOException
import com.stardust.util.ScreenMetrics
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import kotlinx.coroutines.runBlocking
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/** legacy 截图权限请求的默认超时：给系统授权与内部重试留足时间。 */
private const val DEFAULT_CAPTURE_TIMEOUT_MS = 15_000L

/**
 * Created by Stardust on 2017/5/20.
 */
class Images(
    private val mContext: Context,
    private val mScriptRuntime: ScriptRuntime,
    private val mScreenCaptureRequester: ScreenCaptureRequester
) {
    private val mScreenMetrics: ScreenMetrics = mScriptRuntime.screenMetrics
    private val disposables = mutableListOf<Disposable>()

    @ScriptVariable
    val colorFinder: ColorFinder = ColorFinder(mScreenMetrics)

    fun requestScreenCapture(orientation: Int): Boolean = runBlocking {
        try {
            mScreenCaptureRequester.requestScreenCapture(
                mContext, orientation
            )
            captureScreen()
            true
        } catch (e: Exception) {
            mScriptRuntime.toast(e.message)
            Log.e(Images::class.java.name, "请求截图权限失败", e)
            false
        }
    }

    // legacy 截图权限统一走文件末尾的同步版本（Boolean），不再提供
    // 同名 1 参 Promise 重载，避免两种返回语义与 Rhino 重载歧义。

    fun stopScreenCapturer() {
        mScreenCaptureRequester.recycle()
    }

    @Synchronized
    fun captureScreen(): ImageWrapper {
        val screenCapture = mScreenCaptureRequester.screenCapture
        checkNotNull(screenCapture) { SecurityException("No screen capture permission") }
        return runBlocking {
            screenCapture.captureImageWrapper()
        }
    }

    fun registerAsyncCapture(onNext: Consumer<ImageWrapper>): Disposable {
        val screenCapture = mScreenCaptureRequester.screenCapture
        checkNotNull(screenCapture) { SecurityException("No screen capture permission") }
        val scheduler = AndroidSchedulers.from(mScriptRuntime.loopers.servantLooper)
        var disposable: Disposable? = null

        // 使用弱引用包装回调
        val weakRuntime = java.lang.ref.WeakReference(mScriptRuntime)
        val weakImages = java.lang.ref.WeakReference(this)

        disposable = screenCapture.registerAsyncCapture(scheduler, {
            try {
                val images = weakImages.get()
                if (images == null) {
                    disposable?.dispose()
                    return@registerAsyncCapture
                }
                onNext.accept(it)
            } catch (e: Throwable) {
                disposable?.dispose()
                weakRuntime.get()?.exit(e)
            }
        }).also {
            disposables.add(it)
        }
        return disposable
    }

    fun captureScreen(path: String): Boolean {
        val rpath = mScriptRuntime.files.path(path)
        val image = captureScreen()
        image.saveTo(rpath)
        return true
    }

    fun copy(image: ImageWrapper): ImageWrapper {
        return image.clone()
    }

    @Throws(IOException::class)
    fun save(image: ImageWrapper, path: String?, format: String, quality: Int): Boolean {
        val compressFormat = parseImageFormat(format)
            ?: throw IllegalArgumentException("unknown format $format")
        val bitmap = image.bitmap
        val outputStream = FileOutputStream(mScriptRuntime.files.path(path))
        return outputStream.use { out ->
            val compress = bitmap.compress(compressFormat, quality, out)
            out.flush()
            compress
        }
    }

    fun rotate(img: ImageWrapper, x: Float, y: Float, degree: Float): ImageWrapper {
        val matrix = Matrix()
        matrix.postRotate(degree, x, y)
        return ImageWrapper.ofBitmap(
            Bitmap.createBitmap(
                img.bitmap,
                0,
                0,
                img.width,
                img.height,
                matrix,
                true
            )
        )
    }

    fun clip(img: ImageWrapper, x: Int, y: Int, w: Int, h: Int): ImageWrapper? {
        return ImageWrapper.ofBitmap(Bitmap.createBitmap(img.bitmap, x, y, w, h))
    }

    fun read(path: String): ImageWrapper? {
        val bitmap = BitmapFactory.decodeFile(mScriptRuntime.files.path(path))
        return ImageWrapper.ofBitmap(bitmap)
    }

    fun fromBase64(data: String): ImageWrapper? {
        return ImageWrapper.ofBitmap(Drawables.loadBase64Data(data))
    }

    fun toBase64(wrapper: ImageWrapper, format: String, quality: Int): String {
        return Base64.encodeToString(toBytes(wrapper, format, quality), Base64.NO_WRAP)
    }

    fun toBytes(wrapper: ImageWrapper, format: String, quality: Int): ByteArray {
        val compressFormat = parseImageFormat(format)
            ?: throw IllegalArgumentException("unknown format $format")
        val bitmap = wrapper.bitmap
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(compressFormat, quality, outputStream)
        return outputStream.toByteArray()
    }

    fun fromBytes(bytes: ByteArray): ImageWrapper {
        return ImageWrapper.ofBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    private fun parseImageFormat(format: String): CompressFormat? {
        when (format) {
            "png" -> return CompressFormat.PNG
            "jpeg", "jpg" -> return CompressFormat.JPEG
            "webp" -> return CompressFormat.WEBP
        }
        return null
    }

    fun load(src: String): ImageWrapper? {
        return try {
            val url = URL(src)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input = connection.inputStream
            val bitmap = BitmapFactory.decodeStream(input)
            ImageWrapper.ofBitmap(bitmap)
        } catch (e: IOException) {
            null
        }
    }

    fun releaseScreenCapturer() {
        disposables.forEach { it.dispose() }
        disposables.clear()
        try {
            mScreenCaptureRequester.recycle()
        } catch (e: Exception) {
            Log.e(Images::class.java.name, "Error recycling screen capture", e)
        }
    }

    @JvmOverloads
    fun findImage(
        image: ImageWrapper?,
        template: ImageWrapper?,
        threshold: Float = 0.9f,
        rect: Rect? = null
    ): Point? {
        return findImage(image, template, 0.7f, threshold, rect, TemplateMatching.MAX_LEVEL_AUTO)
    }

    @JvmOverloads
    fun findImage(
        image: ImageWrapper?,
        template: ImageWrapper?,
        weakThreshold: Float,
        threshold: Float,
        rect: Rect?,
        maxLevel: Int,
        transparentMask: Boolean = false,
        useGrayscale: Boolean = false,
        matchMethod: Int = MATCHING_METHOD_DEFAULT
    ): Point? {
        initOpenCvIfNeeded()
        if (image == null) throw NullPointerException("image = null")
        if (template == null) throw NullPointerException("template = null")
        var src = image.mat
        if (rect != null) {
            src = Mat(src, rect)
        }
        val point = TemplateMatching.fastTemplateMatching(
            src, template.mat, matchMethod,
            weakThreshold, threshold, maxLevel, transparentMask, useGrayscale
        )
        if (point != null) {
            if (rect != null) {
                point.x += rect.x.toDouble()
                point.y += rect.y.toDouble()
            }
            point.x = mScreenMetrics.scaleX(point.x.toInt()).toDouble()
            point.y = mScreenMetrics.scaleX(point.y.toInt()).toDouble()
        }
        if (src !== image.mat) {
            OpenCVHelper.release(src)
        }
        return point
    }

    @JvmOverloads
    fun matchTemplate(
        image: ImageWrapper?,
        template: ImageWrapper?,
        weakThreshold: Float,
        threshold: Float,
        rect: Rect?,
        maxLevel: Int,
        limit: Int,
        transparentMask: Boolean = false,
        useGrayscale: Boolean = false,
        matchMethod: Int = MATCHING_METHOD_DEFAULT
    ): List<TemplateMatching.Match> {
        return matchMultiTemplates(
            image,
            listOf(template),
            weakThreshold,
            threshold,
            rect,
            maxLevel,
            limit,
            transparentMask,
            useGrayscale,
            matchMethod
        )
    }

    @JvmOverloads
    fun matchMultiTemplates(
        image: ImageWrapper?,
        templates: List<ImageWrapper?>,
        weakThreshold: Float,
        threshold: Float,
        rect: Rect?,
        maxLevel: Int,
        limit: Int,
        transparentMask: Boolean = false,
        useGrayscale: Boolean = false,
        matchMethod: Int = MATCHING_METHOD_DEFAULT
    ): List<TemplateMatching.Match> {
        initOpenCvIfNeeded()
        if (image == null) throw NullPointerException("image = null")
        if (templates.isEmpty() || templates.any { it == null }) throw NullPointerException("template = null")
        var src = image.mat
        if (rect != null) {
            src = Mat(src, rect)
        }

        val result = TemplateMatching.fastMultiTemplateMatching(
            src, templates.map { it?.mat }, matchMethod,
            weakThreshold, threshold, maxLevel, limit, transparentMask, useGrayscale
        )
        for (match in result) {
            val point = match.point
            if (rect != null) {
                point.x += rect.x.toDouble()
                point.y += rect.y.toDouble()
            }
            match.width *= mScreenMetrics.scaleX(1)
            match.height *= mScreenMetrics.scaleY(1)
            point.x = mScreenMetrics.scaleX(point.x.toInt()).toDouble()
            point.y = mScreenMetrics.scaleY(point.y.toInt()).toDouble()
        }
        if (src !== image.mat) {
            OpenCVHelper.release(src)
        }
        return result
    }

    fun newMat(): Mat {
        return Mat()
    }

    fun newMat(mat: Mat?, roi: Rect?): Mat {
        return Mat(mat, roi)
    }

    fun initOpenCvIfNeeded() {
        if (OpenCVHelper.isInitialized.isCompleted) {
            return
        }
        val currentActivity = mScriptRuntime.app.currentActivity
        val context = currentActivity ?: mContext
        mScriptRuntime.console.info("opencv initializing")
        OpenCVHelper.initIfNeeded(context)
        mScriptRuntime.console.info("opencv initialized")
    }

    fun pixel(image: ImageWrapper?, x: Int, y: Int): Int {
        if (image == null) {
            throw NullPointerException("image = null")
        }
        return image.pixel(x, y)
    }

    fun concat(img1: ImageWrapper, img2: ImageWrapper, direction: Int): ImageWrapper {
        var img1 = img1
        var img2 = img2
        require(
            listOf(
                Gravity.LEFT,
                Gravity.RIGHT,
                Gravity.TOP,
                Gravity.BOTTOM
            ).contains(direction)
        ) { "unknown direction $direction" }
        val width: Int
        val height: Int
        if (direction == Gravity.LEFT || direction == Gravity.TOP) {
            val tmp = img1
            img1 = img2
            img2 = tmp
        }
        if (direction == Gravity.LEFT || direction == Gravity.RIGHT) {
            width = img1.width + img2.width
            height = Math.max(img1.height, img2.height)
        } else {
            width = Math.max(img1.width, img2.width)
            height = img1.height + img2.height
        }
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        if (direction == Gravity.LEFT || direction == Gravity.RIGHT) {
            canvas.drawBitmap(img1.bitmap, 0f, ((height - img1.height) / 2).toFloat(), paint)
            canvas.drawBitmap(
                img2.bitmap,
                img1.width.toFloat(),
                ((height - img2.height) / 2).toFloat(),
                paint
            )
        } else {
            canvas.drawBitmap(img1.bitmap, ((width - img1.width) / 2).toFloat(), 0f, paint)
            canvas.drawBitmap(
                img2.bitmap,
                ((width - img2.width) / 2).toFloat(),
                img1.height.toFloat(),
                paint
            )
        }
        return ImageWrapper.ofBitmap(bitmap)
    }

    fun saveBitmap(bitmap: Bitmap, path: String?) {
        try {
            bitmap.compress(CompressFormat.PNG, 100, FileOutputStream(path))
        } catch (e: FileNotFoundException) {
            throw UncheckedIOException(e)
        }
    }

    fun scaleBitmap(origin: Bitmap?, newWidth: Int, newHeight: Int): Bitmap? {
        if (origin == null) {
            return null
        }
        val height = origin.height
        val width = origin.width
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        return Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false)
    }

    // ========== 新增方法 ==========

    /**
     * 获取图像的亮度值
     * @param image 图像
     * @param sampleCount 采样数量
     * @param region 采样区域
     * @return 亮度值
     */
    @JvmOverloads
    fun getBrightness(image: ImageWrapper?, sampleCount: Int = 50, region: Rect? = null): Float {
        if (image == null) {
            throw NullPointerException("image = null")
        }
        val img = image.mat
        val w = img.cols()
        val h = img.rows()
        var startX = 0
        var startY = 0
        var endX = w
        var endY = h

        if (region != null) {
            startX = region.x.coerceAtLeast(0)
            startY = region.y.coerceAtLeast(0)
            endX = (region.x + region.width).coerceAtMost(w)
            endY = (region.y + region.height).coerceAtMost(h)
        }

        val regionW = endX - startX
        val regionH = endY - startY
        if (regionW <= 0 || regionH <= 0) return 0f

        // sampleCount 为 0/负数时原本会除零得到 Infinity，这里收敛到合法区间
        val samples = sampleCount.coerceIn(1, regionW * regionH)
        val step = maxOf(1, sqrt((regionW * regionH).toDouble() / samples).toInt())
        var total = 0.0
        var count = 0
        val channels = img.channels()

        // OpenCV Mat.get(row, col) = (y, x)
        for (y in startY until endY step step) {
            for (x in startX until endX step step) {
                val pixel = img.get(y, x)
                val brightness = when (channels) {
                    1, 2 -> pixel[0]
                    3, 4 -> (pixel[0] + pixel[1] + pixel[2]) / 3.0
                    else -> pixel[0]
                }
                total += brightness
                count++
            }
        }

        return if (count > 0) (total / count).toFloat() else 0f
    }

    /**
     * 请求截图权限（legacy 通道，不跳转应用），同步等待结果。
     *
     * 提供 0 / 1 / 2 参三个重载，统一返回 Boolean：
     * - 无参：自动方向 + 默认超时
     * - 1 参：Boolean 当作横屏开关，Number 当作方向常量，其它值回退自动方向
     * - 2 参：(方向或横屏开关, 超时毫秒)
     *
     * 参数一律用 Any? 接收：JS 省略实参时 Rhino 传的是
     * org.mozilla.javascript.Undefined，基本类型无法匹配会报「找不到方法」。
     * 三个重载 arity 不同，Rhino 按实参个数选择，不会歧义；不要再引入返回
     * ScriptPromiseAdapter 的同名重载，那会重演仅返回类型不同的歧义问题。
     *
     * 用 CountDownLatch 而不是 runBlocking：脚本 forceStop 会 interrupt 线程，
     * latch.await 能响应中断；结果回调由 ScreenCaptureManager 在主线程触发，
     * 不依赖脚本线程的事件循环，因此不会死锁。
     */
    fun requestScreenCaptureLegacy(): Boolean =
        requestLegacyWithTimeout(ScreenCapturer.ORIENTATION_AUTO, DEFAULT_CAPTURE_TIMEOUT_MS)

    fun requestScreenCaptureLegacy(arg: Any?): Boolean =
        requestLegacyWithTimeout(normalizeOrientation(arg), DEFAULT_CAPTURE_TIMEOUT_MS)

    fun requestScreenCaptureLegacy(orientation: Any?, timeout: Any?): Boolean =
        requestLegacyWithTimeout(normalizeOrientation(orientation), normalizeTimeout(timeout))

    private fun normalizeOrientation(value: Any?): Int = when (value) {
        is Boolean -> if (value) ScreenCapturer.ORIENTATION_LANDSCAPE
        else ScreenCapturer.ORIENTATION_PORTRAIT
        is Number -> value.toInt()
        else -> ScreenCapturer.ORIENTATION_AUTO
    }

    private fun normalizeTimeout(value: Any?): Long =
        (value as? Number)?.toLong()?.takeIf { it > 0 } ?: DEFAULT_CAPTURE_TIMEOUT_MS

    private fun requestLegacyWithTimeout(orientation: Int, timeoutMs: Long): Boolean {
        val latch = CountDownLatch(1)
        val result = AtomicBoolean(false)
        val delivered = AtomicBoolean(false)

        requestLegacyCapture(orientation) { ok ->
            if (delivered.compareAndSet(false, true)) {
                result.set(ok)
                latch.countDown()
            }
        }

        return try {
            if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                result.get()
            } else {
                Log.w(Images::class.java.name, "请求截图权限超时: ${timeoutMs}ms")
                false
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(Images::class.java.name, "请求截图权限等待被中断")
            false
        }
    }

    /** 内部实现：把 ScreenCaptureRequester 的 Promise 转成一次性回调。 */
    private fun requestLegacyCapture(orientation: Int, callback: (Boolean) -> Unit) {
        try {
            val innerPromise = mScreenCaptureRequester.requestScreenCaptureLegacy(mContext, orientation)
            innerPromise.onResolve(object : ScriptPromiseAdapter.Callback {
                override fun call(arg: Any?) {
                    callback((arg as? Boolean) ?: true)
                }
            })
            innerPromise.onReject(object : ScriptPromiseAdapter.Callback {
                override fun call(arg: Any?) {
                    mScriptRuntime.toast(arg?.toString())
                    Log.e(Images::class.java.name, "请求截图权限失败: $arg")
                    callback(false)
                }
            })
        } catch (e: Exception) {
            mScriptRuntime.toast(e.message)
            Log.e(Images::class.java.name, "请求截图权限失败", e)
            callback(false)
        }
    }

    /**
     * 回收所有追踪的图像
     */
    fun recycleAllImages() {
        ImageWrapper.recycleAllTrackedImages()
    }

    /**
     * 获取存活图像数量
     */
    fun getAliveImageCount(): Int {
        return ImageWrapper.getTrackedImageCount()
    }

    /**
     * 启用图像追踪
     */
    fun enableImageTracking() {
        ImageWrapper.enableTracking()
    }

    /**
     * 禁用图像追踪
     */
    fun disableImageTracking() {
        ImageWrapper.disableTracking()
    }
}