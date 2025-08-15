package com.stardust.autojs.core.image.capture

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.stardust.autojs.core.image.ImageWrapper
import com.stardust.util.ScreenMetrics
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Created by Stardust on 2017/5/17.
 * Improvedd by TonyJiangWJ(https://github.com/TonyJiangWJ).
 * From [TonyJiangWJ/Auto.js](https://github.com/TonyJiangWJ/Auto.js)
 */
class ScreenCapturer(
    private val mediaProjection: MediaProjection,
    orientation: Int = 0,
    private val screenDensity: Int = ScreenMetrics.getDeviceScreenDensity(),
    private val mHandler: Handler = Handler(Looper.getMainLooper())
) {
    private var mVirtualDisplay: VirtualDisplay
    private var mImageReader: ImageReader
    private val executor = Executors.newSingleThreadExecutor()

    private val cachedImageBitmap = AtomicReference<Bitmap?>()
    private val latestImage = AtomicReference<Image>()
    private val publishSubject = PublishSubject.create<ImageWrapper>()

    @Volatile
    var available = true

    private var mDetectedOrientation = 0


    init {
        val screenHeight = ScreenMetrics.getOrientationAwareScreenHeight(orientation)
        val screenWidth = ScreenMetrics.getOrientationAwareScreenWidth(orientation)
        mImageReader = createImageReader(screenWidth, screenHeight)
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                available = false
                release()
            }
        }, mHandler)
        mVirtualDisplay = createVirtualDisplay(screenWidth, screenHeight, screenDensity)
    }

    private fun createImageReader(width: Int, height: Int): ImageReader {
        return ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).apply {
            setOnImageAvailableListener({
                try {
                    executor.submit {
                        val image = acquireLatestImage()
                        if (image == null) return@submit
                        if (publishSubject.hasObservers()) {
                            val bitmap = ImageWrapper.toBitmap(image)
                            image.close()
                            cachedImageBitmap.set(bitmap)
                            publishSubject.onNext(ImageWrapper.ofBitmap(bitmap))
                        } else {
                            latestImage.getAndSet(image)?.close()
                        }
                    }
                } catch (_: Exception) {
                }
            }, mHandler)
        }
    }

    private fun createVirtualDisplay(width: Int, height: Int, screenDensity: Int): VirtualDisplay {
        return mediaProjection.createVirtualDisplay(
            LOG_TAG,
            width, height, screenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mImageReader.surface, null, null
        )
    }

    fun setOrientation(orientation: Int, context: Context) {
        mDetectedOrientation = context.resources.configuration.orientation
        refreshVirtualDisplay(if (orientation == ORIENTATION_AUTO) mDetectedOrientation else orientation)
    }

    private fun refreshVirtualDisplay(orientation: Int) = synchronized(this) {
        latestImage.set(null)
        mImageReader.close()
        val screenHeight = ScreenMetrics.getOrientationAwareScreenHeight(orientation)
        val screenWidth = ScreenMetrics.getOrientationAwareScreenWidth(orientation)
        mImageReader = createImageReader(screenWidth, screenHeight)
        mVirtualDisplay.surface = mImageReader.surface
        mVirtualDisplay.resize(screenWidth, screenHeight, screenDensity)
    }

    fun capture(): Image? {
        if (!available) throw Exception("ScreenCapturer is not available")
        val newImage = latestImage.getAndSet(null)
        return newImage
    }

    fun registerAsyncCapture(scheduler: Scheduler, onNext: Consumer<ImageWrapper>): Disposable {
        val eventProcessing = AtomicReference(false)
        return publishSubject.filter {
            eventProcessing.getAndSet(true) != true
        }.observeOn(scheduler).subscribe {
            try {
                onNext.accept(it)
            } finally {
                eventProcessing.set(false)
            }
        }
    }

    fun createImageWrapper(image: Image): ImageWrapper {
        val bitmap = ImageWrapper.toBitmap(image)
        image.close()
        cachedImageBitmap.set(bitmap)
        return ImageWrapper.ofBitmap(bitmap)
    }

    suspend fun captureImageWrapper(): ImageWrapper {
        val imageWrapper = synchronized(this) {
            val image = capture()
            if (image != null) createImageWrapper(image) else null
        }
        if (imageWrapper != null) return imageWrapper
        cachedImageBitmap.get()?.let {
            Log.i(LOG_TAG, "Using cached image")
            return ImageWrapper.ofBitmap(it)
        }
        //在缓存图像均不可用的情况下等待2秒取得截图，否则抛出错误
        return withTimeout(2000) {
            var img: ImageWrapper? = null
            while (true) {
                delay(200)
                img = synchronized(this) {
                    capture()?.let {
                        createImageWrapper(it)
                    }
                }
                if (img !== null) {
                    break
                }
            }
            img!!
        }
    }

    fun release() = synchronized(this) {
        available = false
        mVirtualDisplay.release()
        mImageReader.close()
        executor.shutdown()
        cachedImageBitmap.set(null)
        latestImage.getAndSet(null)?.close()
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        release()
    }

    companion object {
        @JvmStatic
        val ORIENTATION_AUTO = Configuration.ORIENTATION_UNDEFINED

        @JvmStatic
        val ORIENTATION_LANDSCAPE = Configuration.ORIENTATION_LANDSCAPE

        @JvmStatic
        val ORIENTATION_PORTRAIT = Configuration.ORIENTATION_PORTRAIT
        private const val LOG_TAG = "ScreenCapturer"
    }
}