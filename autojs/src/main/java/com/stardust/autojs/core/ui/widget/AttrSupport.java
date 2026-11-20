package com.stardust.autojs.core.ui.widget;

import android.view.View;
import android.view.ViewGroup;

import com.stardust.autojs.core.ui.ViewExtras;
import com.stardust.autojs.core.ui.attribute.ViewAttributes;

import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Undefined;

public interface AttrSupport {

    default void attr(String name, Object value) {
        ViewAttributes attrs = ViewExtras.getViewAttributes((View) this);
        if (attrs != null) {
            attrs.setAttr(name, ScriptRuntime.toString(value));
        }
    }

    default Object attr(String name) {
        ViewAttributes attrs = ViewExtras.getViewAttributes((View) this);
        if (attrs != null) {
            return attrs.getAttrValue(name);
        }
        return Undefined.SCRIPTABLE_UNDEFINED;
    }

    default void setWidth(int width) {
        if (this instanceof View) {
            View view = (View) this;
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params != null) {
                params.width = width;
                view.setLayoutParams(params);
                view.requestLayout();
            }
        }
    }

    default void setHeight(int height) {
        if (this instanceof View) {
            View view = (View) this;
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params != null) {
                params.height = height;
                view.setLayoutParams(params);
                view.requestLayout();
            }
        }
    }

    default int getWidth() {
        return this instanceof View ? ((View) this).getWidth() : 0;
    }

    default int getHeight() {
        return this instanceof View ? ((View) this).getHeight() : 0;
    }
}