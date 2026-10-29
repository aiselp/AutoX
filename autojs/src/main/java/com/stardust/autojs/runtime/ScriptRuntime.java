package com.stardust.autojs.runtime;

import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.R;
import com.stardust.autojs.annotation.ScriptVariable;
import com.stardust.autojs.core.accessibility.AccessibilityBridge;
import com.stardust.autojs.core.accessibility.UiSelector;
import com.stardust.autojs.core.activity.ActivityInfoProvider;
import com.stardust.autojs.core.image.Colors;
import com.stardust.autojs.core.looper.Loopers;
import com.stardust.autojs.core.permission.Permissions;
import com.stardust.autojs.core.ui.ViewExtras;
import com.stardust.autojs.rhino.TopLevelScope;
import com.stardust.autojs.runtime.api.AppUtils;
import com.stardust.autojs.runtime.api.Console;
import com.stardust.autojs.runtime.api.Device;
import com.stardust.autojs.runtime.api.Dialogs;
import com.stardust.autojs.runtime.api.Engines;
import com.stardust.autojs.runtime.api.Events;
import com.stardust.autojs.runtime.api.Files;
import com.stardust.autojs.runtime.api.Floaty;
import com.stardust.autojs.runtime.api.Images;
import com.stardust.autojs.runtime.api.Media;
import com.stardust.autojs.runtime.api.Sensors;
import com.stardust.autojs.runtime.api.Threads;
import com.stardust.autojs.runtime.api.Timers;
import com.stardust.autojs.runtime.api.UI;
import com.stardust.autojs.runtime.exception.ScriptEnvironmentException;
import com.stardust.autojs.runtime.exception.ScriptException;
import com.stardust.autojs.runtime.exception.ScriptInterruptedException;
import com.stardust.lang.ThreadCompat;
import com.stardust.autojs.util.CustomToastView;
import com.stardust.util.ScreenMetrics;
import com.stardust.util.SdkVersionUtil;
import com.stardust.util.UiHandler;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Created by Stardust on 2017/1/27.
 */

public abstract class ScriptRuntime {

    private static final String TAG = "ScriptRuntime";


    @ScriptVariable
    public final AppUtils app;

    @ScriptVariable
    public final Console console;

    @ScriptVariable
    public final ActivityInfoProvider info;

    @ScriptVariable
    public final UI ui;

    @ScriptVariable
    public final Dialogs dialogs;

    @ScriptVariable
    public CustomToastView toastX;

    @ScriptVariable
    public Events events;

    @ScriptVariable
    public final ScriptBridges bridges = new ScriptBridges();

    @ScriptVariable
    public Loopers loopers;

    @ScriptVariable
    public Timers timers;

    @ScriptVariable
    public Device device;

    @ScriptVariable
    public final AccessibilityBridge accessibilityBridge;

    @ScriptVariable
    public final Engines engines;

    @ScriptVariable
    public Threads threads;

    @ScriptVariable
    public final Floaty floaty;

    @ScriptVariable
    public UiHandler uiHandler;

    @ScriptVariable
    public final Colors colors = new Colors();

    @ScriptVariable
    public final Files files;

    @ScriptVariable
    public Sensors sensors;

    @ScriptVariable
    public final Media media;

    private final Images images;

    private static WeakReference<Context> applicationContext;
    private Map<String, Object> mProperties = new ConcurrentHashMap<>();
    protected ScreenMetrics mScreenMetrics = new ScreenMetrics();
    protected Thread mThread;
    protected TopLevelScope mTopLevelScope;

