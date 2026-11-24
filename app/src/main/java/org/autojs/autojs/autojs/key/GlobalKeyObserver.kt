package org.autojs.autojs.autojs.key

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import com.aiselp.autox.utils.multiProcessAppConfig
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.core.inputevent.InputEventObserver
import com.stardust.autojs.core.inputevent.ShellKeyObserver
import com.stardust.event.EventDispatcher
import com.stardust.view.accessibility.AccessibilityService
import com.stardust.view.accessibility.OnKeyListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.autojs.autojs.autojs.AutoJs

/**
 * Created by Stardust on 2017/8/14.
 */
class GlobalKeyObserver internal constructor() : OnKeyListener, ShellKeyObserver.KeyListener {

    fun interface OnVolumeDownListener {
        fun onVolumeDown()
    }

    private val mVolumeDownEventDispatcher = EventDispatcher<OnVolumeDownListener>()
    private var mVolumeDownFromShell = false
    private var mVolumeDownFromAccessibility = false
    private var mVolumeUpFromShell = false
    private var mVolumeUpFromAccessibility = false
    var isRunningVolumeControlEnabled = false

    fun onVolumeUp() {
        Log.d(LOG_TAG, "onVolumeUp at ${System.currentTimeMillis()}")
        if (isRunningVolumeControlEnabled) {
            Log.d(LOG_TAG, "stop all scripts when volume up")
            AutoJs.getInstance().scriptEngineService.stopAllAndToast()
        }

    }

    fun onVolumeDown() {
        Log.d(LOG_TAG, "onVolumeDown at ${System.currentTimeMillis()}")
        mVolumeDownEventDispatcher.dispatchEvent(VOLUME_DOWN_EVENT)
    }

    fun addVolumeDownListener(listener: OnVolumeDownListener) {
        mVolumeDownEventDispatcher.addListener(listener)
    }

    fun removeVolumeDownListener(listener: OnVolumeDownListener): Boolean {
        return mVolumeDownEventDispatcher.removeListener(listener)
    }

    override fun onKeyEvent(keyCode: Int, event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_UP) return
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (mVolumeDownFromShell) {
                mVolumeDownFromShell = false
                return
            }
            mVolumeUpFromAccessibility = true
            onVolumeDown()
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (mVolumeUpFromShell) {
                mVolumeUpFromShell = false
                return
            }
            mVolumeUpFromAccessibility = true
            onVolumeUp()
        }
    }

    override fun onKeyDown(keyName: String?) {
    }

    override fun onKeyUp(keyName: String?) {
        if ("KEY_VOLUMEUP" == keyName) {
            if (mVolumeUpFromAccessibility) {
                mVolumeUpFromAccessibility = false
                return
            }
            mVolumeUpFromShell = true
            onVolumeUp()
        } else if ("KEY_VOLUMEDOWN" == keyName) {
            if (mVolumeDownFromAccessibility) {
                mVolumeDownFromAccessibility = false
                return
            }
            mVolumeDownFromShell = true
            onVolumeDown()
        }
    }

    companion object {

        private val VOLUME_DOWN_EVENT =
            EventDispatcher.Event<OnVolumeDownListener> { listener -> listener.onVolumeDown() }
        private const val LOG_TAG = "GlobalKeyObserver"
        private val init = Job()
        private val globalKeyObserver: GlobalKeyObserver by lazy {
            val keyObserver = GlobalKeyObserver()
            AccessibilityService.stickOnKeyObserver.addListener(keyObserver)
            val observer = ShellKeyObserver()
            observer.setKeyListener(keyObserver)
            InputEventObserver.getGlobal(GlobalAppContext.get()).addListener(observer)
            keyObserver
        }

        @Synchronized
        fun init(context: Context) {
            if (init.isCompleted) return
            val scope = CoroutineScope(Dispatchers.Main)
            scope.launch {
                context.multiProcessAppConfig.data.collect {
                    globalKeyObserver.isRunningVolumeControlEnabled =
                        it.isRunningVolumeControlEnabled
                }
            }
            init.complete()
        }
    }
}
