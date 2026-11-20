package com.stardust.autojs.rhino;

import android.widget.TextView;

import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.lc.type.TypeInfo;

/**
 * Created by Stardust on 2018/4/4.
 */
public class NativeJavaObjectWithPrototype extends NativeJavaObject {

    public NativeJavaObjectWithPrototype(Scriptable scope, Object javaObject, Class<?> staticType) {
        super(scope, javaObject, new SimpleTypeInfo(staticType));
    }

    public NativeJavaObjectWithPrototype(Scriptable scope, Object javaObject, Class<?> staticType, boolean isAdapter) {
        super(scope, javaObject, new SimpleTypeInfo(staticType), isAdapter);
    }

    public NativeJavaObjectWithPrototype(Scriptable scope, Object javaObject, TypeInfo staticType) {
        super(scope, javaObject, staticType);
    }

    public NativeJavaObjectWithPrototype(Scriptable scope, Object javaObject, TypeInfo staticType, boolean isAdapter) {
        super(scope, javaObject, staticType, isAdapter);
    }

    public NativeJavaObjectWithPrototype() {
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return super.has(name, start) || (prototype != null && prototype.has(name, start))
                || name.equals(NativeObject.PROTO_PROPERTY);
    }

    @Override
    public Object get(String name, Scriptable start) {
        if (name.equals(NativeObject.PROTO_PROPERTY)) {
            return prototype;
        }
        if (super.has(name, start)) {
            return super.get(name, start);
        }
        if (prototype == null) {
            return Scriptable.NOT_FOUND;
        }
        return prototype.get(name, start);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        if (name.equals(NativeObject.PROTO_PROPERTY)) {
            prototype = (Scriptable) value;
            return;
        }
        // JsTextView / JsButton / JsEditText 同时提供 text() 与 text(CharSequence)，
        // Rhino 会把 text 暴露成 NativeJavaMethod。此时 super.put() 只会把值写进
        // JS 属性表，被同名 Java 方法遮蔽，view.text = "..." 看起来毫无效果。
        // 这里把 text 赋值直接落到 TextView.setText，属性语法与方法调用即可共存。
        if ("text".equals(name) && unwrap() instanceof TextView) {
            ((TextView) unwrap()).setText(ScriptRuntime.toString(value));
            return;
        }
        super.put(name, start, value);
    }
}