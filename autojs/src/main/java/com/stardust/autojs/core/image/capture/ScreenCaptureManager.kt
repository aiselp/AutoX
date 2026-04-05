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
import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException

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
                } finally {
                    serviceConnected.complete(Unit)
                    context.unbindService(this)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // 服务意外断开时处理
                serviceConnected.completeExceptionally(IllegalStateException("Service disconnected unexpectedly"))
            }
        }

        // 先绑定服务再启动（避免延迟）
        val serviceIntent = Intent(context, CaptureForegroundService::class.java)
        context.bindService(
            serviceIntent,
            connection,
            Context.BIND_AUTO_CREATE
        )

        // 绑定后立即启动服务
        context.startForegroundService(serviceIntent)

        delay(50)  // 短暂等待服务启动

        serviceConnected.await()
    }

    override fun requestScreenCaptureLegacy(context: Context, orientation: Int): ScriptPromiseAdapter {
        val promiseAdapter = ScriptPromiseAdapter()

        if (screenCapture?.available == true) {
            screenCapture?.setOrientation(orientation, context)
            promiseAdapter.resolve(true)
            return promiseAdapter
        }

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
                    } catch (e: Exception) {
                        Log.e("SCREEN_LEGACY", "Manager-创建失败: ${e.message}")
                        promiseAdapter.resolve(false)
                    } finally {
                        scope.cancel()
                        manager.currentCoroutineScope = null
                    }
                }
            }
        }

        ScreenCaptureRequestActivity.request(context, callback)
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