package com.stardust.autojs.rhino;

import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.lc.type.TypeInfo;

public class BindingNativeJavaObject extends NativeJavaObject {

    public BindingNativeJavaObject() {
    }

    public BindingNativeJavaObject(Scriptable scope, Object javaObject, Class<?> staticType) {
        super(scope, javaObject, new SimpleTypeInfo(staticType));
    }

    public BindingNativeJavaObject(Scriptable scope, Object javaObject, TypeInfo staticType) {
        super(scope, javaObject, staticType);
    }

    public BindingNativeJavaObject(Scriptable scope, Object javaObject, TypeInfo staticType, boolean isAdapter) {
        super(scope, javaObject, staticType, isAdapter);
    }

    @Override
    protected void initMembers() {
        if (this.javaObject != null) {
            this.javaObject.getClass();
        } else if (this.staticType != null) {
            this.staticType.asClass();
        }
    }
}