package com.stardust.autojs.rhino;

import android.util.Log;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.UniqueTag;

public class ProxyObject extends ScriptableObject {

    private Function mGetter;
    private Function mSetter;

    @Override
    public String getClassName() {
        return "ProxyObject";
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        // 只记属性名：value 可能是插件配置里的 token 等敏感值。
        Log.d("ProxyObject", "put(" + name + ")");
        if (!name.equals("__proxy__") || !(value instanceof Scriptable)) {
            if (mSetter != null) {
                mSetter.call(Context.getCurrentContext(), start, start, new Object[]{name, value});
                return;
            } else {
                super.put(name, start, value);
                return;
            }
        }
        Scriptable proxy = (Scriptable) value;
        Object getter = proxy.get("get", start);
        if (getter instanceof Function) {
            mGetter = (Function) getter;
            Log.d("ProxyObject", "__proxy__ set, mGetter=" + mGetter);
        }
        Object setter = proxy.get("set", start);
        if (setter instanceof Function) {
            mSetter = (Function) setter;
            Log.d("ProxyObject", "__proxy__ set, mSetter=" + mSetter);
        }
    }

    public Object getWithoutProxy(String name, Scriptable start) {
        return super.get(name, start);
    }

    @Override
    public Object get(String name, Scriptable start) {
        if ("bindingContext".equals(name) || "__noSuchMethod__".equals(name)) {
            return super.get(name, start);
        }
        Object value = super.get(name, start);
        if ((value == null || value == UniqueTag.NOT_FOUND) && mGetter != null) {
            return mGetter.call(Context.getCurrentContext(), start, start, new Object[]{name});
        }
        return value;
    }

    @Override
    public Object getDefaultValue(Class<?> typeHint) {
        return toString();
    }
}