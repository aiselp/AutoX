package com.stardust.autojs.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.stardust.autojs.R
import com.stardust.autojs.runtime.api.Console

class CustomToastView(
    private val context: Context,
    private val console: Console? = null
) {
    private val toastViews = mutableListOf<View>()
    private val toastHandler by lazy {
        val thread = HandlerThread("ToastThread")
        thread.start()
        Handler(thread.looper)
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val toastQueue = mutableListOf<ToastTask>()
    private var isShowing = false

    private data class ToastTask(
        val msg: String,
        val config: Config
    )

    data class Config(
        var log: Boolean = false,
        var duration: Long = 2000L,
        var gravity: Int = Gravity.BOTTOM,
        var xOffset: Int = 0,
        var yOffset: Int = 150,
        var alpha: Float = 1.0f,
        var bg: Int = "#DD333333".toColorInt(),
        var textColor: Int = Color.WHITE,
        var textSize: Float = 16f,
        var radius: Float = 8f,
        var padding: Int = 8,
        var icon: String? = null,
        var override: Boolean = false
    )

    private var def = Config()

    private fun parseConfig(config: Any?, target: Config) {
        if (config == null) return
        try {
            val map = config as? Map<*, *>
            map?.let { configMap ->
                configMap["log"]?.let { target.log = it as Boolean }
                configMap["duration"]?.let { target.duration = (it as Number).toLong() }
                configMap["gravity"]?.let { target.gravity = (it as Number).toInt() }
                configMap["xOffset"]?.let { target.xOffset = (it as Number).toInt() }
                configMap["yOffset"]?.let { target.yOffset = (it as Number).toInt() }
                configMap["alpha"]?.let { target.alpha = (it as Number).toFloat() }
                configMap["bg"]?.let { target.bg = (it as Number).toInt() }
                configMap["textColor"]?.let { target.textColor = (it as Number).toInt() }
                configMap["textSize"]?.let { target.textSize = (it as Number).toFloat() }
                configMap["radius"]?.let { target.radius = (it as Number).toFloat() }
                configMap["padding"]?.let { target.padding = (it as Number).toInt() }
                configMap["icon"]?.let {
                    target.icon = when (it) {
                        is String -> it.replace(Regex("^data:image/\\w+;base64,"), "")
                        else -> it.toString()
                    }
                }
                configMap["override"]?.let { target.override = it as Boolean }
            }
        } catch (e: Exception) {}
    }

    fun init(config: Any?) {
        if (config != null) {
            parseConfig(config, def)
        }
    }

    fun show(msg: String) {
        showInternal(msg, def)
    }

    fun show(msg: String, config: Any? = null) {
        val cfg = def.copy()
        if (config != null) {
            parseConfig(config, cfg)
        }
        if (cfg.override) {
            dismissAll()
            showInternal(msg, cfg)
        } else {
            toastQueue.add(ToastTask(msg, cfg))
            if (!isShowing) {
                showNext()
            }
        }
    }

    private fun showNext() {
        if (toastQueue.isEmpty()) {
            isShowing = false
            return
        }
        isShowing = true
        val task = toastQueue.removeAt(0)
        showInternal(task.msg, task.config)
    }

    private fun showInternal(msg: String, config: Config) {
        if (config.log) {
            console?.log(msg)
        }
        showFull(
            msg,
            config.duration,
            config.gravity,
            config.xOffset,
            config.yOffset,
            config.alpha,
            config.bg,
            config.textColor,
            config.textSize,
            config.radius,
            config.padding,
            config.icon
        )
    }

    private fun showFull(
        msg: String,
        duration: Long,
        _gravity: Int,
        xOffset: Int,
        yOffset: Int,
        alpha: Float,
        bg: Int,
        textColor: Int,
        textSize: Float,
        radius: Float,
        padding: Int,
        icon: String?
    ) {
        if (toastViews.size > 20) {
            dismissAll()
        }
        val view = LayoutInflater.from(context).inflate(R.layout.layout_custom_toast, null, false)
        val tv = view.findViewById<TextView>(R.id.tv_toast_message)
        tv.text = msg
        tv.setTextColor(textColor)
        tv.textSize = textSize

        val iv = view.findViewById<ImageView>(R.id.iv_toast_icon)
        if (!icon.isNullOrEmpty()) {
            val data = Base64.decode(icon, Base64.DEFAULT)
            iv.setImageBitmap(BitmapFactory.decodeByteArray(data, 0, data.size))
            iv.visibility = View.VISIBLE
        } else {
            iv.visibility = View.GONE
        }

        val drawable = GradientDrawable().apply {
            setColor(bg)
            cornerRadius = dipToPx(radius.toInt()).toFloat()
        }
        view.background = drawable
        view.setPadding(dipToPx(padding), dipToPx(padding / 2), dipToPx(padding), dipToPx(padding / 2))
        view.alpha = alpha

        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = _gravity
            x = xOffset
            y = yOffset
        }

        mainHandler.post {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(view, layoutParams)
            toastViews.add(view)
        }
        toastViews.add(view)
        toastHandler.postDelayed({
            dismissView(view)
        }, duration)
    }

    private fun dismissView(view: View) {
        mainHandler.post {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
            } catch (e: Exception) {}
            toastViews.remove(view)
            showNext()
        }
    }

    fun dismissAll() {
        toastHandler.removeCallbacksAndMessages(null)
        toastViews.forEach { view ->
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
            } catch (e: Exception) {}
        }
        toastViews.clear()
        toastQueue.clear()
        isShowing = false
    }

    private fun dipToPx(dip: Int) = (dip * context.resources.displayMetrics.density).toInt()
}