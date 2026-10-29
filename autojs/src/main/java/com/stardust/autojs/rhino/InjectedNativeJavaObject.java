package com.stardust.autojs.rhino;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.stardust.autojs.core.ui.ViewExtras;
import com.stardust.autojs.core.ui.nativeview.NativeView;
import com.stardust.autojs.core.ui.nativeview.ViewPrototype;
import com.stardust.autojs.runtime.ScriptRuntime;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.lc.type.TypeInfo;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public final class InjectedNativeJavaObject extends ProxyJavaObject {

    private final Object javaObj;
    private final ScriptRuntime runtime;

    public InjectedNativeJavaObject(Scriptable scope, Object javaObj, TypeInfo staticType, ScriptRuntime runtime) {
        super(scope, javaObj, staticType);
        this.javaObj = javaObj;
        this.runtime = runtime;
    }

    @Override
    public Object get(String name, Scriptable start) {
        Log.d("InjectedNativeJavaObject", "get: name=" + name);

        if (this.javaObj instanceof View) {
            if ("width".equals(name)) {
                return ((View) this.javaObj).getWidth();
            }
            if ("height".equals(name)) {
                return ((View) this.javaObj).getHeight();
            }

            NativeView nativeView = ViewExtras.getOrCreateNativeView(start, (View) this.javaObj, this.runtime);
            Object selfResult = nativeView.get(name, start);
            if (selfResult != Scriptable.NOT_FOUND) {
                if (selfResult instanceof Function) {
                    return new BaseFunction() {
                        @Override
                        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                            return ((Function) selfResult).call(cx, scope, nativeView, args);
                        }
                    };
                }
                return selfResult;
            }

            ViewPrototype prototype = nativeView.getViewPrototype();
            if (prototype != null) {
                try {
                    return new BaseFunction() {
                        @Override
                        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                            Object[] argsArray = args == null ? new Object[0] : args;
                            List<Method> methods = new ArrayList<>();
                            for (Method m : prototype.getClass().getMethods()) {
                                if (m.getName().equals(name)) {
                                    methods.add(m);
                                }
                            }
                            Method targetMethod = null;
                            for (Method m : methods) {
                                if (m.getParameterCount() == argsArray.length) {
                                    targetMethod = m;
                                    break;
                                }
                            }
                            if (targetMethod == null && !methods.isEmpty()) {
                                targetMethod = methods.get(0);
                            }
                            if (targetMethod == null) {
                                return null;
                            }
                            try {
                                return targetMethod.invoke(prototype, Arrays.copyOf(argsArray, argsArray.length));
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                Log.e("InjectedNativeJavaObject", "Method invoke failed: " + name, e);
                                return null;
                            }
                        }
                    };
                } catch (Exception e) {
                    Log.e("InjectedNativeJavaObject", "error", e);
                }
            }
        }

        return super.get(name, start);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        if ((this.javaObj instanceof View view) && name != null) {
            switch (name) {
                case "height" -> {
                    ViewGroup.LayoutParams params = view.getLayoutParams();
                    params.height = ((Number) value).intValue();
                    view.setLayoutParams(params);
                    view.requestLayout();
                    return;
                }
                case "width" -> {
                    ViewGroup.LayoutParams params = view.getLayoutParams();
                    params.width = ((Number) value).intValue();
                    view.setLayoutParams(params);
                    view.requestLayout();
                    return;
                }
                case "text" -> {
                    if (this.javaObj instanceof TextView) {
                        String strValue;
                        if (value instanceof Number) {
                            double d = ((Number) value).doubleValue();
                            strValue = (d == (long) d) ? String.valueOf((long) d) : String.valueOf(d);
                        } else {
                            strValue = String.valueOf(value);
                        }
                        ((TextView) this.javaObj).setText(strValue);
                    }
                    return;
                }
            }
        }
        assert name != null;
        super.put(name, start, value);
    }
}