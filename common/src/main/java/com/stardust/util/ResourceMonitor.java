package com.stardust.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;

import com.stardust.BuildConfig;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourceMonitor {

    private static final String LOG_TAG = "ResourceMonitor";

    // 修改1：类型改为 WeakReference
    private static final ConcurrentHashMap<Class<?>, SparseArray<WeakReference<Exception>>> mResources = new ConcurrentHashMap<>();
    private static Handler sHandler;
    private static boolean sEnabled = BuildConfig.DEBUG;
    private static ExceptionCreator sExceptionCreator;
    private static UnclosedResourceDetectedHandler sUnclosedResourceDetectedHandler;

    public static void setExceptionCreator(ExceptionCreator exceptionCreator) {
        sExceptionCreator = exceptionCreator;
    }

    public static void setUnclosedResourceDetectedHandler(UnclosedResourceDetectedHandler unclosedResourceDetectedHandler) {
        sUnclosedResourceDetectedHandler = unclosedResourceDetectedHandler;
    }

    public static void onOpen(Resource resource) {
        if (!sEnabled) {
            return;
        }
        // 修改2：SparseArray 的类型也要改
        SparseArray<WeakReference<Exception>> map = mResources.get(resource.getClass());
        if (map == null) {
            map = new SparseArray<>();
            mResources.put(resource.getClass(), map);
        }
        int resourceId = resource.getResourceId();
        Exception exception;
        if (sExceptionCreator == null) {
            exception = new UnclosedResourceException(resource);
            exception.fillInStackTrace();
        } else {
            exception = sExceptionCreator.create(resource);
        }
        // 修改3：存入 WeakReference
        map.put(resourceId, new WeakReference<>(exception));
    }

    public static void onClose(Resource resource) {
        if (!sEnabled) {
            return;
        }
        // 修改4：SparseArray 的类型改为 WeakReference
        SparseArray<WeakReference<Exception>> map = mResources.get(resource.getClass());
        if (map != null) {
            map.remove(resource.getResourceId());
        }
    }

    public static void onFinalize(Resource resource) {
        if (!sEnabled) {
            return;
        }
        // 修改5：SparseArray 的类型改为 WeakReference
        SparseArray<WeakReference<Exception>> map = mResources.get(resource.getClass());
        if (map != null) {
            int indexOfKey = map.indexOfKey(resource.getResourceId());
            if (indexOfKey >= 0) {
                WeakReference<Exception> ref = map.valueAt(indexOfKey);
                final Exception exception = ref != null ? ref.get() : null;
                map.removeAt(indexOfKey);
                if (exception != null) {
                    if (sHandler == null) {
                        sHandler = new Handler(Looper.getMainLooper());
                    }
                    sHandler.post(new Runnable() {
                        public final void run() {
                            UnclosedResourceDetectedException detectedException = new UnclosedResourceDetectedException(exception);
                            detectedException.fillInStackTrace();
                            Log.w(LOG_TAG, "UnclosedResourceDetected", detectedException);
                            if (sUnclosedResourceDetectedHandler != null) {
                                sUnclosedResourceDetectedHandler.onUnclosedResourceDetected(detectedException);
                            } else {
                                throw detectedException;
                            }
                        }
                    });
                }
            }
        }
    }

    public static boolean isEnabled() {
        return sEnabled;
    }

    public static void setEnabled(boolean mEnabled) {
        ResourceMonitor.sEnabled = mEnabled;
    }

    public static void clearAll() {
        mResources.clear();
    }

    public static final class UnclosedResourceException extends RuntimeException {
        private final transient WeakReference<Resource> resourceRef;

        public UnclosedResourceException(Resource resource) {
            super("id = " + resource.getResourceId() + ", resource = " + resource);
            this.resourceRef = new WeakReference<>(resource);
        }

        public Resource getResource() {
            return resourceRef != null ? resourceRef.get() : null;
        }
    }

    public static final class UnclosedResourceDetectedException extends RuntimeException {
        public UnclosedResourceDetectedException(Throwable cause) {
            super(cause);
        }
    }

    public interface Resource {
        int getResourceId();
    }

    public interface ExceptionCreator {
        Exception create(Resource resource);
    }

    public interface UnclosedResourceDetectedHandler {
        void onUnclosedResourceDetected(UnclosedResourceDetectedException detectedException);
    }
}