package com.stardust.autojs.core.image.capture

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import com.github.aiselp.autox.activity.TransparentActivity
import com.stardust.autojs.core.image.capture.ScreenCaptureRequester.Callback
import com.stardust.autojs.core.util.ScriptPromiseAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/** 绑定前台截图服务的最长等待时间；超时后失败返回，避免脚本线程永久挂起。 */
private const val BIND_TIMEOUT_MILLIS = 10_000L

class ScreenCaptureManager : ScreenCaptureRequester {
    @Volatile
    override var screenCapture: ScreenCapturer? = null
    private var mediaProjection: MediaProjection? = null
    private var currentCoroutineScope: CoroutineScope? = null
    private var currentConnection: ServiceConnection? = null

    override suspend fun requestScreenCapture(context: Context, orientation: Int) {
        if (screenCapture?.available == true) {
            screenCapture?.setOrientation(orientation, context)
            return
        }

        recycle()
        val result = run {
            val result = CompletableDeferred<Intent>()
            TransparentActivity.requestNewActivity(context) { activity ->
                activity.registerForActivityResult(ScreenCaptureRequester()) { data ->
                    activity.finish()
                    if (data != null) {
                        result.complete(data)
                    } else result.completeExceptionally(CancellationException("data is null"))
                }.launch(activity)
            }
            result.await()
        }

        // 使用服务绑定确保服务就绪
        setupScreenCapture(result, orientation, context)
    }

    private suspend fun setupScreenCapture(result: Intent, orientation: Int, context: Context) {
        val serviceConnected = CompletableDeferred<Unit>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    // 服务已连接，安全获取mediaProjection
                    mediaProjection =
                        (context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                            .getMediaProjection(Activity.RESULT_OK, result)
                    CaptureForegroundService.setMediaProjection(context, mediaProjection!!)
                    screenCapture = ScreenCapturer(mediaProjection!!, orientation)
                    serviceConnected.complete(Unit)
                } catch (e: Exception) {
                    // 原来 finally 里无条件 complete(Unit)，getMediaProjection 抛异常时
                    // 会伪装成成功，脚本随后拿到不可用的截屏器。
                    serviceConnected.completeExceptionally(e)
                } finally {
                    runCatching { context.unbindService(this) }
                    currentConnection = null
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                currentConnection = null
                serviceConnected.completeExceptionally(IllegalStateException("Service disconnected unexpectedly"))
            }
        }

        val serviceIntent = Intent(context, CaptureForegroundService::class.java)
        val bound = context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            serviceConnected.completeExceptionally(
                IllegalStateException("bind CaptureForegroundService failed")
            )
        } else {
            currentConnection = connection
            context.startForegroundService(serviceIntent)
        }

        try {
            // 服务被系统拒绝或迟迟不回调时不能无限挂起脚本线程。
            withTimeout(BIND_TIMEOUT_MILLIS) { serviceConnected.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // 从未回调时 onServiceConnected 的 finally 不会解绑，这里补上。
            currentConnection?.let {
                runCatching { context.unbindService(it) }
                currentConnection = null
            }
            throw IllegalStateException("CaptureForegroundService bind timeout", e)
        }
    }

    override fun requestScreenCaptureLegacy(context: Context, orientation: Int): ScriptPromiseAdapter {
        val promiseAdapter = ScriptPromiseAdapter()
        var retryCount = 0
        val maxRetry = 3

        fun doRequest() {
            if (screenCapture?.isValid() == true) {
                screenCapture?.setOrientation(orientation, context)
                promiseAdapter.resolve(true)
                return
            }

            recycle()

            val weakManager = WeakReference(this)
            val callback = object : Callback {
                override fun onRequestResult(result: Int, data: Intent?) {
                    val manager = weakManager.get()
                    if (manager == null) {
                        promiseAdapter.resolve(false)
                        return
                    }

                    val scope = CoroutineScope(Dispatchers.Main)
                    manager.currentCoroutineScope = scope

                    scope.launch {
                        try {
                            if (result == Activity.RESULT_OK && data != null) {
                                manager.setupScreenCapture(data, orientation, context)
                                promiseAdapter.resolve(true)
                            } else {
                                promiseAdapter.resolve(false)
                            }
                        } catch (e: SecurityException) {
                            if (e.message?.contains("non-current") == true && retryCount < maxRetry) {
                                retryCount++
                                Log.w("SCREEN_LEGACY", "MediaProjection expired, retrying ($retryCount/$maxRetry)")
                                delay(100.milliseconds)
                                doRequest()
                            } else {
                                Log.e("SCREEN_LEGACY", "Failed after $maxRetry retries", e)
                                promiseAdapter.resolve(false)
                            }
                        } catch (e: Exception) {
                            Log.e("SCREEN_LEGACY", "创建失败: ${e.message}")
                            promiseAdapter.resolve(false)
                        } finally {
                            scope.cancel()
                            manager.currentCoroutineScope = null
                        }
                    }
                }
            }

            ScreenCaptureRequestActivity.request(context, callback)
        }

        doRequest()
        return promiseAdapter
    }

    class ScreenCaptureRequester : ActivityResultContract<Context, Intent?>() {
        override fun createIntent(context: Context, input: Context): Intent {
            return (input.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Intent? {
            return if (resultCode != Activity.RESULT_OK) {
                null
            } else intent
        }
    }

    override fun recycle() {
        // 取消协程
        currentCoroutineScope?.cancel()
        currentCoroutineScope = null

        // 清理连接
        currentConnection = null

        // 释放截图器
        screenCapture?.release()
        screenCapture = null

        // 停止并释放 MediaProjection
        mediaProjection?.stop()
        mediaProjection = null
    }
}