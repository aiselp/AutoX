package com.stardust.autojs.rhino;

import androidx.annotation.NonNull;

import org.mozilla.javascript.lc.type.TypeFormatContext;
import org.mozilla.javascript.lc.type.TypeInfo;

public class SimpleTypeInfo implements TypeInfo {
    private final Class<?> clazz;

    public SimpleTypeInfo(Class<?> clazz) {
        this.clazz = clazz != null ? clazz : Object.class;
    }

    @Override
    public Class<?> asClass() {
        return this.clazz;
    }

    @Override
    public String toString(TypeFormatContext context) {
        return this.clazz.getName();
    }

    @NonNull
    @Override
    public String toString() {
        return this.clazz.getName();
    }
}