package com.stardust.autojs.rhino;

import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
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
        } else {
            super.put(name, start, value);
        }
    }
}