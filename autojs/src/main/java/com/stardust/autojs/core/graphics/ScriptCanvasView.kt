package com.stardust.autojs.core.graphics

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import com.stardust.autojs.core.eventloop.EventEmitter
import com.stardust.autojs.core.eventloop.IEventEmitter
import com.stardust.autojs.runtime.ScriptRuntime
import com.stardust.autojs.util.isUiThread

/**
 * Created by Stardust on 2018/3/16.
 */

@SuppressLint("ViewConstructor")
class ScriptCanvasView(context: Context, private val mScriptRuntime: ScriptRuntime) :
    TextureView(context), IEventEmitter by EventEmitter(mScriptRuntime.bridges),
    TextureView.SurfaceTextureListener {

    init {
        surfaceTextureListener = this
    }

    @Synchronized
    private fun performDraw() {
        val canvas: Canvas = lockCanvas() ?: return
        val scriptCanvas = ScriptCanvas()
        scriptCanvas.setCanvas(canvas)
        try {
            emit("draw", scriptCanvas, this@ScriptCanvasView)
        } catch (e: Exception) {
            if (isUiThread()) {
                mScriptRuntime.exit(e)
            } else throw e
        } finally {
            unlockCanvasAndPost(canvas)
        }
    }

    fun updateCanvas() {
        performDraw()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d(LOG_TAG, "onSurfaceTextureAvailable: ${this}, width = $width, height = $height")
        performDraw()
    }

    override fun onSurfaceTextureSizeChanged(
        surface: SurfaceTexture, width: Int, height: Int
    ) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        Log.d(LOG_TAG, "onSurfaceTextureDestroyed: $this")
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    companion object {
        private const val LOG_TAG = "ScriptCanvasView"
        fun defaultMaxListeners(): Int {
            return EventEmitter.defaultMaxListeners()
        }
    }
}