    protected ScriptRuntime(ScriptRuntimeV2.Builder builder) {
        uiHandler = builder.getUiHandler();
        Context context = uiHandler.getContext();
        app = builder.getAppUtils();
        console = builder.getConsole();
        accessibilityBridge = builder.getAccessibilityBridge();
        ui = new UI(context, this);
        ViewExtras.setResourceParser(ui.getResourceParser());
        this.info = accessibilityBridge.getInfoProvider();
        images = new Images(context, this, builder.getScreenCaptureRequester());
        engines = new Engines(builder.getEngineService(), this);
        dialogs = new Dialogs(this);
        device = new Device(context);
        floaty = new Floaty(uiHandler, ui, this);
        files = new Files(this);
        media = new Media(context, this);
        toastX = new CustomToastView(context, console);
    }

    public abstract void init();

    public abstract TopLevelScope getTopLevelScope();

    public abstract void setTopLevelScope(TopLevelScope topLevelScope);

    public static void setApplicationContext(Context context) {
        applicationContext = new WeakReference<>(context);
    }

    public static Context getApplicationContext() {
        if (applicationContext == null || applicationContext.get() == null) {
            throw new ScriptEnvironmentException("No application context");
        }
        return applicationContext.get();
    }

    public UiHandler getUiHandler() {
        return uiHandler;
    }

    public AccessibilityBridge getAccessibilityBridge() {
        return accessibilityBridge;
    }

    public void toast(final String text) {
        uiHandler.toast(text);
    }

    public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new ScriptInterruptedException();
        }
    }

    public UiSelector selector() {
        return new UiSelector(accessibilityBridge);
    }

    public boolean isStopped() {
        return Thread.currentThread().isInterrupted();
    }

    public static void requiresApi(int i) {
        if (Build.VERSION.SDK_INT < i) {
            throw new ScriptException(GlobalAppContext.getString(R.string.text_requires_sdk_version_to_run_the_script) + SdkVersionUtil.sdkIntToString(i));
        }
    }

    public void requestPermissions(String[] permissions) {
        Context context = uiHandler.getContext();
        permissions = Permissions.getPermissionsNeedToRequest(context, permissions);
        if (permissions.length == 0)
            return;
        Permissions.requestPermissions(context, permissions);
    }


    public void exit() {
        mThread.interrupt();
        engines.myEngine().forceStop();
        threads.exit();
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new ScriptInterruptedException();
        }
    }

    public void exit(Throwable e) {
        engines.myEngine().uncaughtException(e);
        exit();
    }

    @Deprecated
    public void stop() {
        exit();
    }


    public void setScreenMetrics(int width, int height) {
        mScreenMetrics.setScreenMetrics(width, height);
    }

    public ScreenMetrics getScreenMetrics() {
        return mScreenMetrics;
    }

    public void ensureAccessibilityServiceEnabled() {
        accessibilityBridge.ensureServiceEnabled();
    }

    public void onExit() {
        Log.d(TAG, "on exit");
        //清除interrupt状态
        ThreadCompat.interrupted();
        //悬浮窗需要第一时间关闭以免出现恶意脚本全屏悬浮窗屏蔽屏幕并且在exit中写死循环的问题
        ignoresException(floaty::closeAll);
        try {

            events.emit("exit");
            if (console.isAutoHide()) {
                console.log("系统消息：任务结束,3秒后该窗口关闭");
                uiHandler.postDelayed(console::hide, 4000);
            }
        } catch (Throwable e) {
            console.error("exception on exit: ", e);
        }
        ignoresException(threads::shutDownAll);
        ignoresException(events::recycle);
        ignoresException(media::recycle);
        ignoresException(loopers::recycle);
        ignoresException(images::releaseScreenCapturer);
        ignoresException(sensors::unregisterAll);
        ignoresException(timers::recycle);
        ignoresException(ui::recycle);
//        ignoresException(paddle::release);
    }

    private void ignoresException(Runnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public Object getImages() {
        return images;
    }

    public Object getProperty(String key) {
        return mProperties.get(key);
    }

    public Object putProperty(String key, Object value) {
        return mProperties.put(key, value);
    }

    public Object removeProperty(String key) {
        return mProperties.remove(key);
    }

}