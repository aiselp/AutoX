package com.stardust.autojs.rhino;

import android.util.Log;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.UniqueTag;
import org.mozilla.javascript.lc.type.TypeInfo;

public class ProxyJavaObject extends NativeJavaObject {
    private static final String TAG = "ProxyJavaObject";
    private Function mGetter;
    private Function mSetter;

    public ProxyJavaObject() {
        super();
    }

    public ProxyJavaObject(Scriptable scope, Object javaObject) {
        super(scope, javaObject, new SimpleTypeInfo(javaObject != null ? javaObject.getClass() : Object.class));
    }

    public ProxyJavaObject(Scriptable scope, Object javaObject, Class<?> staticType) {
        super(scope, javaObject, new SimpleTypeInfo(staticType != null ? staticType : Object.class));
    }

    public ProxyJavaObject(Scriptable scope, Object javaObject, TypeInfo staticType) {
        super(scope, javaObject, staticType);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        if (name.equals("__proxy__") && value instanceof NativeObject) {
            NativeObject proxy = (NativeObject) value;
            Object getter = proxy.get("get", start);
            if (getter instanceof Function) {
                mGetter = (Function) getter;
                Log.d(TAG, "__proxy__ getter set");
            }
            Object setter = proxy.get("set", start);
            if (setter instanceof Function) {
                mSetter = (Function) setter;
                Log.d(TAG, "__proxy__ setter set");
            }
        } else if (mSetter != null) {
            mSetter.call(Context.getCurrentContext(), start, start, new Object[]{name, value});
        } else {
            super.put(name, start, value);
        }
    }

    public Object getWithoutProxy(String name, Scriptable start) {
        return super.get(name, start);
    }

    @Override
    public Object get(String name, Scriptable start) {
        Object value = super.get(name, start);
        if (value != null && value != UniqueTag.NOT_FOUND) {
            return value;
        }
        if (mGetter != null) {
            return mGetter.call(Context.getCurrentContext(), start, start, new Object[]{name});
        }
        return value;
    }

    @Override
    public Object getDefaultValue(Class<?> typeHint) {
        return toString();
    }
}