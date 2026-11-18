package org.autojs.autojs.autojs.key;

import android.util.Log;
import android.view.KeyEvent;

import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.core.inputevent.InputEventObserver;
import com.stardust.autojs.core.inputevent.ShellKeyObserver;
import com.stardust.event.EventDispatcher;
import org.autojs.autojs.autojs.AutoJs;
import com.stardust.view.accessibility.AccessibilityService;
import com.stardust.view.accessibility.OnKeyListener;

/**
 * Created by Stardust on 2017/8/14.
 */

public class GlobalKeyObserver implements OnKeyListener, ShellKeyObserver.KeyListener {


    public interface OnVolumeDownListener {
        void onVolumeDown();
    }

    private static final EventDispatcher.Event<OnVolumeDownListener> VOLUME_DOWN_EVENT = OnVolumeDownListener::onVolumeDown;
    private static final String LOG_TAG = "GlobalKeyObserver";
    private static final long EVENT_TIMEOUT = 200;
    private static GlobalKeyObserver sSingleton;
    private static volatile boolean sRunningVolumeControlEnabled;
    private static volatile boolean sVolumeRecordControlEnabled;
    private EventDispatcher<OnVolumeDownListener> mVolumeDownEventDispatcher = new EventDispatcher<>();
    private boolean mVolumeDownFromShell, mVolumeDownFromAccessibility;
    private boolean mVolumeUpFromShell, mVolumeUpFromAccessibility;

    GlobalKeyObserver() {
        AccessibilityService.Companion.getStickOnKeyObserver()
                .addListener(this);
        ShellKeyObserver observer = new ShellKeyObserver();
        observer.setKeyListener(this);
        InputEventObserver.getGlobal(GlobalAppContext.get()).addListener(observer);
    }

    public static GlobalKeyObserver getSingleton() {
        if (sSingleton == null) {
            sSingleton = new GlobalKeyObserver();
        }
        return sSingleton;
    }

    public static void init() {
        getSingleton();
    }

    /**
     * 只在 :script 进程调用。 SharedPreferences 在进程间不共享内存缓存，
     * 因此两个开关键以主进程通过 Binder 下发的值为准，而不是在这里重新读偏好。
     */
    public static void init(boolean runningVolumeControlEnabled, boolean volumeRecordControlEnabled) {
        sRunningVolumeControlEnabled = runningVolumeControlEnabled;
        sVolumeRecordControlEnabled = volumeRecordControlEnabled;
        if (runningVolumeControlEnabled || volumeRecordControlEnabled) {
            getSingleton();
        }
    }

    public static void setVolumeControlEnabled(boolean runningEnabled, boolean recordEnabled) {
        sRunningVolumeControlEnabled = runningEnabled;
        sVolumeRecordControlEnabled = recordEnabled;
        if (runningEnabled || recordEnabled) {
            getSingleton();
        }
    }

    public void onVolumeUp() {
        Log.d(LOG_TAG, "onVolumeUp at " + System.currentTimeMillis());
        if (sRunningVolumeControlEnabled) {
            Log.d(LOG_TAG, "stop all scripts when volume up");
            AutoJs.getInstance().getScriptEngineService().stopAllAndToast();
        }
    }

    public void onVolumeDown() {
        if (!sVolumeRecordControlEnabled) {
            return;
        }
        Log.d(LOG_TAG, "onVolumeDown at " + System.currentTimeMillis());
        mVolumeDownEventDispatcher.dispatchEvent(VOLUME_DOWN_EVENT);
    }

    public void addVolumeDownListener(OnVolumeDownListener listener) {
        mVolumeDownEventDispatcher.addListener(listener);
    }

    public boolean removeVolumeDownListener(OnVolumeDownListener listener) {
        return mVolumeDownEventDispatcher.removeListener(listener);
    }

    @Override
    public void onKeyEvent(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP)
            return;
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (mVolumeDownFromShell) {
                mVolumeDownFromShell = false;
                return;
            }
            mVolumeDownFromAccessibility = true;
            onVolumeDown();
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (mVolumeUpFromShell) {
                mVolumeUpFromShell = false;
                return;
            }
            mVolumeUpFromAccessibility = true;
            onVolumeUp();
        }
    }


    @Override
    public void onKeyDown(String keyName) {

    }

    @Override
    public void onKeyUp(String keyName) {
        if ("KEY_VOLUMEUP".equals(keyName)) {
            if (mVolumeUpFromAccessibility) {
                mVolumeUpFromAccessibility = false;
                return;
            }
            mVolumeUpFromShell = true;
            onVolumeUp();
        } else if ("KEY_VOLUMEDOWN".equals(keyName)) {
            if (mVolumeDownFromAccessibility) {
                mVolumeDownFromAccessibility = false;
                return;
            }
            mVolumeDownFromShell = true;
            onVolumeDown();
        }
    }
}
