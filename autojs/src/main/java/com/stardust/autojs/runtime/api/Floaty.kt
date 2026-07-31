package com.stardust.autojs.runtime.api

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import com.stardust.autojs.R
import com.stardust.autojs.core.floaty.BaseResizableFloatyWindow
import com.stardust.autojs.core.floaty.RawWindow
import com.stardust.autojs.core.floaty.RawWindow.RawFloaty
import com.stardust.autojs.core.ui.JsViewHelper
import com.stardust.autojs.runtime.ScriptRuntime
import com.stardust.autojs.runtime.exception.ScriptInterruptedException
import com.stardust.autojs.util.FloatingPermission
import com.stardust.enhancedfloaty.FloatyService
import com.stardust.util.UiHandler
import com.stardust.util.ViewUtil
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Created by Stardust on 2017/12/5.
 */
class Floaty(private val mUiHandler: UiHandler, ui: UI, private val mRuntime: ScriptRuntime) {
    private val mContext: Context = ContextThemeWrapper(mUiHandler.context, R.style.ScriptTheme)
    private val mWindows = CopyOnWriteArraySet<JsWindow>()

    private fun checkFloatingPermission() {
        try {
            FloatingPermission.waitForPermissionGranted(mContext)
        } catch (e: InterruptedException) {
            throw ScriptInterruptedException()
        }
    }

    fun window(supplier: BaseResizableFloatyWindow.ViewSupplier): JsResizableWindow {
        checkFloatingPermission()
        val window = JsResizableWindow(supplier)
        addWindow(window)
        return window
    }

    fun window(view: View): JsResizableWindow {
        checkFloatingPermission()

        val window = JsResizableWindow { _: Context?, _: ViewGroup? -> view }
        addWindow(window)
        return window
    }

    fun rawWindow(floaty: RawFloaty): JsRawWindow {
        checkFloatingPermission()

        val window = JsRawWindow(floaty)
        addWindow(window)
        return window
    }

    fun rawWindow(view: View): JsRawWindow {
        checkFloatingPermission()
        val window = JsRawWindow { _: Context?, _: ViewGroup? -> view }
        addWindow(window)
        return window
    }

    @Synchronized
    private fun addWindow(window: JsWindow) {
        mWindows.add(window)
    }

    @Synchronized
    private fun removeWindow(window: JsWindow): Boolean {
        return mWindows.remove(window)
    }

    @Synchronized
    fun closeAll() {
        for (window in mWindows) {
            window.close(false)
        }
        mWindows.clear()
    }

    @Synchronized
    fun checkPermission(): Boolean {
        return FloatingPermission.canDrawOverlays(mContext)
    }

    @Synchronized
    fun requestPermission() {
        FloatingPermission.manageDrawOverlays(mContext)
    }

    interface JsWindow {
        fun close(removeFromWindows: Boolean)
    }

    inner class JsRawWindow(floaty: RawFloaty) : JsWindow {
        private var mWindow: RawWindow = RawWindow(floaty, mUiHandler.context)
        private var mExitOnClose = false

        init {
            mUiHandler.context.startService(Intent(mUiHandler.context, FloatyService::class.java))

            //如果是ui线程则直接创建
            if (Looper.myLooper() == Looper.getMainLooper()) {
                FloatyService.addWindow(mWindow)
            } else { //否则放入ui线程
                mUiHandler.post { FloatyService.addWindow(mWindow) }
            }
        }

        fun findView(id: String?): View? {
            return JsViewHelper.findViewByStringId(mWindow.getContentView(), id)
        }

        val x: Int
            get() = mWindow.windowBridge.x

        val y: Int
            get() = mWindow.windowBridge.y

        val width: Int
            get() = mWindow.windowView.width

        val height: Int
            get() = mWindow.windowView.height

        fun setSize(w: Int, h: Int) {
            runWithWindow {
                mWindow.windowBridge.updateMeasure(w, h)
                ViewUtil.setViewMeasure(mWindow.windowView, w, h)
            }
        }

        fun setTouchable(touchable: Boolean) {
            runWithWindow { mWindow.setTouchable(touchable) }
        }

        fun setCoverStatusBar(cover: Boolean) {
            runWithWindow { mWindow.setCoverStatusBar(cover) }
        }

        private fun runWithWindow(r: Runnable) {
            mUiHandler.post(r)
        }

        fun setPosition(x: Int, y: Int) {
            runWithWindow { mWindow.windowBridge.updatePosition(x, y) }
        }

        fun exitOnClose() {
            mExitOnClose = true
        }

        fun requestFocus() {
            mWindow.requestWindowFocus()
        }

        fun disableFocus() {
            mWindow.disableWindowFocus()
        }

        fun close() {
            close(true)
        }

        override fun close(removeFromWindows: Boolean) {
            if (removeFromWindows && !removeWindow(this)) {
                return
            }
            runWithWindow {
                mWindow.close()
                if (mExitOnClose) {
                    mRuntime.exit()
                }
            }
        }
    }

    inner class JsResizableWindow(supplier: BaseResizableFloatyWindow.ViewSupplier) : JsWindow {
        private var mView: View? = null

        @Volatile
        private var mWindow: BaseResizableFloatyWindow =
            BaseResizableFloatyWindow(mContext) { context, parent ->
                supplier.inflate(context, parent).apply {
                    mView = this
                }
            }
        private var mExitOnClose = false

        init {
            mUiHandler.context.startService(Intent(mUiHandler.context, FloatyService::class.java))
            //如果是ui线程则直接创建
            if (Looper.myLooper() == Looper.getMainLooper()) {
                FloatyService.addWindow(mWindow)
            } else { //否则放入ui线程
                mUiHandler.post {
                    FloatyService.addWindow(mWindow)
                }
            }

            mWindow.setOnCloseButtonClickListener { close() }
            //setSize(mWindow.getWindowBridge().getScreenWidth() / 2, mWindow.getWindowBridge().getScreenHeight() / 2);
        }

        fun findView(id: String?): View? {
            return JsViewHelper.findViewByStringId(mView, id)
        }

        val x: Int
            get() = mWindow.windowBridge.x

        val y: Int
            get() = mWindow.windowBridge.y

        val width: Int
            get() = mWindow.rootView.width

        val height: Int
            get() = mWindow.rootView.height

        fun setSize(w: Int, h: Int) {
            runWithWindow {
                mWindow.windowBridge.updateMeasure(w, h)
                ViewUtil.setViewMeasure(mWindow.rootView, w, h)
            }
        }


        private fun runWithWindow(r: Runnable) {
            mUiHandler.post(r)
        }

        fun setPosition(x: Int, y: Int) {
            runWithWindow { mWindow.windowBridge.updatePosition(x, y) }
        }

        var isAdjustEnabled: Boolean
            get() = mWindow.isAdjustEnabled
            set(enabled) {
                runWithWindow { mWindow.isAdjustEnabled = enabled }
            }

        fun exitOnClose() {
            mExitOnClose = true
        }

        fun requestFocus() {
            mWindow.requestWindowFocus()
        }

        fun disableFocus() {
            mWindow.disableWindowFocus()
        }

        fun close() {
            close(true)
        }

        override fun close(removeFromWindows: Boolean) {
            if (removeFromWindows && !removeWindow(this)) {
                return
            }
            runWithWindow {
                mWindow.close()
                if (mExitOnClose) {
                    mRuntime.exit()
                }
            }
        }
    }
}
