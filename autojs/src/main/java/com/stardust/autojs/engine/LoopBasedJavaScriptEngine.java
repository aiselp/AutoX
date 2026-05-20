package com.stardust.autojs.engine;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.stardust.autojs.script.JavaScriptSource;
import com.stardust.autojs.script.ScriptSource;

import org.mozilla.javascript.ContinuationPending;

/**
 * Created by Stardust on 2017/7/28.
 */

public class LoopBasedJavaScriptEngine extends RhinoJavaScriptEngine {

    public interface ExecuteCallback {
        void onResult(Object r);

        void onException(Exception e);
    }

    private Handler mHandler;
    private boolean mLooping = false;
    private volatile boolean mDestroyed = false;

    public LoopBasedJavaScriptEngine(Context context) {
        super(context);
    }

    @Override
    public Object execute(final JavaScriptSource source) {
        execute(source, null);
        return null;
    }


    public void execute(final ScriptSource source, final ExecuteCallback callback) {
        Runnable r = () -> {
            try {
                Object o = LoopBasedJavaScriptEngine.super.execute((JavaScriptSource) source);
                if (callback != null)
                    callback.onResult(o);
            } catch (ContinuationPending ignored) {
            } catch (Throwable e) {
                if (callback == null) {
                    throw e;
                } else {
                    if (e instanceof Exception) {
                        callback.onException((Exception) e);
                    } else {
                        callback.onException(new Exception(e));
                    }
                }
            }


        };
        mHandler.post(r);
        if (!mLooping && Looper.myLooper() != Looper.getMainLooper()) {
            mLooping = true;
            while (true) {
                try {
                    Looper.loop();
                } catch (ContinuationPending ignored) {
                    continue;
                } catch (Throwable t) {
                    mLooping = false;
                    throw t;
                }
                break;
            }
        }
    }

    @Override
    public void forceStop() {
        // 获取引擎线程的 Looper
        Looper engineLooper = mHandler.getLooper();
        if (engineLooper != Looper.getMainLooper()) {
            // 向引擎线程投递 destroy，确保 Context.exit() 在正确的线程执行
            mHandler.post(this::destroy);
            // 安全退出引擎线程的 Looper（会先处理完已投递的 destroy 消息）
            engineLooper.quitSafely();
        } else {
            // 极端情况：如果引擎 Looper 不存在或是主线程，直接同步清理
            destroy();
        }

        // 关闭关联 Activity（如果有）
        Activity activity = (Activity) getTag("activity");
        if (activity != null) {
            activity.finish();
        }
        super.forceStop();
    }

    @Override
    public synchronized void destroy() {
        if (mDestroyed) return;
        mDestroyed = true;
        // 此时已经在引擎线程执行，无需再投递
        mHandler.removeCallbacksAndMessages(null);
        getRuntime().loopers.forceStop();
        super.destroy();
    }

    @Override
    public void init() {
        if (Looper.myLooper() == null) Looper.prepare();
        mHandler = new Handler();
        super.init();
    }


}
