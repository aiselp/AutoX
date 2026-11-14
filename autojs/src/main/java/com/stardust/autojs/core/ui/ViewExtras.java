package com.stardust.autojs.core.ui;

import android.util.Log;
import android.view.View;

import com.stardust.autojs.R;
import com.stardust.autojs.core.ui.attribute.ViewAttributes;
import com.stardust.autojs.core.ui.attribute.ViewAttributesFactory;
import com.stardust.autojs.core.ui.inflater.ResourceParser;
import com.stardust.autojs.core.ui.nativeview.NativeView;
import com.stardust.autojs.runtime.ScriptRuntime;

import org.mozilla.javascript.Scriptable;

public class ViewExtras {

    private static final String LOG_TAG = "ViewExtras";
    private NativeView mNativeView;
    private ViewAttributes mViewAttributes;
    /** 创建该 view 的 ViewAttributes 时所用的解析器，按 view 固定下来。 */
    private ResourceParser mResourceParser;
    // 只保留弱引用：脚本退出后不应再由 ViewExtras 静态持有 ScriptRuntime 的资源。
    private static volatile java.lang.ref.WeakReference<ResourceParser> sResourceParser;

    public static void setResourceParser(ResourceParser parser) {
        sResourceParser = new java.lang.ref.WeakReference<>(parser);
    }

    private static ResourceParser globalParser() {
        java.lang.ref.WeakReference<ResourceParser> ref = sResourceParser;
        return ref == null ? null : ref.get();
    }

    public static ViewExtras get(View view) {
        ViewExtras extras;
        Object tag = view.getTag(R.id.view_tag_view_extras);
        Log.d(LOG_TAG, "view = " + view + ", tag = " + tag);
        if (tag instanceof ViewExtras) {
            extras = (ViewExtras) tag;
        } else {
            extras = new ViewExtras();
            view.setTag(R.id.view_tag_view_extras, extras);
        }
        return extras;
    }

    public static ViewAttributes getViewAttributes(View view) {
        ViewExtras extras = get(view);
        // 后启动的脚本会覆盖静态 parser；优先用本 view 创建时绑定的那个，
        // 否则旧脚本控件的属性解析会落到另一个脚本的运行时上。
        ResourceParser parser = extras.mResourceParser;
        return getViewAttributes(view, parser != null ? parser : globalParser());
    }

    public static ViewAttributes getViewAttributes(View view, ResourceParser parser) {
        ViewExtras extras = get(view);
        ViewAttributes attributes = extras.getViewAttributes();
        if (attributes == null) {
            attributes = ViewAttributesFactory.create(parser, view);
            extras.setViewAttributes(attributes);
            extras.mResourceParser = parser;
        }
        return attributes;
    }

    public static NativeView getNativeView(Scriptable scope, View view, Class<?> staticType, ScriptRuntime runtime) {
        ViewExtras extras = get(view);
        NativeView nativeView = extras.getNativeView();
        if (nativeView == null) {
            nativeView = new NativeView(scope, view, staticType, runtime);
            extras.setNativeView(nativeView);
        }
        return nativeView;
    }

    public static NativeView getNativeView(View view) {
        ViewExtras extras = get(view);
        return extras.getNativeView();
    }

    public static void setAttr(View view, String name, Object value) {
        ViewAttributes attrs = getViewAttributes(view);
        attrs.setAttr(name, String.valueOf(value));
    }

    public static Object getAttr(View view, String name) {
        ViewExtras extras = get(view);
        ViewAttributes attrs = extras.getViewAttributes();
        if (attrs != null) {
            return attrs.getAttrValue(name);
        }
        return null;
    }

    public static NativeView getOrCreateNativeView(Scriptable scope, View view, ScriptRuntime runtime) {
        ViewExtras extras = get(view);
        NativeView nativeView = extras.getNativeView();
        if (nativeView == null) {
            nativeView = new NativeView(scope, view, view.getClass(), runtime);
            extras.setNativeView(nativeView);
        }
        return nativeView;
    }

    public final NativeView getNativeView() {
        return mNativeView;
    }

    public final ViewAttributes getViewAttributes() {
        return mViewAttributes;
    }

    public final void setNativeView(NativeView nativeView) {
        mNativeView = nativeView;
    }

    public final void setViewAttributes(ViewAttributes viewAttributes) {
        mViewAttributes = viewAttributes;
    }
}